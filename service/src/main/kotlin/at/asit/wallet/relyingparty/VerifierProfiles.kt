package at.asit.wallet.relyingparty


import at.asit.wallet.relyingparty.ApiController.Transaction
import at.asitplus.data.NonEmptyList
import at.asitplus.data.NonEmptyList.Companion.toNonEmptyList
import at.asitplus.dcapi.request.verifier.CredentialRequestOptions
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.ResponseMode
import at.asitplus.openid.VerifierInfo
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.HttpBody
import at.asitplus.wallet.lib.PreparedHttpResponse
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
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
import at.asitplus.wallet.lib.validation.CredentialValidator
import at.asitplus.wallet.lib.validation.ValidationPolicy
import io.github.aakira.napier.Napier
import io.ktor.http.*
import org.springframework.stereotype.Component


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
    private val credentialValidator: CredentialValidator,
    private val presentationPolicy: ValidationPolicy,
) {

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
            name = "MDOC10",
            label = "OpenID4VP 1.0: ISO 18013-7",
            description = "x509_san_dns, OpenID4VP 1.0, direct_post.jwt",
            urlPrefix = Paths.Schemes.MdocOpenId4Vp,
            clientIdSchemeType = ClientIdSchemeType.X509SanDns,
            deviceFlowConfig = DeviceFlowConfig(DeviceResponseMode.DirectPostJwt),
        ),
        VerifierProfile(
            name = "EUDIW",
            label = "OpenID4VP: EUDIW Ref.",
            description = "x509_san_dns, OpenID4VP 1.0, direct_post.jwt",
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

        private val defaultOid4vpVerifier = openId4VpVerifier(verifierKeyMaterial, clientIdScheme)

        private val defaultDcApiVerifier = dcApiVerifier(verifierKeyMaterial, clientIdScheme)

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
                transaction.transactionGet(context.responseUrl).body!!.text
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
                    // The AV profile serves an inline wallet URL rather than a signed request object.
                    body = HttpBody(
                        contentType = ContentType.parse(MediaTypes.Application.AUTHZ_REQ_JWT),
                        text = verifier.directPost(
                            transactionId = transactionId,
                            responseUrl = responseUrl,
                            dcqlRequest = dcqlRequest,
                            verifierMetadataMode = profile.deviceFlowConfig.verifierMetadataMode,
                            verifierInfo = verifierInfo,
                        ),
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

    private fun String.normalizeAvWalletUrl(): String = when {
        startsWith("av://localhost/?") -> "av://?" + removePrefix("av://localhost/?")
        startsWith("av://localhost?") -> "av://?" + removePrefix("av://localhost?")
        else -> this
    }

    /** The JWT of the selected WRPRC for OpenID4VP `verifier_info`, see [buildEuWrprc] for its CWT. */
    fun buildVerifierInfo(
        selectedWrprcId: Int?,
    ): NonEmptyList<VerifierInfo>? {
        val wrprc = selectedWrprc(selectedWrprcId) ?: return null
        val jwt = wrprc.jwt
            ?: return null.also { Napier.i("Selected WRPRC '$selectedWrprcId' has no JWT, not usable for OpenID4VP") }
        return listOf(
            VerifierInfo(
                format = OpenIdConstants.VerifierInfo.REGISTRATION_CERT_FORMAT,
                data = jwt,
                credentialIds = null,
            )
        ).toNonEmptyList()
    }

    /** The CWT of the selected WRPRC for ISO mdoc `euWrprc`, i.e. a COSE_Sign1, see [buildVerifierInfo] for its JWT. */
    fun buildEuWrprc(
        selectedWrprcId: Int?,
    ): ByteArray? {
        val wrprc = selectedWrprc(selectedWrprcId) ?: return null
        return wrprc.cwt
            ?: null.also { Napier.i("Selected WRPRC '$selectedWrprcId' has no CWT, not usable for ISO mdoc") }
    }

    private fun selectedWrprc(selectedWrprcId: Int?): RegistrationCertificateData? {
        val effectiveWrprcId = selectedWrprcId ?: return null
        return wrpCertificateStore.registrationCertificates[effectiveWrprcId]
            ?: throw ClientFacingException("Selected WRPRC '$effectiveWrprcId' is not available")
    }

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
        openId4VpVerifier(wrpac.keyMaterial, wrpacClientIdScheme)
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

        return dcApiVerifier(wrpac.keyMaterial, wrpacClientIdScheme)
    }

    /** Validates presentations with [credentialValidator] under [presentationPolicy]. */
    private fun openId4VpVerifier(keyMaterial: KeyMaterial, clientIdScheme: ClientIdScheme) = OpenId4VpVerifier(
        keyMaterial = keyMaterial,
        clientIdScheme = clientIdScheme,
        credentialValidator = credentialValidator,
        presentationPolicy = presentationPolicy,
    )

    /** Validates presentations with [credentialValidator] under [presentationPolicy]. */
    private fun dcApiVerifier(keyMaterial: KeyMaterial, clientIdScheme: ClientIdScheme) = DcApiVerifier(
        keyMaterial = keyMaterial,
        clientIdScheme = clientIdScheme,
        credentialValidator = credentialValidator,
        presentationPolicy = presentationPolicy,
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
