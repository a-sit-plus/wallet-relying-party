package at.asit.wallet.relyingparty


import at.asit.wallet.relyingparty.ApiController.Transaction
import at.asitplus.catching
import at.asitplus.data.NonEmptyList
import at.asitplus.data.NonEmptyList.Companion.toNonEmptyList
import at.asitplus.dcapi.request.verifier.CredentialRequestOptions
import at.asitplus.dcapi.request.verifier.DigitalCredentialGetRequest
import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.ResponseMode
import at.asitplus.openid.VerifierInfo
import at.asitplus.openid.encodeToParameters
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.JwsFlattened
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJwsFlattened
import at.asitplus.signum.indispensable.josef.toJwsGeneral
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
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
import at.asitplus.wallet.lib.jws.PublicJsonWebKeyLookup
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.jws.VerifyJwsObjectFun
import at.asitplus.wallet.lib.jws.VerifyJwsObjectTrusted
import at.asitplus.wallet.lib.oauth2.OAuth2Utils
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.ClientIdScheme.*
import at.asitplus.wallet.lib.openid.CreationOptions
import at.asitplus.wallet.lib.openid.DcApiCreationOptions
import at.asitplus.wallet.lib.openid.DcApiRequestSigner
import at.asitplus.wallet.lib.openid.DcApiVerifier
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.VerifierMetadataMode
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.springframework.stereotype.Component
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import kotlin.random.Random
import kotlin.time.Clock


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
enum class Oid4vpDcApiMode { NONE, SIGNED, MULTISIGNED, UNSIGNED }

/** Verifier identities for [Oid4vpDcApiMode.SIGNED] and [Oid4vpDcApiMode.MULTISIGNED] requests. */
data class DcApiSignerSelection(
    /** Configured signer ids, defaulting to the first configured signer. */
    val signerIds: List<String> = emptyList(),
    /**
     * WRPRC id per signer id to carry as that signature's `verifier_info`, or `null` for none. Signers not listed
     * default to none, except the WRPAC signer of [preferredWrpacId], which defaults to
     * [wrpacRegistrationCertificateId].
     */
    val verifierInfo: Map<String, Int?> = emptyMap(),
    /**
     * Test option: selected signers whose signature value is replaced by random bytes, keeping their protected header
     * as if copied from a genuine request. Wallets must neither accept nor present these identities, and must reject
     * the request with `invalid_request` if every signature is forged.
     */
    val forgedSignerIds: Set<String> = emptySet(),
    /** Access certificate whose WRPAC signer to default to rather than the first configured one, see `selectedWrpacId`. */
    val preferredWrpacId: Int? = null,
    /** Default WRPRC of the preferred WRPAC signer, see `selectedWrprcId`. */
    val wrpacRegistrationCertificateId: Int? = null,
    /**
     * Test option: allow a WRPRC in the `verifier_info` of a signer that does not use a WRPAC. Registration
     * certificates are issued to the relying party identified by its WRPAC, so wallets should reject such a binding.
     */
    val allowMismatchedVerifierInfo: Boolean = false,
)

@Serializable
data class DcApiSignerDescriptor(
    val id: String,
    val label: String,
    val scheme: DcApiSignerScheme,
    /** Signs with the WRPAC, i.e. the identity the configured registration certificates were issued to. */
    val wrpac: Boolean = false,
)

