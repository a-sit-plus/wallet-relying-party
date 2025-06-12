package at.asit.apps.terminal_sp.prototype.server


import at.asit.apps.terminal_sp.prototype.server.ApiController.Transaction
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.StatusListToken
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.communication.primitives.StatusListTokenMediaType
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.OpenIdRequestOptions
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import com.benasher44.uuid.uuid4
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.security.KeyStore
import kotlin.time.Clock

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

    private suspend fun buildPotentialKeyLookup(jwsSigned: JwsSigned<*>): Set<JsonWebKey>? =
        (jwsSigned.payload as? JsonObject)?.get("iss")?.jsonPrimitive?.content?.let { iss ->
            val url = buildVcIssuerUrl(iss)
            Napier.i("Resolving Key for $iss from $url")
            httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet()
        }

    private fun buildVcIssuerUrl(iss: String): Url = URLBuilder(urlString = iss).apply {
        path(".well-known", "jwt-vc-issuer", *(pathSegments.toTypedArray()))
    }.build()

    val knownProfiles: List<Profile> = listOf(
        object : Profile {
            override val name = DEFAULT_PROFILE
            override val label = "Potential (v1)"
            override val description = "pre-registered client, OpenID4VP d18, direct_post"
            override val urlPrefix = "haip://"
            override val clientIdScheme = PreRegistered(
                clientId = "AT-GV-EGIZ-CUSTOMVERIFIER",
                redirectUri = publicUrl,
                issuerUri = publicUrl,
                useDeprecatedClientIdScheme = true,
            )
            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = EphemeralKeyWithoutCert(),
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validator = potentialValidator()
                ),
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
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    presentationMechanism = presentationMechanism,
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
            override val name = "Potentialv2"
            override val label = "Potential (v2)"
            override val description = "x509_san_dns, OpenID4VP d18, direct_post.jwt"
            override val urlPrefix = "haip://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    chain = listOf(verifierKeyMaterial.getCertificate()!!),
                    clientIdDnsName = publicUrl.getDnsName(),
                    redirectUri = publicUrl,
                    useDeprecatedClientIdScheme = true,
                )
            }
            val strippedClientId = clientIdScheme.clientId.removePrefix(clientIdScheme.scheme.prefix)
            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = strippedClientId,
                    validator = potentialValidator()
                ),
            )

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String) = ServletUriComponentsBuilder
                .fromUriString(urlPrefix).apply {
                    AuthenticationRequestParameters(
                        clientId = strippedClientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { queryParam(it.key, it.value) }
                }
                .toUriString()

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    presentationMechanism = presentationMechanism,
                    encryption = true,
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
            override val name = "HAIPd01"
            override val label = "HAIP (d01)"
            override val description = "x509_san_dns, OpenID4VP d23, direct_post"
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
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validator = validator()
                ),
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
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    presentationMechanism = presentationMechanism,
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
            override val name = "HAIPd03"
            override val label = "HAIP (d03)"
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = "haip://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    listOf(verifierKeyMaterial.getCertificate()!!),
                    publicUrl.getDnsName(),
                    publicUrl,
                )
            }
            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validator = validator()
                ),
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
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    encryption = true,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    presentationMechanism = presentationMechanism,
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
            override val name = "MDOC"
            override val label = "ISO 18013-7"
            override val description = "x509_san_dns, OpenID4VP d18, direct_post.jwt"
            override val urlPrefix = "mdoc-openid4vp://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    listOf(verifierKeyMaterial.getCertificate()!!),
                    publicUrl.getDnsName(),
                    publicUrl,
                    useDeprecatedClientIdScheme = true,
                )
            }
            val strippedClientId = clientIdScheme.clientId.removePrefix(clientIdScheme.scheme.prefix)

            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = strippedClientId,
                    validator = validator(),
                ),
            )

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String {
                return ServletUriComponentsBuilder
                    .fromUriString(urlPrefix).apply {
                        AuthenticationRequestParameters(
                            clientId = strippedClientId,
                            requestUri = requestUrl,
                        ).encodeToParameters()
                            .forEach { queryParam(it.key, it.value) }
                    }
                    .toUriString()
            }

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<RequestOptionsCredential>,
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    encryption = true,
                    presentationMechanism = presentationMechanism,
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
            override val name = "EUDIW"
            override val label = "EUDIW Ref."
            override val description = "x509_san_dns, OpenID4VP d23, direct_post.jwt"
            override val urlPrefix = "openid4vp://"
            override val clientIdScheme = runBlocking {
                ClientIdScheme.CertificateSanDns(
                    listOf(verifierKeyMaterial.getCertificate()!!),
                    publicUrl.getDnsName(),
                    publicUrl,
                )
            }
            override val openIdVerifier = OpenId4VpVerifier(
                keyMaterial = verifierKeyMaterial,
                clientIdScheme = clientIdScheme,
                verifier = VerifierAgent(
                    identifier = clientIdScheme.clientId,
                    validator = validator()
                ),
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
                presentationMechanism: PresentationMechanismEnum,
            ): String = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenIdRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    encryption = true,
                    presentationMechanism = presentationMechanism,
                )
            ).getOrThrow().serialize()
        }
    )

    fun validator(): Validator = Validator(
        resolveStatusListToken = resolveStatusListToken(),
        acceptedTokenStatuses = setOf(TokenStatus.Valid, TokenStatus.Invalid)
    )

    fun potentialValidator(): Validator = Validator(
        verifyJwsObject = VerifyJwsObject(
            publicKeyLookup = { jwsSigned ->
                buildPotentialKeyLookup(jwsSigned)
            }
        ),
        resolveStatusListToken = resolveStatusListToken(),
        acceptedTokenStatuses = setOf(TokenStatus.Valid, TokenStatus.Invalid)
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
            StatusListToken.StatusListJwt(it, kotlinx.datetime.Clock.System.now())
        }
    }

    fun getVerifierByName(profileName: String): OpenId4VpVerifier? =
        knownProfiles.firstOrNull { it.name == profileName }?.openIdVerifier

    fun getJarMetadataByName(profileName: String): JwtVcIssuerMetadata? {
        val profile = (knownProfiles.firstOrNull { it.name == profileName }
            ?: knownProfiles.firstOrNull { it.name == DEFAULT_PROFILE })
        return profile?.openIdVerifier?.jarMetadata
    }
}

suspend fun Transaction.transactionGet(
    responseUrl: String,
): String {
    val requestOptionsCredentials = request.toRequestOptionsCredentials()
    return profile.transactionGet(
        responseUrl,
        uuid4().toString(),
        if (request.presentationMechanism == PresentationMechanismEnum.DCQL) {
            requestOptionsCredentials.map {
                // TODO: unsure how we handle optional attributes with DCQL
                it.copy(
                    requestedAttributes = it.requestedOptionalAttributes,
                    requestedOptionalAttributes = null,
                )
            }.toSet()
        } else requestOptionsCredentials,
        presentationMechanism = request.presentationMechanism,
    )
}

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"

interface Profile {
    val name: String
    val label: String
    val description: String
    val urlPrefix: String
    val clientIdScheme: ClientIdScheme
    val openIdVerifier: OpenId4VpVerifier
    fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String
    suspend fun transactionGet(
        responseUrl: String,
        state: String,
        requestOptionsCredentials: Set<RequestOptionsCredential>,
        presentationMechanism: PresentationMechanismEnum,
    ): String
}


const val DEFAULT_PROFILE = "Potentialv1"
