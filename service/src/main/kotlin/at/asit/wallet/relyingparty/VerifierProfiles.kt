package at.asit.wallet.relyingparty


import at.asit.wallet.relyingparty.ApiController.Transaction
import at.asitplus.catching
import at.asitplus.data.NonEmptyList
import at.asitplus.data.NonEmptyList.Companion.toNonEmptyList
import at.asitplus.dcapi.request.verifier.CredentialRequestOptions
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.ResponseMode
import at.asitplus.openid.VerifierInfo
import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.PreparedHttpResponse
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.agent.ValidatorMdoc
import at.asitplus.wallet.lib.agent.ValidatorSdJwt
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.agent.validation.TokenStatusResolverImpl
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.jws.PublicJsonWebKeyLookup
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.jws.VerifyJwsObjectFun
import at.asitplus.wallet.lib.jws.VerifyJwsObjectTrusted
import at.asitplus.wallet.lib.oauth2.OAuth2Utils
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.ClientIdScheme.*
import at.asitplus.wallet.lib.openid.CreationOptions
import at.asitplus.wallet.lib.openid.CreatedRequest
import at.asitplus.wallet.lib.openid.DcApiCreationOptions
import at.asitplus.wallet.lib.openid.DcApiVerifier
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.loadRequestObjectHttpResponse
import at.asitplus.wallet.lib.openid.VerifierMetadataMode
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Component
import kotlin.io.encoding.Base64


// Supported options for verifier interactions
enum class SupportedOptions {
    CROSS_DEVICE,
    SAME_DEVICE,
    DC_API
    ;

    val isUrlOrQrCode: Boolean
        get() = this == CROSS_DEVICE || this == SAME_DEVICE
    val isDcApi: Boolean
        get() = this == DC_API
}

// Which OpenID4VP DC API request to offer in a single navigator.credentials.get call.
// Signed and Unsigned are mutually exclusive (they'd overwrite each other's stored request).
enum class Oid4vpDcApiMode { NONE, SIGNED, UNSIGNED }

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

data class VerifierProfile(
    val name: String,
    val label: String,
    val description: String,
    val urlPrefix: String,
    val clientIdSchemeType: ClientIdSchemeType,
    val deviceFlowConfig: DeviceFlowConfig,
    val offeredByDefault: Boolean = true,
) {
    // DC API is offered for every profile; the request contents are chosen per-request in the UI.
    val supportedOptions: Set<SupportedOptions> = buildSet {
        add(SupportedOptions.CROSS_DEVICE)
        add(SupportedOptions.SAME_DEVICE)
        add(SupportedOptions.DC_API)
    }
}

// --- Component ---

