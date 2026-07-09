package at.asit.wallet.relyingparty


import at.asit.wallet.relyingparty.ApiController.Transaction
import at.asitplus.dcapi.request.IsoMdocRequest
import at.asitplus.dcapi.request.verifier.CredentialRequestOptions
import at.asitplus.dcapi.request.verifier.DigitalCredentialGetRequest
import at.asitplus.iso.DeviceRequest
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.ResponseMode
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.agent.ValidatorMdoc
import at.asitplus.wallet.lib.agent.ValidatorSdJwt
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.agent.validation.TokenStatusResolverImpl
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.iso.Iso180137AnnexCRequestOptions
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifier
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.oauth2.OAuth2Utils
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.CreationOptions
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.lib.openid.VerifierMetadataMode
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Component
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import kotlin.time.Clock


// Supported options for verifier interactions
enum class SupportedOptions {
    CROSS_DEVICE,
    SAME_DEVICE,
    OID4VP_DC_API,
    ISO_MDOC_DC_API
    ;

    val isDevice: Boolean
        get() = this == CROSS_DEVICE || this == SAME_DEVICE
    val isDcApi: Boolean
        get() = this == OID4VP_DC_API || this == ISO_MDOC_DC_API
}

// --- Declarative profile configuration types ---

sealed class ClientIdSchemeType {
    data object X509Hash : ClientIdSchemeType()
    data object X509SanDns : ClientIdSchemeType()
    data object RedirectUri : ClientIdSchemeType()
}

enum class DeviceResponseMode { DirectPost, DirectPostJwt }

enum class WalletUrlStyle {
    ByReference,  // QR code is a request_uri deep link resolved by the wallet
    Inline,       // QR code embeds the full request (used by AV/redirect_uri profiles)
}

data class DeviceFlowConfig(
    val responseMode: DeviceResponseMode,
    val verifierMetadataMode: VerifierMetadataMode = VerifierMetadataMode.AUTO,
    val walletUrlStyle: WalletUrlStyle = WalletUrlStyle.ByReference,
)

data class DcApiConfig(
    val oid4vpEncrypted: Boolean? = null,  // null = no OID4VP DC API; false/true = unencrypted/encrypted
    val isoMdoc: Boolean = false,
)

data class VerifierProfile(
    val name: String,
    val label: String,
    val description: String,
    val urlPrefix: String,
    val clientIdSchemeType: ClientIdSchemeType? = null,
    val deviceFlowConfig: DeviceFlowConfig? = null,
    val dcApiConfig: DcApiConfig? = null,
) {
    val supportedOptions: Set<SupportedOptions> = buildSet {
        if (deviceFlowConfig != null) {
            add(SupportedOptions.CROSS_DEVICE)
            add(SupportedOptions.SAME_DEVICE)
        }
        if (dcApiConfig?.oid4vpEncrypted != null) add(SupportedOptions.OID4VP_DC_API)
        if (dcApiConfig?.isoMdoc == true) add(SupportedOptions.ISO_MDOC_DC_API)
    }
}

// --- Component ---

