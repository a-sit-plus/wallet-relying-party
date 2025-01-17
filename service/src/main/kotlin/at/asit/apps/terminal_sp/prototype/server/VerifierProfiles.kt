package at.asit.apps.terminal_sp.prototype.server


import at.asit.apps.terminal_sp.prototype.server.ApiController.Transaction
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.coroutines.runBlocking
import org.springframework.web.util.UriComponentsBuilder
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
            private val extensions = listOf(
                X509CertificateExtension(
                    KnownOIDs.subjectAltName_2_5_29_17,
                critical = false,
                Asn1EncapsulatingOctetString(
                    listOf(
                    Asn1.Sequence {
                        +Asn1Primitive(
                            SubjectAltNameImplicitTags.dNSName,
                            Asn1String.UTF8(publicUrl.getDnsName()).encodeToTlv().content
                        )
                    }
                ))))
            private val verifierKeyMaterial = EphemeralKeyWithSelfSignedCert(extensions = extensions)
            override val name = "MDOC"
            override val label = "ISO 18013-7"
            override val urlPrefix = "mdoc-openid4vp://"
            override val clientId = publicUrl
            override val verifier = runBlocking {
                OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = verifierKeyMaterial,
                    clientIdScheme = OidcSiopVerifier.ClientIdScheme.CertificateSanDns(
                        listOf(verifierKeyMaterial.getCertificate()!!),
                        publicUrl.getDnsName()
                    )
                )
            }
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

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"

interface Profile {
    val name: String
    val label: String
    val urlPrefix: String
    val clientId: String
    val verifier: OidcSiopVerifier
    fun buildQrCodeUrl(requestUrl: String, urlPrefix: String): String
    suspend fun transactionGet(
        responseUrl: String,
        state: String,
        requestOptionsCredentials: Set<OidcSiopVerifier.RequestOptionsCredential>,
    ): String
}