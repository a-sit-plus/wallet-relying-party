package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.ApiController.Transaction
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.random.Random

class VerifierProfiles(private val publicUrl: String) {

    val knownProfiles: List<Profile> = listOf(
        "AT-GV-EGIZ-CUSTOMVERIFIER".let { clientId ->
            Profile(
                name = "HAIP",
                label = "HAIP (Potential)",
                urlPrefix = "haip://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId)
                ),
                clientMetadataUrl = URLBuilder(publicUrl).apply {
                    appendPathSegments("siopv2", "metadata", "HAIP")
                }.buildString(),
                buildQrCodeUrl = { requestUrl, urlPrefix ->
                    with(URLBuilder(urlPrefix)) {
                        AuthenticationRequestParameters(
                            clientId = clientId,
                            requestUri = requestUrl,
                        ).encodeToParameters()
                            .forEach { parameters.append(it.key, it.value) }
                        buildString()
                    }
                }
            )
        },
        publicUrl.let { clientId ->
            Profile(
                name = "EUDI",
                label = "EUDI",
                urlPrefix = "eudi-openid4vp://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId)
                ),
                clientMetadataUrl = URLBuilder(publicUrl).apply {
                    appendPathSegments("siopv2", "metadata", "EUDI")
                }.buildString(),
                buildQrCodeUrl = { requestUrl, urlPrefix ->
                    with(URLBuilder(urlPrefix)) {
                        AuthenticationRequestParameters(
                            clientId = clientId,
                            requestUri = requestUrl,
                        ).encodeToParameters()
                            .forEach { parameters.append(it.key, it.value) }
                        buildString()
                    }
                }
            )
        },
        publicUrl.let { clientId ->
            Profile(
                name = "MDOC",
                label = "ISO 18013-7",
                urlPrefix = "mdoc-openid4vp://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId),
                ),
                clientMetadataUrl = URLBuilder(publicUrl).apply {
                    appendPathSegments("siopv2", "metadata", "MDOC")
                }.buildString(),
                buildQrCodeUrl = { requestUrl, urlPrefix ->
                    with(URLBuilder(urlPrefix)) {
                        AuthenticationRequestParameters(
                            clientId = clientId,
                            requestUri = requestUrl,
                        ).encodeToParameters()
                            .forEach { parameters.append(it.key, it.value) }
                        buildString()
                    }
                }
            )
        }
    )

    fun getVerifierByName(profileName: String): OidcSiopVerifier? =
        knownProfiles.firstOrNull { it.name == profileName }?.verifier
}


suspend fun Transaction.transactionGet(responseUrl: String): String {
    val state = createSafeState()
    val requestOptionsCredentials = request.toRequestOptionsCredentials()
    val requestOptions = OidcSiopVerifier.RequestOptions(
        state = state,
        responseMode = OpenIdConstants.ResponseMode.DirectPost,
        responseUrl = responseUrl,
        credentials = requestOptionsCredentials, // TODO Attributes optional!
    )
    // TODO may not always be a requestObjectJws!
    val requestObjectJws = profile.verifier.createAuthnRequestAsSignedRequestObject(requestOptions).getOrElse {
        Napier.w("transactionGet($id) error", it)
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
    }
    return requestObjectJws.serialize()
}


private fun createSafeState() = Random.nextBytes(32).encodeToString(Base64())


data class Profile(
    val name: String,
    val label: String,
    val urlPrefix: String,
    val clientId: String,
    val verifier: OidcSiopVerifier,
    val clientMetadataUrl: String,
    val buildQrCodeUrl: (requestUrl: String, urlPrefix: String) -> String,
)