@Component
class VerifierProfiles(
    private val configuration: AppConfigurationProperties,
    private val verifierKeyMaterial: KeyMaterial,
) {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(joseCompliantSerializer)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.i(message = message, tag = "at.asitplus.http")
                }
            }
            level = LogLevel.ALL
        }
    }

    private suspend fun remoteKeyLookup(jwsCompact: JwsCompact): Set<JsonWebKey>? =
        (jwsCompact.getPayload<JsonObject>().getOrNull()?.get("iss") as? JsonPrimitive?)?.content?.let { iss ->
            val url = OAuth2Utils.insertWellKnownPath(iss, OpenIdConstants.WellKnownPaths.JwtVcIssuer)
            Napier.i("Resolving Key for $iss from $url")
            httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet()
        }

    val knownProfiles: List<VerifierProfile> = listOf(
        VerifierProfile(
            name = "HAIPd05",
            label = "OpenID4VP: HAIP (d05)",
            description = "x509_hash, OpenID4VP 1.0, direct_post.jwt",
            urlPrefix = Paths.Schemes.HaipVp,
            clientIdSchemeType = ClientIdSchemeType.X509Hash,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
            dcApiConfig = DcApiConfig(oid4vpEncrypted = true),
        ),
        VerifierProfile(
            name = "AV",
            label = "OpenID4VP: AV",
            description = "redirect_uri, OpenID4VP 1.0, direct_post",
            urlPrefix = Paths.Schemes.Av,
            clientIdSchemeType = ClientIdSchemeType.RedirectUri,
            deviceFlowConfig = DeviceFlowConfig(
                responseMode = DeviceResponseMode.DirectPost,
                verifierMetadataMode = VerifierMetadataMode.OMIT_IF_OUT_OF_BAND,
                walletUrlStyle = WalletUrlStyle.Inline,
            ),
            dcApiConfig = DcApiConfig(isoMdoc = true),
        ),
        VerifierProfile(
            name = "UOID4VP",
            label = "DCAPI: Unencrypted OpenID4VP",
            description = "DCAPI, OpenID4VP 1.0, direct_post",
            urlPrefix = "",
            clientIdSchemeType = ClientIdSchemeType.X509Hash,
            dcApiConfig = DcApiConfig(oid4vpEncrypted = false),
        ),
        VerifierProfile(
            name = "MDOCd23",
            label = "OpenID4VP: ISO 18013-7 (d23)",
            description = "x509_san_dns, OpenID4VP d23, direct_post.jwt",
            urlPrefix = Paths.Schemes.MdocOpenId4Vp,
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
        ),
        VerifierProfile(
            name = "MDOCISO",
            label = "DCAPI: ISO 18013-7 (Annex C)",
            description = "ISO 18013-7 Annex C",
            urlPrefix = "",
            dcApiConfig = DcApiConfig(isoMdoc = true),
        ),
        VerifierProfile(
            name = "EUDIW",
            label = "OpenID4VP: EUDIW Ref.",
            description = "x509_san_dns, OpenID4VP d23, direct_post.jwt",
            urlPrefix = Paths.Schemes.OpenId4Vp,
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
        ),
        VerifierProfile(
            name = "DC_API_COMBINED",
            label = "DCAPI: Unencrypted OpenID4VP + ISO 18013-7",
            description = "Unencrypted OpenID4VP (signed/unsigned) and ISO 18013-7 Annex-C via DC API",
            urlPrefix = "",
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            dcApiConfig = DcApiConfig(oid4vpEncrypted = false, isoMdoc = true),
        ),
        VerifierProfile(
            name = "DC_API_COMBINED_ENCRYPTED",
            label = "DCAPI: Encrypted OpenID4VP + ISO 18013-7",
            description = "Encrypted OpenID4VP (signed/unsigned) and ISO 18013-7 Annex-C via DC API",
            urlPrefix = "",
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            dcApiConfig = DcApiConfig(oid4vpEncrypted = true, isoMdoc = true),
        ),
    )

    suspend fun prepare(profile: VerifierProfile, context: TransactionContext): PreparedProfile =
        profile.doPrepare(context)

    private suspend fun VerifierProfile.doPrepare(context: TransactionContext): PreparedProfile {
        suspend fun ClientIdSchemeType.build(): ClientIdScheme = when (this) {
            ClientIdSchemeType.X509Hash -> x509Hash()
            ClientIdSchemeType.X509SanDns -> x509SanDnsD23()
            ClientIdSchemeType.RedirectUri -> redirectUri(context.responseUrl)
        }
        val clientIdScheme = clientIdSchemeType?.build()
        val oid4vpVerifier = clientIdScheme?.let { openId4VpVerifier(it) }
        val iso180137 = if (dcApiConfig?.isoMdoc == true) iso180137Verifier() else null
        val deviceFlow = deviceFlowConfig
        val dcApi = dcApiConfig

        return PreparedVerifierProfile(
            name = name,
            label = label,
            description = description,
            urlPrefix = urlPrefix,
            supportedOptions = supportedOptions,
            clientIdScheme = clientIdScheme,
            oid4vpVerifier = oid4vpVerifier,
            iso180137Verifier = iso180137,
            buildQrCodeUrlFn = { requestUrl ->
                if (deviceFlow != null) buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme!!)
                else requestUrl
            },
            buildWalletUrlFn = { tx, ctx ->
                when {
                    deviceFlow?.walletUrlStyle == WalletUrlStyle.Inline ->
                        tx.transactionGet(ctx.responseUrl)
                    deviceFlow != null ->
                        buildQrCodeUrlByReference(urlPrefix, ctx.transactionGetUrl, clientIdScheme!!)
                    else -> ctx.transactionGetUrl
                }
            },
            transactionGetFn = { txId, responseUrl, request ->
                when (deviceFlow!!.responseMode) {
                    DeviceResponseMode.DirectPost ->
                        directPost(txId, responseUrl, request, oid4vpVerifier!!, deviceFlow.verifierMetadataMode)
                    DeviceResponseMode.DirectPostJwt ->
                        directPostJwt(txId, responseUrl, request, oid4vpVerifier!!, urlPrefix)
                }
            },
            transactionGetDcApiFn = { txId, responseUrl, dcqlRequest, deviceRequest, signed ->
                buildDcApiResponse(txId, responseUrl, dcqlRequest, deviceRequest, signed, oid4vpVerifier, iso180137, dcApi!!)
            },
        )
    }

    private class PreparedVerifierProfile(
        override val name: String,
        override val label: String,
        override val description: String,
        override val urlPrefix: String,
        override val supportedOptions: Set<SupportedOptions>,
        override val clientIdScheme: ClientIdScheme?,
        override val oid4vpVerifier: OpenId4VpVerifier?,
        override val iso180137Verifier: Iso180137AnnexCVerifier?,
        private val buildQrCodeUrlFn: (String) -> String,
        private val buildWalletUrlFn: suspend (Transaction, TransactionContext) -> String,
        private val transactionGetFn: suspend (String, String, CredentialPresentationRequest?) -> String,
        private val transactionGetDcApiFn: suspend (String, String, CredentialPresentationRequest.DCQLRequest?, DeviceRequest?, Boolean) -> String,
    ) : PreparedProfile {
        override fun buildQrCodeUrl(requestUrl: String) = buildQrCodeUrlFn(requestUrl)
        override suspend fun buildWalletUrl(transaction: Transaction, context: TransactionContext) = buildWalletUrlFn(transaction, context)
        override suspend fun transactionGet(transactionId: String, responseUrl: String, presentationRequest: CredentialPresentationRequest?) =
            transactionGetFn(transactionId, responseUrl, presentationRequest)
        override suspend fun transactionGetDcApi(transactionId: String, responseUrl: String, dcqlRequest: CredentialPresentationRequest.DCQLRequest?, deviceRequest: DeviceRequest?, dcApiSignedOid4vp: Boolean) =
            transactionGetDcApiFn(transactionId, responseUrl, dcqlRequest, deviceRequest, dcApiSignedOid4vp)
    }

    private suspend fun buildDcApiResponse(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
        deviceRequest: DeviceRequest?,
        dcApiSignedOid4vp: Boolean,
        oid4vpVerifier: OpenId4VpVerifier?,
        iso180137Verifier: Iso180137AnnexCVerifier?,
        config: DcApiConfig,
    ): String = joseCompliantSerializer.encodeToString(
        CredentialRequestOptions.create(buildList {
            if (config.isoMdoc) add(
                DigitalCredentialGetRequest.IsoMdoc(
                    dcApiIsoMdoc(
                        deviceRequest ?: throw IllegalStateException("Device request is not available"),
                        iso180137Verifier!!,
                        transactionId,
                    )
                )
            )
            if (config.oid4vpEncrypted != null) add(
                buildOpenId4VpDcApiRequest(
                    transactionId = transactionId,
                    responseUrl = responseUrl,
                    presentationRequest = dcqlRequest,
                    verifier = oid4vpVerifier!!,
                    dcApiSignedOid4vp = dcApiSignedOid4vp,
                    encryption = config.oid4vpEncrypted,
                )
            )
        })
    )

    private suspend fun openId4VpVerifier(clientIdScheme: ClientIdScheme) = OpenId4VpVerifier(
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = clientIdScheme,
        verifier = VerifierAgent(
            identifier = clientIdScheme.clientId,
            validatorSdJwt = potentialValidatorSdJwt(),
            validatorMdoc = potentialValidatorMdoc()
        ),
    )

    private fun iso180137Verifier() = Iso180137AnnexCVerifier(validatorMdoc = potentialValidatorMdoc())

    private suspend fun x509SanDnsD23(redirectUri: String = configuration.publicContext.toString()): ClientIdScheme.CertificateSanDns = ClientIdScheme.CertificateSanDns(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
        clientIdDnsName = configuration.publicContext.host,
        redirectUri = redirectUri
    )

    private suspend fun buildOpenId4VpDcApiRequest(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest?,
        verifier: OpenId4VpVerifier,
        dcApiSignedOid4vp: Boolean,
        encryption: Boolean,
    ): DigitalCredentialGetRequest = if (dcApiSignedOid4vp) {
        DigitalCredentialGetRequest.OpenId4VpSigned(
            DigitalCredentialGetRequest.OpenId4Vp.SignedDataElement(dcApiOpenIdSigned(
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = verifier,
                encryption = encryption,
                transactionId = transactionId
            ))
        )
    } else {
        DigitalCredentialGetRequest.OpenId4VpUnsigned(
            dcApiOpenIdUnsigned(
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = verifier,
                encryption = encryption,
                transactionId = transactionId
            )
        )
    }

    private suspend fun x509Hash(redirectUri: String = configuration.publicContext.toString()) = ClientIdScheme.CertificateHash(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
        redirectUri = redirectUri,
    )

    private suspend fun redirectUri(redirectUri: String = configuration.publicContext.toString()) = ClientIdScheme.RedirectUri(
        redirectUri = redirectUri,
    )

    private fun buildQrCodeUrlByReference(
        urlPrefix: String,
        requestUrl: String,
        clientIdScheme: ClientIdScheme,
    ): String = ServletUriComponentsBuilder.fromUriString(urlPrefix).apply {
        JarRequestParameters(
            clientId = clientIdScheme.clientId,
            requestUri = requestUrl,
        ).encodeToParameters()
            .forEach { queryParam(it.key, it.value) }
    }.toUriString()

    private suspend fun directPost(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest?,
        verifier: OpenId4VpVerifier,
        verifierMetadataMode: VerifierMetadataMode = VerifierMetadataMode.OMIT_IF_OUT_OF_BAND,
    ): String = verifier.createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPost,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            verifierMetadataMode = verifierMetadataMode,
        ),
        creationOptions = CreationOptions.Query("av://"),
    ).getOrThrow().url.normalizeAvWalletUrl()

    private suspend fun directPostJwt(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest?,
        verifier: OpenId4VpVerifier,
        urlPrefix: String,
    ): String = verifier.createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPostJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
        ),
        creationOptions = CreationOptions.SignedRequestByReference(
            walletUrl = urlPrefix,
            requestUrl = configuration.publicContext.appendPath("${Paths.Transaction.GetUrl}/$transactionId"),
        ),
        // wallet URL was already delivered as QR code, only the request object content is needed here
    ).getOrThrow().loadRequestObject!!.invoke(null).getOrThrow()

    private suspend fun dcApiOpenIdUnsigned(
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest?,
        verifier: OpenId4VpVerifier,
        encryption: Boolean,
        transactionId: String,
    ): AuthenticationRequestParameters = verifier.createAuthnRequest(
        OpenId4VpRequestOptions(
            responseMode = if (!encryption) ResponseMode.DcApi else ResponseMode.DcApiJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            expectedOrigins = listOf(configuration.publicContext.toString()),
            populateClientId = false,
            state = transactionId,
        ),
    )

    private suspend fun dcApiOpenIdSigned(
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest?,
        verifier: OpenId4VpVerifier,
        encryption: Boolean,
        transactionId: String,
    ) = verifier.createAuthnRequestAsSignedRequestObject(
        OpenId4VpRequestOptions(
            responseMode = if (!encryption) ResponseMode.DcApi else ResponseMode.DcApiJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            expectedOrigins = listOf(configuration.publicContext.toString()),
            state = transactionId,
        ),
    ).getOrThrow().jws

    private suspend fun dcApiIsoMdoc(
        deviceRequest: DeviceRequest,
        verifier: Iso180137AnnexCVerifier,
        id: String,
    ): IsoMdocRequest = try {
        verifier.createRequest(Iso180137AnnexCRequestOptions(deviceRequest, id))
    } catch (e: UnsupportedOperationException) {
        throw ClientFacingException(e.message, e.cause)
    }

    fun validator(): Validator = Validator(
        tokenStatusResolver = TokenStatusResolverImpl(
            resolveStatusListToken = resolveStatusListToken(),
        ),
    )

    fun potentialValidatorSdJwt(): ValidatorSdJwt = ValidatorSdJwt(
        verifyJwsObject = VerifyJwsObject(
            publicKeyLookup = { jwsCompact ->
                remoteKeyLookup(jwsCompact)
            }
        ),
        validator = validator(),
    )

    fun potentialValidatorMdoc(): ValidatorMdoc = ValidatorMdoc(
        validator = validator(),
    )

    private fun resolveStatusListToken() = StatusListTokenResolver {
        Napier.i("Resolving token status for from $it")
        run {
            httpClient.get(it.string) {
                header(HttpHeaders.Accept, MediaTypes.Application.STATUSLIST_JWT)
            }.body<String>()
        }.let {
            JwsCompactTyped<StatusListTokenPayload>(it)
        }.let {
            StatusListJwt(it, Clock.System.now())
        }
    }

    private fun String.normalizeAvWalletUrl(): String = when {
        startsWith("av://localhost/?") -> "av://?" + removePrefix("av://localhost/?")
        startsWith("av://localhost?") -> "av://?" + removePrefix("av://localhost?")
        else -> this
    }

}