@Component
class VerifierProfiles(
    private val configuration: AppConfigurationProperties,
    private val verifierKeyMaterial: KeyMaterial,
    private val wrpCertificateStore: WrpCertificateStore,
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
            catching {
                httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet()
            }.getOrElse {
                Napier.w("Could not resolve issuer metadata from $url", it)
                null
            }
        }

    /**
     * Verifies issuer signatures against the key the JWS asserts itself, and consults the issuer's
     * `jwt-vc-issuer` metadata only when the JWS names no key at all (e.g. a bare `kid`).
     *
     * This is what `VerifyJwsObject(publicKeyLookup = ...)` used to do; passing the lookup there now turns it
     * into a trust list that is the *only* accepted signer, which rejects every credential whose issuer
     * publishes no such metadata. Trust is decided separately, see [TrustListService].
     */
    private val verifyIssuerJws = VerifyJwsObjectFun { jwsObject ->
        when (jwsObject.jwsHeader.publicKey) {
            null -> VerifyJwsObjectTrusted(
                trustedKeys = PublicJsonWebKeyLookup { remoteKeyLookup(it) },
            )(jwsObject)

            else -> VerifyJwsObject()(jwsObject)
        }
    }

    val knownProfiles: List<VerifierProfile> = listOf(
        VerifierProfile(
            name = "EUDIW2026",
            label = "EUDI.Wallet 2026",
            description = "eu-eaap:// for redirects, x509_hash, OpenID4VP 1.0",
            urlPrefix = Paths.Schemes.EuEaap,
            clientIdSchemeType = ClientIdSchemeType.X509Hash,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
            offeredByDefault = false,
        ),
        VerifierProfile(
            name = "HAIPd05",
            label = "OpenID4VP: HAIP (d05)",
            description = "x509_hash, OpenID4VP 1.0, direct_post.jwt",
            urlPrefix = Paths.Schemes.HaipVp,
            clientIdSchemeType = ClientIdSchemeType.X509Hash,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
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
            name = "EUDIW",
            label = "OpenID4VP: EUDIW Ref.",
            description = "x509_san_dns, OpenID4VP d23, direct_post.jwt",
            urlPrefix = Paths.Schemes.OpenId4Vp,
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
        ),
    )

    suspend fun prepare(profile: VerifierProfile, context: TransactionContext, selectedWrpacId: Int?): PreparedProfile =
        PreparedVerifierProfile(
            profile = profile,
            context = context,
            clientIdScheme = profile.clientIdSchemeType.build(context),
            selectedWrpacId = selectedWrpacId,
        )

    private suspend fun ClientIdSchemeType.build(context: TransactionContext): ClientIdScheme = when (this) {
        ClientIdSchemeType.X509Hash -> CertificateHash(
            chain = listOf(verifierKeyMaterial.getCertificate()!!),
            redirectUri = configuration.publicContext.toString(),
        )

        ClientIdSchemeType.X509SanDns -> CertificateSanDns(
            chain = listOf(verifierKeyMaterial.getCertificate()!!),
            clientIdDnsName = configuration.publicContext.host,
            redirectUri = configuration.publicContext.toString()
        )

        ClientIdSchemeType.RedirectUri -> RedirectUri(
            redirectUri = context.responseUrl,
        )
    }

    private inner class PreparedVerifierProfile(
        private val profile: VerifierProfile,
        private val context: TransactionContext,
        override val clientIdScheme: ClientIdScheme,
        private val selectedWrpacId: Int?,
    ) : PreparedProfile {
        override val name: String get() = profile.name
        override val label: String get() = profile.label
        override val description: String get() = profile.description
        override val urlPrefix: String get() = profile.urlPrefix
        override val supportedOptions: Set<SupportedOptions> get() = profile.supportedOptions

        private val defaultOid4vpVerifier = OpenId4VpVerifier(
            keyMaterial = verifierKeyMaterial,
            clientIdScheme = clientIdScheme,
            verifier = buildVerifierAgent(clientIdScheme),
        )

        private val defaultDcApiVerifier = DcApiVerifier(
            keyMaterial = verifierKeyMaterial,
            clientIdScheme = clientIdScheme,
            verifier = buildVerifierAgent(clientIdScheme),
        )

        // Request creation stores an ephemeral response-decryption key in the verifier.
        // Keep the selected WRPAC verifier on this transaction for response validation.
        override val oid4vpVerifier: OpenId4VpVerifier by lazy {
            selectVerifier(selectedWrpacId, clientIdScheme, defaultOid4vpVerifier)
        }

        override val dcApiVerifier: DcApiVerifier by lazy {
            if (selectedWrpacId == null) defaultDcApiVerifier
            else selectOid4vpDcApiVerifier(selectedWrpacId, clientIdScheme)
        }

        // Created before publishing the transaction. Its URL and request loader use the same
        // selected verifier identity; retain that verifier for response decryption as well.
        private var deviceRequest: CreatedRequest? = null

        override suspend fun buildWalletUrl(
            transaction: Transaction,
            context: TransactionContext,
        ): String = when (profile.deviceFlowConfig.walletUrlStyle) {
            WalletUrlStyle.Inline -> {
                require(transaction.request.selectedWrpacId == null && transaction.request.selectedWrprcId == null) {
                    "VerifierProfile($name) cannot include a WRPAC or WRPRC"
                }
                transaction.transactionGet(context.responseUrl).body
            }

            WalletUrlStyle.ByReference -> {
                val createdRequest = deviceRequest ?: oid4vpVerifier.createDirectPostJwtRequest(
                    transactionId = transaction.id,
                    responseUrl = context.responseUrl,
                    presentationRequest = transaction.dcqlRequest,
                    verifierInfo = buildVerifierInfo(transaction.request.selectedWrprcId),
                    urlPrefix = urlPrefix,
                    requestUrl = context.transactionGetUrl,
                ).also { deviceRequest = it }
                createdRequest.url
            }
        }

        override suspend fun transactionGet(
            transactionId: String,
            responseUrl: String,
            dcqlRequest: CredentialPresentationRequest.DCQLRequest,
            verifierInfo: NonEmptyList<VerifierInfo>?,
        ): PreparedHttpResponse = oid4vpVerifier.let { verifier ->
            when (profile.deviceFlowConfig.responseMode) {
                DeviceResponseMode.DirectPost -> PreparedHttpResponse(
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, MediaTypes.Application.AUTHZ_REQ_JWT),
                    // The AV profile serves an inline wallet URL rather than a signed request object.
                    body = verifier.directPost(
                        transactionId = transactionId,
                        responseUrl = responseUrl,
                        dcqlRequest = dcqlRequest,
                        verifierMetadataMode = profile.deviceFlowConfig.verifierMetadataMode,
                        verifierInfo = verifierInfo,
                    ),
                )

                DeviceResponseMode.DirectPostJwt -> checkNotNull(deviceRequest) {
                    "Device request must be created before publishing the transaction"
                }.loadRequestObjectHttpResponse(null).getOrThrow()
            }
        }

        override suspend fun transactionGetDcApi(
            transactionId: String,
            responseUrl: String,
            dcqlRequest: CredentialPresentationRequest.DCQLRequest,
            oid4vpMode: Oid4vpDcApiMode,
            isoMdoc: Boolean,
            encrypt: Boolean,
            verifierInfo: NonEmptyList<VerifierInfo>?,
            euWrprc: ByteArray?,
        ): String = dcApiVerifier.let { verifier ->
            joseCompliantSerializer.encodeToString<CredentialRequestOptions>(
                verifier.createAuthnRequest(
                    requestOptions = OpenId4VpRequestOptions(
                        state = transactionId,
                        // Encryption toggle governs the OpenID4VP part; ISO Annex C is HPKE-encrypted internally regardless.
                        responseMode = if (encrypt) ResponseMode.DcApiJwt else ResponseMode.DcApi,
                        responseUrl = responseUrl,
                        presentationRequest = dcqlRequest,
                        expectedOrigins = listOf(element = context.dcApiOrigin),
                        verifierInfo = verifierInfo,
                        euWrprc = euWrprc,
                    ),
                    creationOptions = listOfNotNull(
                        when (oid4vpMode) {
                            Oid4vpDcApiMode.SIGNED -> DcApiCreationOptions.OpenId4VpSigned
                            Oid4vpDcApiMode.UNSIGNED -> DcApiCreationOptions.OpenId4VpUnsigned
                            Oid4vpDcApiMode.NONE -> null
                        },
                        if (isoMdoc) DcApiCreationOptions.Iso180137AnnexC else null,
                    ).toTypedArray<DcApiCreationOptions>()
                ).getOrThrow()
            )
        }
    }

    private suspend fun OpenId4VpVerifier.directPost(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        verifierMetadataMode: VerifierMetadataMode = VerifierMetadataMode.OMIT_IF_OUT_OF_BAND,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
    ): String = createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPost,
            responseUrl = responseUrl,
            presentationRequest = dcqlRequest,
            verifierMetadataMode = verifierMetadataMode,
            verifierInfo = verifierInfo,
        ),
        creationOptions = CreationOptions.Query("av://"),
    ).getOrThrow().url.normalizeAvWalletUrl()

    private suspend fun OpenId4VpVerifier.createDirectPostJwtRequest(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
        urlPrefix: String,
        requestUrl: String,
    ): CreatedRequest = createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPostJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            verifierInfo = verifierInfo
        ),
        creationOptions = CreationOptions.SignedRequestByReference(
            walletUrl = urlPrefix,
            requestUrl = requestUrl,
        ),
    ).getOrThrow()

    fun buildValidator(): Validator = Validator(
        tokenStatusResolver = TokenStatusResolverImpl(
            resolveStatusListToken = StatusListTokenFetcher(httpClient),
        ),
    )

    fun buildValidatorSdJwt(): ValidatorSdJwt = ValidatorSdJwt(
        verifyJwsObject = verifyIssuerJws,
        validator = buildValidator(),
    )

    /**
     * The default [at.asitplus.wallet.lib.cbor.VerifyCoseSignature] verifies `issuerAuth` against the
     * certificate transported in its COSE headers, the mdoc counterpart to [verifyIssuerJws]. Do not pass a
     * `publicKeyLookup` here: it turns signature verification into a trust list
     * ([at.asitplus.wallet.lib.cbor.VerifyCoseSignatureTrusted]), and trust is decided separately, see
     * [TrustListService].
     */
    fun buildValidatorMdoc(): ValidatorMdoc = ValidatorMdoc(
        validator = buildValidator(),
    )

    private fun String.normalizeAvWalletUrl(): String = when {
        startsWith("av://localhost/?") -> "av://?" + removePrefix("av://localhost/?")
        startsWith("av://localhost?") -> "av://?" + removePrefix("av://localhost?")
        else -> this
    }

    /** The selected WRPRC for OpenID4VP `verifier_info`, unless it is a CWT, see [buildEuWrprc]. */
    fun buildVerifierInfo(
        selectedWrprcId: Int?,
    ): NonEmptyList<VerifierInfo>? {
        val wrprc = selectedWrprc(selectedWrprcId) ?: return null
        if (wrprc.decodeCwtOrNull() != null) {
            Napier.i("Selected WRPRC '$selectedWrprcId' is a CWT, not usable for OpenID4VP")
            return null
        }

        return listOf(
            VerifierInfo(
                format = OpenIdConstants.VerifierInfo.REGISTRATION_CERT_FORMAT,
                data = wrprc,
                credentialIds = null,
            )
        ).toNonEmptyList()
    }

    /** The selected WRPRC for ISO mdoc `euWrprc`, if it is a CWT, i.e. a base64-encoded COSE_Sign1. */
    fun buildEuWrprc(
        selectedWrprcId: Int?,
    ): ByteArray? {
        val wrprc = selectedWrprc(selectedWrprcId) ?: return null
        return wrprc.decodeCwtOrNull()
            ?: null.also { Napier.i("Selected WRPRC '$selectedWrprcId' is not a CWT, not usable for ISO mdoc") }
    }

    private fun selectedWrprc(selectedWrprcId: Int?): String? {
        val effectiveWrprcId = selectedWrprcId ?: return null
        return wrpCertificateStore.registrationCertificates?.get(effectiveWrprcId)?.let { (_, content) ->
            content.trim().takeIf { it.isNotBlank() }
        } ?: throw ClientFacingException("Selected WRPRC '$effectiveWrprcId' is not available")
    }

    private fun String.decodeCwtOrNull(): ByteArray? = listOf(
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL),
        Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL),
    ).firstNotNullOfOrNull { runCatching { it.decode(this) }.getOrNull() }
        ?.takeIf { runCatching { coseCompliantSerializer.decodeFromByteArray<CoseSigned<ByteArray>>(it) }.isSuccess }

    private fun selectVerifier(
        selectedWrpacId: Int?,
        clientIdScheme: ClientIdScheme,
        oid4vpVerifier: OpenId4VpVerifier?,
    ): OpenId4VpVerifier = if (selectedWrpacId != null) {
        val wrpac = wrpCertificateStore.accessCertificates[selectedWrpacId]
            ?: throw ClientFacingException("Selected WRPAC '$selectedWrpacId' is not available")
        val wrpacChain = wrpac.certificateChain
            ?: throw ClientFacingException("Selected WRPAC '$selectedWrpacId' has no certificate chain")
        val wrpacClientIdScheme = buildWrpacClientIdScheme(wrpacChain, clientIdScheme)
        OpenId4VpVerifier(
            keyMaterial = wrpac.keyMaterial,
            clientIdScheme = wrpacClientIdScheme,
            verifier = buildVerifierAgent(wrpacClientIdScheme),
        )
    } else {
        oid4vpVerifier!!
    }

    private fun selectOid4vpDcApiVerifier(
        selectedWrpacId: Int,
        defaultClientIdScheme: ClientIdScheme,
    ): DcApiVerifier {
        val wrpac = wrpCertificateStore.accessCertificates[selectedWrpacId]
            ?: throw ClientFacingException("Selected WRPAC '$selectedWrpacId' is not available")
        val wrpacChain = wrpac.certificateChain
            ?: throw ClientFacingException("Selected WRPAC '$selectedWrpacId' has no certificate chain")
        val wrpacClientIdScheme = buildWrpacClientIdScheme(wrpacChain, defaultClientIdScheme)

        return DcApiVerifier(
            keyMaterial = wrpac.keyMaterial,
            clientIdScheme = wrpacClientIdScheme,
            verifier = buildVerifierAgent(wrpacClientIdScheme),
        )
    }

    private fun buildVerifierAgent(clientIdScheme: ClientIdScheme): VerifierAgent = VerifierAgent(
        identifier = clientIdScheme.clientId,
        validatorSdJwt = buildValidatorSdJwt(),
        validatorMdoc = buildValidatorMdoc()
    )

    private fun buildWrpacClientIdScheme(
        wrpacChain: at.asitplus.signum.indispensable.pki.CertificateChain,
        defaultClientIdScheme: ClientIdScheme,
    ): CertificateHash {
        val redirectUri = when (defaultClientIdScheme.canBuildCertificateHash()) {
            true -> defaultClientIdScheme.redirectUri
            else -> throw ClientFacingException(
                "A WRPAC is selected, but profile client_id scheme ${defaultClientIdScheme::class.simpleName} " +
                        "cannot be replaced with x509_hash"
            )
        }
        return runCatching {
            CertificateHash(
                chain = wrpacChain,
                redirectUri = redirectUri,
            )
        }.getOrElse {
            throw ClientFacingException("Failed to build x509_hash client_id from WRPAC", it)
        }
    }
}

