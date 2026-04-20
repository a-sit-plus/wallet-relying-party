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
import at.asitplus.signum.indispensable.josef.JwsSigned
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
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.iso.Iso180137AnnexCRequestOptions
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifier
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.oauth2.OAuth2Utils
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private suspend fun remoteKeyLookup(jwsSigned: JwsSigned<*>): Set<JsonWebKey>? =
        (jwsSigned.payload as? JsonObject)?.get("iss")?.jsonPrimitive?.content?.let { iss ->
            val url = OAuth2Utils.insertWellKnownPath(iss, OpenIdConstants.WellKnownPaths.JwtVcIssuer)
            Napier.i("Resolving Key for $iss from $url")
            httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet()
        }

    val knownProfiles: List<Profile> = listOf(

        object : Profile {
            override val name = "HAIPd05"
            override val label = "OpenID4VP: HAIP (d05)"
            override val description = "x509_hash, OpenID4VP 1.0, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.HaipVp
            override val clientIdScheme = runBlocking { x509Hash() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier: Iso180137AnnexCVerifier? = null
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.CROSS_DEVICE, SupportedOptions.SAME_DEVICE, SupportedOptions.OID4VP_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = true

            override fun buildQrCodeUrl(requestUrl: String) =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                presentationRequest: CredentialPresentationRequest?,
            ): String = directPostJwt(
                transactionId = transactionId,
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = oid4vpVerifier
            )

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String {
                dcApiSignedOid4vpRequired = dcApiSignedOid4vp
                val openId4VpRequest = buildOpenId4VpDcApiRequest(
                    transactionId = transactionId,
                    responseUrl = responseUrl,
                    presentationRequest = dcqlRequest,
                    verifier = oid4vpVerifier,
                    dcApiSignedOid4vp = dcApiSignedOid4vp,
                    encryption = true,
                )

                val getRequests = listOf(openId4VpRequest)
                val credentialRequestOptions = CredentialRequestOptions.create(getRequests)
                return vckJsonSerializer.encodeToString(credentialRequestOptions)
            }
        },

        object : Profile {
            override val name = "AV"
            override val label = "OpenID4VP: AV"
            override val description = "redirect_uri, OpenID4VP 1.0, direct_post"
            override val urlPrefix = Paths.Schemes.Av
            override val clientIdScheme = runBlocking { redirectUri() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier = Iso180137AnnexCVerifier(validatorMdoc = potentialValidatorMdoc())
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.CROSS_DEVICE, SupportedOptions.SAME_DEVICE, SupportedOptions.ISO_MDOC_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = null

            override fun buildQrCodeUrl(requestUrl: String) =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                presentationRequest: CredentialPresentationRequest?,
            ): String = directPost(
                transactionId = transactionId,
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = oid4vpVerifier
            )

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String = vckJsonSerializer.encodeToString(
                CredentialRequestOptions.create(
                    listOf(
                        DigitalCredentialGetRequest.IsoMdoc(
                            dcApiIsoMdoc(
                                deviceRequest = deviceRequest
                                    ?: throw IllegalStateException("Device request is not available"),
                                verifier = iso180137Verifier,
                                id = transactionId
                            )
                        )
                    )
                )
            )
        },

        object : Profile {
            override val name = "UOID4VP"
            override val label = "DCAPI: Unencrypted OpenID4VP"
            override val description = "DCAPI, OpenID4VP 1.0, direct_post"
            override val urlPrefix = ""
            override val clientIdScheme = runBlocking { x509Hash() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier: Iso180137AnnexCVerifier? = null
            override val supportedOptions: Set<SupportedOptions> = setOf(SupportedOptions.OID4VP_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = null
            override fun buildQrCodeUrl(requestUrl: String) = requestUrl

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String = vckJsonSerializer.encodeToString(
                CredentialRequestOptions.create(
                    listOf(
                        buildOpenId4VpDcApiRequest(
                            transactionId = transactionId,
                            responseUrl = responseUrl,
                            presentationRequest = dcqlRequest,
                            verifier = oid4vpVerifier,
                            dcApiSignedOid4vp = dcApiSignedOid4vp,
                            encryption = false,
                        )
                    )
                )
            ).also {
                dcApiSignedOid4vpRequired = dcApiSignedOid4vp
            }
        },

        object : Profile {
            override val name = "MDOCd23"
            override val label = "OpenID4VP: ISO 18013-7 (d23)"
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.MdocOpenId4Vp
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier: Iso180137AnnexCVerifier? = null
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.CROSS_DEVICE, SupportedOptions.SAME_DEVICE)
            override var dcApiSignedOid4vpRequired: Boolean? = null

            override fun buildQrCodeUrl(requestUrl: String): String =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                presentationRequest: CredentialPresentationRequest?,
            ): String = directPostJwt(
                transactionId = transactionId,
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = oid4vpVerifier
            )
        },

        object : Profile {
            override val name = "MDOCISO"
            override val label = "DCAPI: ISO 18013-7 (Annex C)"
            override val description = "ISO 18013-7 Annex C"
            override val urlPrefix = ""
            override val clientIdScheme = null
            override val oid4vpVerifier = null
            override val iso180137Verifier = Iso180137AnnexCVerifier(validatorMdoc = potentialValidatorMdoc())
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.ISO_MDOC_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = null

            override fun buildQrCodeUrl(requestUrl: String): String = requestUrl

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String = vckJsonSerializer.encodeToString(
                CredentialRequestOptions.create(
                    listOf(
                        DigitalCredentialGetRequest.IsoMdoc(
                            dcApiIsoMdoc(
                                deviceRequest = deviceRequest
                                    ?: throw IllegalStateException("Device request is not available"),
                                verifier = iso180137Verifier,
                                id = transactionId
                            )
                        )
                    )
                )
            )
        },

        object : Profile {
            override val name = "EUDIW"
            override val label = "OpenID4VP: EUDIW Ref."
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.OpenId4Vp
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier: Iso180137AnnexCVerifier? = null
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.CROSS_DEVICE, SupportedOptions.SAME_DEVICE)
            override var dcApiSignedOid4vpRequired: Boolean? = null

            override fun buildQrCodeUrl(requestUrl: String) =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                presentationRequest: CredentialPresentationRequest?,
            ): String = directPostJwt(
                transactionId = transactionId,
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = oid4vpVerifier
            )
        },
        object : Profile {
            override val name = "DC_API_COMBINED"
            override val label = "DCAPI: Unencrypted OpenID4VP + ISO 18013-7"
            override val description = "Unencrypted OpenID4VP (signed/unsigned) and ISO 18013-7 Annex-C via DC API"
            override val urlPrefix = ""
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier = Iso180137AnnexCVerifier(validatorMdoc = potentialValidatorMdoc())

            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.OID4VP_DC_API, SupportedOptions.ISO_MDOC_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = true

            override fun buildQrCodeUrl(requestUrl: String) = requestUrl

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String = vckJsonSerializer.encodeToString(
                CredentialRequestOptions.create(
                    listOf(
                        DigitalCredentialGetRequest.IsoMdoc(
                            dcApiIsoMdoc(
                                deviceRequest = deviceRequest
                                    ?: throw IllegalStateException("Device request is not available"),
                                verifier = iso180137Verifier,
                                id = transactionId
                            )
                        ), buildOpenId4VpDcApiRequest(
                            transactionId = transactionId,
                            responseUrl = responseUrl,
                            presentationRequest = dcqlRequest,
                            verifier = oid4vpVerifier,
                            dcApiSignedOid4vp = dcApiSignedOid4vp,
                            encryption = false,
                        )
                    )
                )
            ).also {
                dcApiSignedOid4vpRequired = dcApiSignedOid4vp
            }
        },

        object : Profile {
            override val name = "DC_API_COMBINED_ENCRYPTED"
            override val label = "DCAPI: Encrypted OpenID4VP + ISO 18013-7"
            override val description = "Encrypted OpenID4VP (signed/unsigned) and ISO 18013-7 Annex-C via DC API"
            override val urlPrefix = ""
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val oid4vpVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )
            override val iso180137Verifier = Iso180137AnnexCVerifier(validatorMdoc = potentialValidatorMdoc())
            override val supportedOptions: Set<SupportedOptions> =
                setOf(SupportedOptions.OID4VP_DC_API, SupportedOptions.ISO_MDOC_DC_API)
            override var dcApiSignedOid4vpRequired: Boolean? = true

            override fun buildQrCodeUrl(requestUrl: String) = requestUrl

            override suspend fun transactionGetDcApi(
                transactionId: String,
                responseUrl: String,
                dcqlRequest: CredentialPresentationRequest.DCQLRequest?,
                deviceRequest: DeviceRequest?,
                dcApiSignedOid4vp: Boolean,
            ): String = vckJsonSerializer.encodeToString(
                CredentialRequestOptions.create(
                    listOf(
                        DigitalCredentialGetRequest.IsoMdoc(
                            dcApiIsoMdoc(
                                deviceRequest = deviceRequest
                                    ?: throw IllegalStateException("Device request is not available"),
                                verifier = iso180137Verifier,
                                id = transactionId
                            )
                        ), buildOpenId4VpDcApiRequest(
                            transactionId = transactionId,
                            responseUrl = responseUrl,
                            presentationRequest = dcqlRequest,
                            verifier = oid4vpVerifier,
                            dcApiSignedOid4vp = dcApiSignedOid4vp,
                            encryption = true,
                        )
                    )
                )
            ).also {
                dcApiSignedOid4vpRequired = dcApiSignedOid4vp
            }
        },
    )

    private suspend fun x509SanDnsD23(): ClientIdScheme.CertificateSanDns = ClientIdScheme.CertificateSanDns(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
        clientIdDnsName = configuration.publicContext.host,
        redirectUri = configuration.publicContext.toString()
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
            JarRequestParameters(
                request = dcApiSigned(
                    responseUrl = responseUrl,
                    presentationRequest = presentationRequest,
                    verifier = verifier,
                    encryption = encryption,
                    transactionId = transactionId
                )
            )
        )
    } else {
        DigitalCredentialGetRequest.OpenId4VpUnsigned(
            dcApi(
                responseUrl = responseUrl,
                presentationRequest = presentationRequest,
                verifier = verifier,
                encryption = encryption,
                transactionId = transactionId
            )
        )
    }

    private suspend fun x509Hash() = ClientIdScheme.CertificateHash(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
        redirectUri = configuration.publicContext.toString(),
    )

    private suspend fun redirectUri() = ClientIdScheme.RedirectUri(
        redirectUri = configuration.publicContext.toString(),
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
    ): String = verifier.createAuthnRequest(
        requestOptions = OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPost,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
        ),
        creationOptions = OpenId4VpVerifier.CreationOptions.Query("av://"),
    ).getOrThrow().url

    private suspend fun directPostJwt(
        transactionId: String,
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest?,
        verifier: OpenId4VpVerifier,
    ): String = verifier.createAuthnRequestAsSignedRequestObject(
        OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = ResponseMode.DirectPostJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
        ),
    ).getOrThrow().serialize()

    private suspend fun dcApi(
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

    private suspend fun dcApiSigned(
        responseUrl: String,
        presentationRequest: CredentialPresentationRequest.DCQLRequest?,
        verifier: OpenId4VpVerifier,
        encryption: Boolean,
        transactionId: String,
    ): String = verifier.createAuthnRequestAsSignedRequestObject(
        OpenId4VpRequestOptions(
            responseMode = if (!encryption) ResponseMode.DcApi else ResponseMode.DcApiJwt,
            responseUrl = responseUrl,
            presentationRequest = presentationRequest,
            expectedOrigins = listOf(configuration.publicContext.toString()),
            state = transactionId,
        ),
    ).getOrThrow().serialize()

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
            publicKeyLookup = { jwsSigned ->
                remoteKeyLookup(jwsSigned)
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
            JwsSigned.deserialize<StatusListTokenPayload>(StatusListTokenPayload.serializer(), it).getOrThrow()
        }.let {
            StatusListJwt(it, Clock.System.now())
        }
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
): String = profile.transactionGetDcApi(
    transactionId = id,
    responseUrl = responseUrl,
    deviceRequest = deviceRequest,
    dcqlRequest = dcqlRequest,
    dcApiSignedOid4vp = dcApiSignedOid4vp,
)

interface Profile {
    val name: String
    val label: String
    val description: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme?
    val oid4vpVerifier: OpenId4VpVerifier?
    val iso180137Verifier: Iso180137AnnexCVerifier?
    val supportedOptions: Set<SupportedOptions>
    var dcApiSignedOid4vpRequired: Boolean?

    fun buildQrCodeUrl(requestUrl: String): String

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


