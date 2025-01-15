package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.ApiController.Transaction
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.random.Random

class VerifierProfiles(private val publicUrl: String) {

    val knownProfiles: List<Profile> = listOf(
        object : Profile {
            override val name = "HAIP"
            override val label = "HAIP (Potential)"
            override val urlPrefix = "haip://"
            override val clientId = "AT-GV-EGIZ-CUSTOMVERIFIER"
            override val verifier = OidcSiopVerifier(
                verifier = VerifierAgent(clientId),
                keyMaterial = EphemeralKeyWithoutCert(),
                clientIdScheme = PreRegistered(clientId)
            )
            override val clientMetadataUrl = URLBuilder(publicUrl).apply {
                appendPathSegments("siopv2", "metadata", "HAIP")
            }.buildString()

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String =
                with(URLBuilder(urlPrefix)) {
                    AuthenticationRequestParameters(
                        clientId = clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { parameters.append(it.key, it.value) }
                    buildString()
                }

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<OidcSiopVerifier.RequestOptionsCredential>,
            ): String = verifier.createAuthnRequestAsSignedRequestObject(
                OidcSiopVerifier.RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                )
            ).getOrThrow().serialize()
        },
        object : Profile {
            override val name = "EUDI"
            override val label = "EUDI"
            override val urlPrefix = "eudi-openid4vp://"
            override val clientId = publicUrl
            override val verifier = OidcSiopVerifier(
                verifier = VerifierAgent(clientId),
                keyMaterial = EphemeralKeyWithoutCert(),
                clientIdScheme = PreRegistered(clientId)
            )
            override val clientMetadataUrl = URLBuilder(publicUrl).apply {
                appendPathSegments("siopv2", "metadata", "EUDI")
            }.buildString()

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String =
                with(URLBuilder(urlPrefix)) {
                    AuthenticationRequestParameters(
                        clientId = clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { parameters.append(it.key, it.value) }
                    buildString()
                }

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<OidcSiopVerifier.RequestOptionsCredential>,
            ): String = verifier.createAuthnRequestAsSignedRequestObject(
                OidcSiopVerifier.RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                )
            ).getOrThrow().serialize()
        },
        object : Profile {
            override val name = "MDOC"
            override val label = "ISO 18013-7"
            override val urlPrefix = "mdoc-openid4vp://"
            override val clientId = publicUrl
            override val verifier = OidcSiopVerifier(
                verifier = VerifierAgent(clientId),
                keyMaterial = EphemeralKeyWithoutCert(),
                clientIdScheme = PreRegistered(clientId),
            )
            override val clientMetadataUrl = URLBuilder(publicUrl).apply {
                appendPathSegments("siopv2", "metadata", "MDOC")
            }.buildString()

            override fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String =
                with(URLBuilder(urlPrefix)) {
                    AuthenticationRequestParameters(
                        clientId = clientId,
                        requestUri = requestUrl,
                    ).encodeToParameters()
                        .forEach { parameters.append(it.key, it.value) }
                    buildString()
                }

            override suspend fun transactionGet(
                responseUrl: String,
                state: String,
                requestOptionsCredentials: Set<OidcSiopVerifier.RequestOptionsCredential>,
            ): String = verifier.createAuthnRequestAsSignedRequestObject(
                OidcSiopVerifier.RequestOptions(
                    state = state,
                    // TODO Also consider this on verifying the result?
                    responseMode = OpenIdConstants.ResponseMode.DirectPostJwt,
                    responseUrl = responseUrl,
                    credentials = requestOptionsCredentials,
                    encryption = true
                )
            ).getOrThrow().serialize()
        }
    )

    fun getVerifierByName(profileName: String): OidcSiopVerifier? =
        knownProfiles.firstOrNull { it.name == profileName }?.verifier
}


suspend fun Transaction.transactionGet(responseUrl: String): String {
    val state = Random.nextBytes(32).encodeToString(Base64())
    val requestOptionsCredentials = request.toRequestOptionsCredentials()
    return profile.transactionGet(responseUrl, state, requestOptionsCredentials)
}

interface Profile {
    val name: String
    val label: String
    val urlPrefix: String
    val clientId: String
    val verifier: OidcSiopVerifier
    val clientMetadataUrl: String
    fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String
    suspend fun transactionGet(
        responseUrl: String,
        state: String,
        requestOptionsCredentials: Set<OidcSiopVerifier.RequestOptionsCredential>,
    ): String
}