suspend fun Transaction.transactionGet(
    responseUrl: String,
    verifierInfo: NonEmptyList<VerifierInfo>? = null,
): PreparedHttpResponse = profile.transactionGet(
    transactionId = id,
    responseUrl = responseUrl,
    dcqlRequest = dcqlRequest,
    verifierInfo = verifierInfo,
)

suspend fun Transaction.transactionGetDcApi(
    responseUrl: String,
    oid4vpMode: Oid4vpDcApiMode,
    isoMdoc: Boolean,
    encrypt: Boolean,
    verifierInfo: NonEmptyList<VerifierInfo>? = null,
    euWrprc: ByteArray? = null,
): String = profile.transactionGetDcApi(
    transactionId = id,
    responseUrl = responseUrl,
    dcqlRequest = dcqlRequest,
    oid4vpMode = oid4vpMode,
    isoMdoc = isoMdoc,
    encrypt = encrypt,
    verifierInfo = verifierInfo,
    euWrprc = euWrprc,
)

data class TransactionContext(
    val id: String,
    val transactionGetUrl: String,
    val responseUrl: String,
    val dcApiOrigin: String,
    val dcApiUrl: String?,
)

interface PreparedProfile {
    val name: String
    val label: String
    val description: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme?
    val oid4vpVerifier: OpenId4VpVerifier?
    val dcApiVerifier: DcApiVerifier?
    val supportedOptions: Set<SupportedOptions>

    suspend fun buildWalletUrl(
        transaction: Transaction,
        context: TransactionContext,
    ): String

    suspend fun transactionGet(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
    ): PreparedHttpResponse

    suspend fun transactionGetDcApi(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        oid4vpMode: Oid4vpDcApiMode,
        isoMdoc: Boolean,
        encrypt: Boolean,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
        euWrprc: ByteArray? = null,
    ): String
}

fun ClientIdScheme.canBuildCertificateHash() = when (this) {
    is CertificateSanDns -> true
    is CertificateHash -> true
    else -> false
}
