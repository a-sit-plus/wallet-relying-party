package at.asit.apps.terminal_sp.prototype.server


import at.asit.apps.terminal_sp.prototype.server.ApiController.Transaction
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.jws.DefaultVerifierJwsService
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.RequestOptions
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.security.KeyStore
import kotlin.random.Random

class VerifierProfiles(private val publicUrl: String) {

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

    private fun buildPotentialKeyLookup(jwsSigned: JwsSigned<*>): Set<JsonWebKey>? =
        (jwsSigned.payload as? JsonObject)?.get("iss")?.jsonPrimitive?.content?.let { iss ->
            runBlocking {
                val url = buildVcIssuerUrl(iss)
                Napier.i("Resolving Key for $iss from $url")
                httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet()
            }
        }

    private fun buildVcIssuerUrl(iss: String): Url = URLBuilder(urlString = iss).apply {
        path(".well-known", "jwt-vc-issuer", *(pathSegments.toTypedArray()))
    }.build()

    val knownProfiles: List<Profile> = listOf(
        object : Profile {
            override val name = "HAIP"
            override val label = "HAIP (Potential)"
            override val urlPrefix = "haip://"
            override val clientIdScheme = PreRegistered("AT-GV-EGIZ-CUSTOMVERIFIER", publicUrl, publicUrl)
            override val openIdVerifier = OpenId4VpVerifier(
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validator = potentialValidator()
                ),
                keyMaterial = EphemeralKeyWithoutCert(),
                clientIdScheme = clientIdScheme,
            )

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String) = ServletUriComponentsBuilder
                .fromUriString(urlPrefix).apply {
                    AuthenticationRequestParameters(
                        clientId = clientIdScheme.clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { queryParam(it.key, it.value) }
                }
                .toUriString()

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                )
            ).getOrThrow().serialize()
        },
        object : Profile {
            private val verifierKeyMaterial = KeyStoreMaterial(
                keyStore = KeyStore.getInstance("PKCS12").apply {
                    load(File("verifier.p12").inputStream(), "changeit".toCharArray())
                },
                keyAlias = "verifier",
                privateKeyPassword = "changeit".toCharArray(),
                certAlias = "verifier",
            )
            override val name = "EUDI"
            override val label = "HAIP (x509_san_dns)"
            override val urlPrefix = "haip://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    listOf(verifierKeyMaterial.getCertificate()!!),
                    publicUrl.getDnsName(),
                    publicUrl
                )
            }
            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme
            )

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String) = ServletUriComponentsBuilder
                .fromUriString(urlPrefix).apply {
                    AuthenticationRequestParameters(
                        clientId = clientIdScheme.clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { queryParam(it.key, it.value) }
                }
                .toUriString()

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                )
            ).getOrThrow().serialize()
        },
        object : Profile {
            private val verifierKeyMaterial = KeyStoreMaterial(
                keyStore = KeyStore.getInstance("PKCS12").apply {
                    load(File("verifier.p12").inputStream(),"changeit".toCharArray())
                },
                keyAlias = "verifier",
                privateKeyPassword = "changeit".toCharArray(),
                certAlias = "verifier",
            )
            override val name = "MDOC"
            override val label = "ISO 18013-7"
            override val urlPrefix = "mdoc-openid4vp://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    listOf(verifierKeyMaterial.getCertificate()!!),
                    publicUrl.getDnsName(),
                    publicUrl
                )
            }

            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme
            )

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String) = ServletUriComponentsBuilder
                .fromUriString(urlPrefix).apply {
                    AuthenticationRequestParameters(
                        clientId = clientIdScheme.clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { queryParam(it.key, it.value) }
                }
                .toUriString()

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    encryption = true
                )
            ).getOrThrow().serialize()
        }
    )

    fun potentialValidator(): Validator =
        Validator(verifierJwsService = DefaultVerifierJwsService(publicKeyLookup = { jwsSigned ->
            buildPotentialKeyLookup(jwsSigned)
        }))

    fun getVerifierByName(profileName: String): OpenId4VpVerifier? =
        knownProfiles.firstOrNull { it.name == profileName }?.openIdVerifier

    fun getJarMetadataByName(profileName: String): JwtVcIssuerMetadata? {
        val profile = (knownProfiles.firstOrNull { it.name == profileName }
            ?: knownProfiles.firstOrNull { it.name == "HAIP" })
        return profile?.openIdVerifier?.jarMetadata
    }
}

suspend fun Transaction.transactionGet(responseUrl: String): String {
    val state = Random.nextBytes(32).encodeToString(Base64())
    val requestOptionsCredentials = request.toRequestOptionsCredentials()
    return profile.transactionGet(responseUrl, state, requestOptionsCredentials)
}

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"

interface Profile {
    val name: String
    val label: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme
    val openIdVerifier: OpenId4VpVerifier
    fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String
    suspend fun transactionGet(
        responseUrl: String,
        state: String,
        requestOptionsCredentials: Set<RequestOptionsCredential>,
    ): String
}
