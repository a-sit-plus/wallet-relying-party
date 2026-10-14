package at.asit.wallet.relyingparty

import at.asitplus.openid.truncateToSeconds
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.basicConstraints_2_5_29_19
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import at.asitplus.signum.supreme.asKmmResult
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.InMemoryIssuerCredentialStore
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.agent.TrustedCertificates
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.validation.CredentialTrustAnchors
import at.asitplus.wallet.lib.validation.CredentialTrustScope
import at.asitplus.wallet.lib.validation.CredentialTrustScopes
import com.benasher44.uuid.uuid4
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.encodeToByteArray
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Trusts [TestTrust.issuerKey] to issue [AtomicAttribute2023] credentials, and serves the status lists of
 * [TestTrust.issuer] without network access.
 *
 * A plain [Configuration], like [CachedTypeMetadataConfiguration], so every Spring test picks it up.
 */
@Configuration
class TestCredentialValidationConfiguration {

    @Bean
    @Primary
    fun testCredentialTrustAnchors(): CredentialTrustAnchors = CredentialTrustScopes(
        CredentialTrustScope(
            credentialIdentifiers = setOf(
                AtomicAttribute2023.sdJwtType,
                AtomicAttribute2023.isoDocType,
                AtomicAttribute2023.vcType,
            ),
            source = TestTrust.SOURCE,
            issuance = TrustedCertificates { setOf(TestTrust.anchor()) },
            status = TrustedCertificates { setOf(TestTrust.anchor()) },
        )
    )

    @Bean
    @Primary
    fun testStatusListTokenResolver(): StatusListTokenResolver = StatusListTokenFetcher(
        HttpClient(MockEngine { request ->
            val url = request.url.toString()
            val agent = TestTrust.statusListAgent(url)
                ?: return@MockEngine respondError(HttpStatusCode.NotFound, "No status list at $url")
            val kind = if (url.contains("/identifier/")) RevocationList.Kind.IDENTIFIER_LIST
            else RevocationList.Kind.STATUS_LIST
            respond(
                content = coseCompliantSerializer.encodeToByteArray(
                    agent.issueStatusListCwt(request.url.segments.last().toInt(), kind)
                ),
                headers = headersOf(
                    HttpHeaders.ContentType,
                    if (kind == RevocationList.Kind.STATUS_LIST) MediaTypes.Application.STATUSLIST_CWT
                    else MediaTypes.Application.IDENTIFIERLIST_CWT,
                ),
            )
        })
    )
}

object TestTrust {
    const val SOURCE = "Test issuer"

    /**
     * Trusted to issue credentials of [AtomicAttribute2023], directly as a listed certificate, and to certify the
     * signers of their status lists, as a certificate authority.
     */
    val issuerKey = certificateAuthorityKey()

    /** A key to issue credentials and certify status list signers, like [issuerKey], but trusted by no one. */
    fun certificateAuthorityKey() = EphemeralKeyWithSelfSignedCert(
        extensions = listOf(basicConstraintsCa),
        lifetimeInSeconds = 1.hours.inWholeSeconds,
    )

    private val statusListAgents = ConcurrentHashMap<String, StatusListAgent>()

    suspend fun anchor(): X509Certificate = checkNotNull(issuerKey.getCertificate())

    /**
     * An issuer signing with [key], whose credentials reference status lists of their own, signed by a key that [key]
     * certified, as status list signers have to be issued by an anchor (OpenID4VC HAIP 1.0, 6.1). Revoking one of its
     * credentials does not affect other tests.
     */
    suspend fun issuer(key: EphemeralKeyWithSelfSignedCert = issuerKey): Pair<IssuerAgent, StatusListAgent> {
        val baseUrl = "https://status.example.com/${uuid4()}"
        val store = InMemoryIssuerCredentialStore()
        val statusListAgent = StatusListAgent(
            keyMaterial = certifiedBy(key),
            issuerCredentialStore = store,
            statusListBaseUrl = "$baseUrl/status",
            identifierListBaseUrl = "$baseUrl/identifier",
        )
        statusListAgents[baseUrl] = statusListAgent
        return IssuerAgent(
            keyMaterial = key,
            issuerCredentialStore = store,
            identifier = UniformResourceIdentifier("https://example.com"),
            statusListAgent = statusListAgent,
        ) to statusListAgent
    }

    fun statusListAgent(url: String): StatusListAgent? =
        statusListAgents.entries.firstOrNull { url.startsWith("${it.key}/") }?.value

    /**
     * A self-signed certificate authority of [organization], e.g. to publish as a service of [organization] on a list
     * of trusted entities, which only lists certificates of the entity's organization.
     */
    suspend fun certificateAuthority(organization: String): KeyMaterial {
        val key = EphemeralKeyWithoutCert()
        val name = listOf(
            RelativeDistinguishedName(AttributeTypeAndValue.Organization(Asn1String.UTF8(organization))),
            RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("$organization CA"))),
        )
        return key.withCertificate(
            certificate(subjectName = name, issuerName = name, publicKey = key.publicKey, issuer = key, ca = true)
        )
    }

    /** A key with a certificate issued by [issuer], which has to be a certificate authority. */
    suspend fun certifiedBy(issuer: KeyMaterial): KeyMaterial {
        val key = EphemeralKeyWithoutCert()
        return key.withCertificate(
            certificate(
                subjectName = listOf(
                    RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("Status list signer")))
                ),
                issuerName = checkNotNull(issuer.getCertificate()).tbsCertificate.subjectName,
                publicKey = key.publicKey,
                issuer = issuer,
                ca = false,
            )
        )
    }

    private suspend fun certificate(
        subjectName: List<RelativeDistinguishedName>,
        issuerName: List<RelativeDistinguishedName>,
        publicKey: CryptoPublicKey,
        issuer: KeyMaterial,
        ca: Boolean,
    ): X509Certificate {
        val algorithm = issuer.signatureAlgorithm.toX509SignatureAlgorithm().getOrThrow()
        val notBefore = (Clock.System.now() - 1.hours).truncateToSeconds()
        val tbsCertificate = TbsCertificate(
            version = 2,
            serialNumber = Random.nextBytes(8),
            issuerName = issuerName,
            subjectName = subjectName,
            validFrom = Asn1Time(notBefore),
            validUntil = Asn1Time(notBefore + 2.hours),
            signatureAlgorithm = algorithm,
            publicKey = publicKey,
            extensions = if (ca) listOf(basicConstraintsCa) else listOf(),
        )
        val signature = issuer.sign(tbsCertificate.encodeToDer()).asKmmResult().getOrThrow()
        return X509Certificate(tbsCertificate, algorithm, signature)
    }

    private fun EphemeralKeyWithoutCert.withCertificate(certificate: X509Certificate): KeyMaterial =
        object : KeyMaterial by this {
            override suspend fun getCertificate() = certificate
        }

    // A getter, as [issuerKey] is initialized before the properties declared after it
    private val basicConstraintsCa get() = X509CertificateExtension(
        oid = KnownOIDs.basicConstraints_2_5_29_19,
        critical = true,
        value = Asn1EncapsulatingOctetString(listOf(Asn1.Sequence { +Asn1.Bool(true) })),
    )
}
