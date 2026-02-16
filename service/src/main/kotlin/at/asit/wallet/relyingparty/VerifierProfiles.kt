package at.asit.wallet.relyingparty


import at.asit.wallet.relyingparty.ApiController.Transaction
import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.agent.validation.TokenStatusResolverImpl
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
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
            override val label = "HAIP (d05)"
            override val description = "x509_hash, OpenID4VP 1.0, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.HaipVp
            override val clientIdScheme = runBlocking { x509Hash() }
            override val verifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )

            override fun buildQrCodeUrl(requestUrl: String) =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
                presentationMechanism: PresentationMechanismEnum,
            ): String = directPostJwt(
                transactionId,
                responseUrl,
                requestOptionsCredentials,
                presentationMechanism,
                verifier
            )
        },

        object : Profile {
            override val name = "MDOCd23"
            override val label = "ISO 18013-7 (d23)"
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.MdocOpenId4Vp
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val verifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )

            override fun buildQrCodeUrl(requestUrl: String): String =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
                presentationMechanism: PresentationMechanismEnum,
            ): String = directPostJwt(
                transactionId,
                responseUrl,
                requestOptionsCredentials,
                presentationMechanism,
                verifier
            )
        },

        object : Profile {
            override val name = "EUDIW"
            override val label = "EUDIW Ref."
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = Paths.Schemes.OpenId4Vp
            override val clientIdScheme = runBlocking { x509SanDnsD23() }
            override val verifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validatorSdJwt = potentialValidatorSdJwt(),
                    validatorMdoc = potentialValidatorMdoc()
                ),
            )

            override fun buildQrCodeUrl(requestUrl: String) =
                buildQrCodeUrlByReference(urlPrefix, requestUrl, clientIdScheme)

            override suspend fun transactionGet(
                transactionId: String,
                responseUrl: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
                presentationMechanism: PresentationMechanismEnum,
            ): String = directPostJwt(
                transactionId,
                responseUrl,
                requestOptionsCredentials,
                presentationMechanism,
                verifier
            )
        }
    )

    private suspend fun x509SanDnsD23(): ClientIdScheme.CertificateSanDns = ClientIdScheme.CertificateSanDns(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
        clientIdDnsName = configuration.publicContext.host,
        redirectUri = configuration.publicContext.toString()
    )

    private suspend fun x509Hash(): ClientIdScheme.CertificateHash = ClientIdScheme.CertificateHash(
        chain = listOf(verifierKeyMaterial.getCertificate()!!),
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

    private suspend fun directPostJwt(
        transactionId: String,
        responseUrl: String,
        requestOptionsCredentials: Set<RequestOptionsCredential>,
        presentationMechanism: PresentationMechanismEnum,
        verifier: OpenId4VpVerifier,
    ): String = verifier.createAuthnRequestAsSignedRequestObject(
        OpenId4VpRequestOptions(
            state = transactionId,
            responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
            responseUrl = responseUrl,
            credentials = requestOptionsCredentials,
            presentationMechanism = presentationMechanism,
            encryption = true,
        )
    ).getOrThrow().serialize()

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

suspend fun Transaction.transactionGet(
    responseUrl: String,
): String = profile.transactionGet(
    transactionId = id,
    responseUrl = responseUrl,
    requestOptionsCredentials = request.toCredentials(),
    presentationMechanism = request.presentationMechanism,
)

interface Profile {
    val name: String
    val label: String
    val description: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme
    val verifier: OpenId4VpVerifier
    fun buildQrCodeUrl(requestUrl: String): String
    suspend fun transactionGet(
        transactionId: String,
        responseUrl: String,
        requestOptionsCredentials: Set<RequestOptionsCredential>,
        presentationMechanism: PresentationMechanismEnum,
    ): String
}