suspend fun Transaction.transactionGet(responseUrl: String): String = when (presentationMechanism) {
    PresentationMechanismEnum.PresentationExchange -> profile.transactionGet(
        transactionId = id,
        responseUrl = responseUrl,
        presentationRequest = presentationExchangeRequest,
    )

    PresentationMechanismEnum.DCQL -> profile.transactionGet(
        transactionId = id,
        responseUrl = responseUrl,
        presentationRequest = dcqlRequest,
    )

    PresentationMechanismEnum.DeviceRequest -> throw IllegalStateException("Not supported for this type of request.")
}


suspend fun Transaction.transactionGetDcApi(
    responseUrl: String,
    dcApiSignedOid4vp: Boolean,
): String {
    dcApiSignedOid4vpRequired = dcApiSignedOid4vp
    return profile.transactionGetDcApi(
        transactionId = id,
        responseUrl = responseUrl,
        deviceRequest = deviceRequest,
        dcqlRequest = dcqlRequest,
        dcApiSignedOid4vp = dcApiSignedOid4vp,
    )
}

data class TransactionContext(
    val id: String,
    val transactionGetUrl: String,
    val responseUrl: String,
    val dcApiUrl: String?,
)

interface PreparedProfile {
    val name: String
    val label: String
    val description: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme?
    val oid4vpVerifier: OpenId4VpVerifier?
    val iso180137Verifier: Iso180137AnnexCVerifier?
    val supportedOptions: Set<SupportedOptions>

    fun buildQrCodeUrl(requestUrl: String): String

    suspend fun buildWalletUrl(
        transaction: Transaction,
        context: TransactionContext,
    ): String = buildQrCodeUrl(context.transactionGetUrl)

    suspend fun transactionGet(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest?,
    ): String = throw IllegalStateException("Not supported for this profile")

    suspend fun transactionGetDcApi(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
        deviceRequest: DeviceRequest?,
        dcApiSignedOid4vp: Boolean,
    ): String = throw IllegalStateException("DC API not supported for this profile")
}