@Serializable
data class RegistrationCertificateDescriptor(
    val id: Int,
    val label: String,
)

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
    dcApiSignerRegistry: DcApiSignerRegistry,
) {

    private val dcApiSignerMaterials = dcApiSignerRegistry.signers

    /** Configured signers, followed by one WRPAC signer per available access certificate. */
    fun dcApiSigners(): List<DcApiSignerDescriptor> =
        (dcApiSignerMaterials + wrpacSignerMaterials()).map {
            DcApiSignerDescriptor(
                id = it.configuration.id,
                label = it.configuration.label,
                scheme = it.configuration.scheme,
                wrpac = it.configuration.id.isWrpacSignerId(),
            )
        }

    fun dcApiRegistrationCertificates(): List<RegistrationCertificateDescriptor> =
        wrpCertificateStore.registrationCertificates.orEmpty().map { (id, certificate) ->
            RegistrationCertificateDescriptor(id, certificate.first.label)
        }

    /** Resolved per request, as the access certificates are managed by [WrpCertificateStore]. */
    private fun wrpacSignerMaterials(): List<DcApiSignerMaterial> =
        wrpCertificateStore.accessCertificates.mapNotNull { (id, wrpac) ->
            val chain = wrpac.certificateChain?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            DcApiSignerMaterial(
                configuration = DcApiSignerConfiguration(
                    id = wrpacSignerId(id),
                    label = "${wrpac.label} (WRPAC, x509_hash)",
                    scheme = DcApiSignerScheme.X509_HASH,
                ),
                keyMaterial = wrpac.keyMaterial,
                verifierAttestation = null,
                certificateChain = chain,
            )
        }

    private fun dcApiSignerMaterial(id: String): DcApiSignerMaterial =
        (dcApiSignerMaterials + wrpacSignerMaterials()).firstOrNull { it.configuration.id == id }
            ?: throw ClientFacingException("Unknown DC API signer '$id'")

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

        // Signed DC API requests are signed by the selected DC API signers, including WRPACs, see
        // resolveDcApiSigners, so this verifier only creates the payload and validates the response
        override val dcApiVerifier: DcApiVerifier get() = defaultDcApiVerifier

        override fun buildQrCodeUrl(requestUrl: String): String =
            buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

        override suspend fun buildWalletUrl(
            transaction: Transaction,
            context: TransactionContext,
        ): String = when (profile.deviceFlowConfig.walletUrlStyle) {
            WalletUrlStyle.Inline -> {
                require(transaction.request.selectedWrpacId == null && transaction.request.selectedWrprcId == null) {
                    "VerifierProfile($name) cannot include a WRPAC or WRPRC"
                }
                transaction.transactionGet(context.responseUrl)
            }

            WalletUrlStyle.ByReference -> buildQrCodeUrl(context.transactionGetUrl)
        }

        override suspend fun transactionGet(
            transactionId: String,
            responseUrl: String,
            dcqlRequest: CredentialPresentationRequest.DCQLRequest,
            verifierInfo: NonEmptyList<VerifierInfo>?,
        ): String = oid4vpVerifier.let { verifier ->
            when (profile.deviceFlowConfig.responseMode) {
                DeviceResponseMode.DirectPost -> verifier.directPost(
                    transactionId = transactionId,
                    responseUrl = responseUrl,
                    dcqlRequest = dcqlRequest,
                    verifierMetadataMode = profile.deviceFlowConfig.verifierMetadataMode,
                    verifierInfo = verifierInfo
                )

                DeviceResponseMode.DirectPostJwt -> verifier.directPostJwt(transactionId, responseUrl, dcqlRequest, verifierInfo, urlPrefix)
            }
        }

        override suspend fun transactionGetDcApi(
            transactionId: String,
            responseUrl: String,
            dcqlRequest: CredentialPresentationRequest.DCQLRequest,
            oid4vpMode: Oid4vpDcApiMode,
            isoMdoc: Boolean,
            encrypt: Boolean,
            signerSelection: DcApiSignerSelection,
        ): String = buildDcApiResponse(
            transactionId = transactionId,
            dcqlRequest = dcqlRequest,
            oid4vpMode = oid4vpMode,
            isoMdoc = isoMdoc,
            encrypt = encrypt,
            dcApiVerifier = dcApiVerifier,
            expectedOrigin = context.dcApiOrigin,
            signerSelection = signerSelection,
        )
    }

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

    private suspend fun OpenId4VpVerifier.directPostJwt(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
        urlPrefix: String,
    ): String = createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPostJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            verifierInfo = verifierInfo
        ),
        creationOptions = CreationOptions.SignedRequestByReference(
            walletUrl = urlPrefix,
            requestUrl = configuration.publicContext.appendPath("${Paths.Transaction.GetUrl}/$transactionId"),
        ),
        // wallet URL was already delivered as QR code, only the request object content is needed here
    ).getOrThrow().loadRequestObject!!.invoke(null).getOrThrow()

    fun buildValidator(): Validator = Validator(
        tokenStatusResolver = TokenStatusResolverImpl(
            resolveStatusListToken = buildStatusListTokenResolver(),
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

    private fun buildStatusListTokenResolver() = StatusListTokenResolver {
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

    private suspend fun buildDcApiResponse(
        transactionId: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        oid4vpMode: Oid4vpDcApiMode,
        isoMdoc: Boolean,
        encrypt: Boolean,
        dcApiVerifier: DcApiVerifier,
        expectedOrigin: String,
        signerSelection: DcApiSignerSelection,
    ): String {
        if (signerSelection.forgedSignerIds.isNotEmpty() &&
            oid4vpMode != Oid4vpDcApiMode.SIGNED && oid4vpMode != Oid4vpDcApiMode.MULTISIGNED
        ) {
            throw ClientFacingException("forgedSignerId is only supported for signed and multisigned OpenID4VP")
        }
        val signerIds = selectedDcApiSignerIds(signerSelection)
        val requestOptions = dcApiVerifier.createAuthnRequest(
            requestOptions = OpenId4VpRequestOptions(
                state = transactionId,
                // Encryption toggle governs the OpenID4VP part; ISO Annex C is HPKE-encrypted internally regardless.
                responseMode = if (encrypt) ResponseMode.DcApiJwt else ResponseMode.DcApi,
                presentationRequest = dcqlRequest,
                expectedOrigins = listOf(expectedOrigin),
                verifierInfo = null,
            ),
            creationOptions = listOfNotNull(
                when (oid4vpMode) {
                    Oid4vpDcApiMode.SIGNED -> DcApiCreationOptions.OpenId4VpSignedBy(
                        resolveDcApiSigners(signerSelection, requiredCount = 1).single()
                    )
                    Oid4vpDcApiMode.MULTISIGNED -> DcApiCreationOptions.OpenId4VpMultiSigned(
                        resolveDcApiSigners(signerSelection, requiredCount = 2)
                    )
                    Oid4vpDcApiMode.UNSIGNED -> DcApiCreationOptions.OpenId4VpUnsigned
                    Oid4vpDcApiMode.NONE -> null
                },
                if (isoMdoc) DcApiCreationOptions.Iso180137AnnexC else null,
            ).toTypedArray()
        ).getOrThrow()
        if (signerSelection.forgedSignerIds.isEmpty()) {
            return joseCompliantSerializer.encodeToString(requestOptions)
        }
        // VC-K keeps the order of the signers, so signature i belongs to signerIds[i]
        val forgedIndices = signerIds.indices.filter { signerIds[it] in signerSelection.forgedSignerIds }.toSet()
        return joseCompliantSerializer.encodeToString(
            CredentialRequestOptions.create(requestOptions.digital.requests.map {
                when (it) {
                    is DigitalCredentialGetRequest.OpenId4VpSigned -> it.withForgedSignature()
                    is DigitalCredentialGetRequest.OpenId4VpMultiSigned -> it.withForgedSignatures(forgedIndices)
                    else -> it
                }
            })
        )
    }

    private fun selectedDcApiSignerIds(signerSelection: DcApiSignerSelection): List<String> {
        val ids = signerSelection.signerIds.ifEmpty {
            val signer = signerSelection.preferredWrpacId?.let { wrpacId ->
                wrpacSignerMaterials().firstOrNull { it.configuration.id == wrpacSignerId(wrpacId) }
                    ?: throw ClientFacingException("Selected WRPAC '$wrpacId' is not available")
            } ?: dcApiSignerMaterials.first()
            listOf(signer.configuration.id)
        }
        if (ids.distinct().size != ids.size) {
            throw ClientFacingException("DC API signer ids must be distinct")
        }
        if (signerSelection.forgedSignerIds.any { it !in ids }) {
            throw ClientFacingException("forgedSignerId may only reference selected signer ids")
        }
        return ids
    }

    private suspend fun resolveDcApiSigners(
        signerSelection: DcApiSignerSelection,
        requiredCount: Int,
    ): List<DcApiRequestSigner> {
        val ids = selectedDcApiSignerIds(signerSelection)
        if (signerSelection.verifierInfo.keys.any { it !in ids }) {
            throw ClientFacingException("signerVerifierInfo may only reference selected signer ids")
        }
        if (!(ids.size == requiredCount || requiredCount > 1 && ids.size >= requiredCount)) {
            throw ClientFacingException(
                if (requiredCount == 1) "Signed OpenID4VP requires exactly one signer"
                else "Multisigned OpenID4VP requires at least two signers"
            )
        }
        return ids.map { id ->
            val material = dcApiSignerMaterial(id)
            val config = material.configuration
            val certificateChain = suspend { material.certificateChain ?: material.keyMaterial.certificateChainForX5c() }
            val clientIdScheme = when (config.scheme) {
                DcApiSignerScheme.X509_SAN_DNS -> ClientIdScheme.CertificateSanDns(
                    chain = certificateChain() ?: throw ClientFacingException("Signer '$id' has no certificate chain"),
                    clientIdDnsName = configuration.publicContext.host,
                    redirectUri = configuration.publicContext.toString(),
                )
                DcApiSignerScheme.X509_HASH -> ClientIdScheme.CertificateHash(
                    chain = certificateChain() ?: throw ClientFacingException("Signer '$id' has no certificate chain"),
                    redirectUri = configuration.publicContext.toString(),
                )
                DcApiSignerScheme.VERIFIER_ATTESTATION -> ClientIdScheme.VerifierAttestation(
                    attestationJwt = requireNotNull(material.verifierAttestation),
                    redirectUri = configuration.publicContext.toString(),
                )
            }
            DcApiRequestSigner(
                clientIdScheme = clientIdScheme,
                keyMaterial = material.keyMaterial,
                verifierInfo = buildVerifierInfo(signerSelection.registrationCertificateIdFor(id)),
            )
        }
    }

    fun buildVerifierInfo(
        selectedWrprcId: Int?,
    ): NonEmptyList<VerifierInfo>? {
        val effectiveWrprcId = selectedWrprcId ?: return null
        val wrprc = wrpCertificateStore.registrationCertificates?.get(effectiveWrprcId)?.let { (_, content) ->
            content.trim().takeIf { it.isNotBlank() }
        } ?: throw ClientFacingException("Selected WRPRC '$effectiveWrprcId' is not available")

        return listOf(
            VerifierInfo(
                format = OpenIdConstants.VerifierInfo.REGISTRATION_CERT_FORMAT,
                data = wrprc,
                credentialIds = null,
            )
        ).toNonEmptyList()
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
        OpenId4VpVerifier(
            keyMaterial = wrpac.keyMaterial,
            clientIdScheme = wrpacClientIdScheme,
            verifier = buildVerifierAgent(wrpacClientIdScheme),
        )
    } else {
        oid4vpVerifier!!
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
): String = profile.transactionGet(
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
    signerSelection: DcApiSignerSelection = DcApiSignerSelection(),
): String = profile.transactionGetDcApi(
    transactionId = id,
    responseUrl = responseUrl,
    dcqlRequest = dcqlRequest,
    oid4vpMode = oid4vpMode,
    isoMdoc = isoMdoc,
    encrypt = encrypt,
    signerSelection = signerSelection,
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

    fun buildQrCodeUrl(requestUrl: String): String

    suspend fun buildWalletUrl(
        transaction: Transaction,
        context: TransactionContext,
    ): String = buildQrCodeUrl(context.transactionGetUrl)

    suspend fun transactionGet(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        verifierInfo: NonEmptyList<VerifierInfo>? = null,
    ): String

    suspend fun transactionGetDcApi(
        transactionId: String,
        responseUrl: String,
        dcqlRequest: CredentialPresentationRequest.DCQLRequest,
        oid4vpMode: Oid4vpDcApiMode,
        isoMdoc: Boolean,
        encrypt: Boolean,
        signerSelection: DcApiSignerSelection = DcApiSignerSelection(),
    ): String
}

fun ClientIdScheme.canBuildCertificateHash() = when (this) {
    is CertificateSanDns -> true
    is CertificateHash -> true
    else -> false
}

/** DC API signer ids `wrpac-<id>` are reserved for the access certificates of [WrpCertificateStore]. */
const val WRPAC_SIGNER_ID_PREFIX = "wrpac-"

fun wrpacSignerId(accessCertificateId: Int) = "$WRPAC_SIGNER_ID_PREFIX$accessCertificateId"

private fun String.isWrpacSignerId() = startsWith(WRPAC_SIGNER_ID_PREFIX)

/*
 * Forging replaces a signature value with random bytes of the same length, keeping the protected header bytes
 * unchanged, as if the header had been copied from a genuine request of that verifier.
 */

private fun forgedSignatureValue(length: Int) = Random.nextBytes(length).encodeToString(Base64UrlStrict)

private fun DigitalCredentialGetRequest.OpenId4VpSigned.withForgedSignature(): DigitalCredentialGetRequest.OpenId4VpSigned {
    val jws = data.request
    val forged = JwsCompact(
        jws.toString().substringBeforeLast('.') + "." + forgedSignatureValue(jws.plainSignature.size)
    )
    return copy(data = DigitalCredentialGetRequest.OpenId4Vp.SignedDataElement(forged))
}

private fun DigitalCredentialGetRequest.OpenId4VpMultiSigned.withForgedSignatures(
    indices: Set<Int>,
): DigitalCredentialGetRequest.OpenId4VpMultiSigned {
    val signatures = data.request.toJwsFlattened().mapIndexed { index, signature ->
        if (index !in indices) return@mapIndexed signature
        // JwsFlattened has no public constructor from raw parts, so swap the value in its JSON representation
        joseCompliantSerializer.decodeFromJsonElement(
            JwsFlattened.serializer(),
            JsonObject(
                joseCompliantSerializer.encodeToJsonElement(JwsFlattened.serializer(), signature).jsonObject +
                        ("signature" to JsonPrimitive(forgedSignatureValue(signature.plainSignature.size)))
            ),
        )
    }
    return copy(data = DigitalCredentialGetRequest.OpenId4Vp.MultiSignedDataElement(signatures.toJwsGeneral()))
}

/**
 * The WRPRC for signer [id], rejecting one bound to a signer that does not use a WRPAC unless
 * [DcApiSignerSelection.allowMismatchedVerifierInfo] is set.
 */
private fun DcApiSignerSelection.registrationCertificateIdFor(id: String): Int? {
    val preferred = preferredWrpacId?.let(::wrpacSignerId) == id
    val wrprcId = if (id in verifierInfo) verifierInfo[id] else wrpacRegistrationCertificateId.takeIf { preferred }
    // Which access certificate a WRPRC belongs to is not configured, so only the binding to a WRPAC is enforced
    if (wrprcId != null && !id.isWrpacSignerId() && !allowMismatchedVerifierInfo) {
        throw ClientFacingException(
            "Registration certificates are issued to the relying party of a WRPAC, so signer '$id' cannot carry " +
                    "one; allow mismatched verifier_info to test this"
        )
    }
    return wrprcId
}

/** The full chain for `x5c` if the key material has one, e.g. a WRPAC issued by a registrar, else the leaf. */
private suspend fun KeyMaterial.certificateChainForX5c(): List<X509Certificate>? =
    (this as? KeyStoreMaterial)?.getCertificateChain()?.takeIf { it.isNotEmpty() }
        ?: getCertificate()?.let(::listOf)
