package at.asit.wallet.relyingparty

import at.asitplus.etsi.TrustListPayload
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.leaf
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.etsi.LoTEFilterCriteria
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.isTrustedBy
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifiedPresentationResult
import at.asitplus.wallet.lib.jws.VerifyJwsObjectJades
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResult
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.springframework.stereotype.Service
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.springframework.scheduling.annotation.Scheduled

@Service
class TrustListService(
    private val trustListCache: TrustListCache
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(joseCompliantSerializer)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.i(message = message, tag = "at.asitplus.http.trust")
                }
            }
            level = LogLevel.INFO
        }
    }

    private val asitRootPem = """
        -----BEGIN CERTIFICATE-----
        MIICNzCCAd6gAwIBAgIUVKbs5o5e1jnILQPrKrsBnZbJj5EwCgYIKoZIzj0EAwIw
        MTELMAkGA1UEBhMCQVQxDjAMBgNVBAoMBUEtU0lUMRIwEAYDVQQDDAlJQUNBIDIw
        MjYwHhcNMjYwNDE2MTQ1NDQ1WhcNMjcwNDE2MTQ1NDQ1WjAxMQswCQYDVQQGEwJB
        VDEOMAwGA1UECgwFQS1TSVQxEjAQBgNVBAMMCUlBQ0EgMjAyNjBZMBMGByqGSM49
        AgEGCCqGSM49AwEHA0IABA7215fpBuEqE0AmnwgUoKMGCIZjnXMPZohMJKKrO0f/
        84eg4bFLVUAM25Clukqbjr/Ol3Pa16LLhxQoSIupJx+jgdMwgdAwEgYDVR0TAQH/
        BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwMQYDVR0fBCowKDAmoCSgIoYgaHR0
        cDovL3dhbGxldC5hLXNpdC5hdC9jcmwvMS5jcmwwIgYDVR0SBBswGYYXaHR0cHM6
        Ly93YWxsZXQuYS1zaXQuYXQwEwYDVR0gBAwwCjAIBgYEAI96AQEwHwYDVR0jBBgw
        FoAUTXNbbT6FjuThGuNsHM5KMNSead4wHQYDVR0OBBYEFE1zW20+hY7k4RrjbBzO
        SjDUnmneMAoGCCqGSM49BAMCA0cAMEQCIDMQ328z1NWGUK6wcLC8JmgTkKxt3Ycw
        BapSKA9Qxhd6AiANUlRcM5BT5JKZL3yNSvUlERYXqcEYs50sxwE60SVkEw==
        -----END CERTIFICATE-----
    """.trimIndent()

    private val asitIssuerCert = X509Certificate.decodeFromPem(asitRootPem).getOrThrow()
    private val loTeFilterService = LoTEFilterService()

    private val defaultUrls = listOf(
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/pid-providers.json",
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wallet-providers.json",
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wrpac-providers.json",
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/mdl-providers.json",
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/pub-eaa-providers.json"
    )

    @PostConstruct
    fun initCacheBootstrap() = runBlocking {
        Napier.i("Initializing baseline Trust List sync...")
        refreshAll()
    }

    /**
     * Background routine executing once every hour.
     */
    @Scheduled(fixedRate = 3600000)
    fun scheduledRefresh() = runBlocking {
        refreshAll()
    }

    suspend fun refreshAll() {
        defaultUrls.forEach { url ->
            try {
                syncSingleUrl(url)
            } catch (e: Exception) {
                Napier.e("Background sync failed for Trust List URL: $url", e)
            }
        }
    }

    private suspend fun syncSingleUrl(url: String) {
        Napier.i("Fetching Trust List from: $url")
        val jws = httpClient.get(url) {
            accept(ContentType.Application.Json)
        }.body<String>().let {
            JwsCompact.parse<TrustListPayload>(it).getOrThrow()
        }
        VerifyJwsObjectJades().invoke(jws.first).getOrThrow()
        trustListCache.updatePayload(url, jws.second)
        Napier.i("Successfully verified and cached Trust List for URL: $url")
    }

    fun evaluateIssuer(
        issuer: X509Certificate,
        serviceType: String
    ): TrustState = try {
        if (issuer.isTrustedBy(listOf(asitIssuerCert)).isSuccess) {
            return TrustState.TRUSTED
        }

        val criteria = LoTEFilterCriteria(expectedServiceType = serviceType)
        val allLoTes = trustListCache.getAllPayloads().map { it.loTe }

        val certificateList: List<X509Certificate> = allLoTes
            .flatMap { lote -> loTeFilterService.extractTrustedCertificates(lote, criteria) }
            .mapNotNull { it.certificate }

        if (certificateList.isEmpty()) {
            TrustState.UNTRUSTED
        } else if (issuer.isTrustedBy(certificateList).isSuccess) {
            TrustState.TRUSTED
        } else {
            TrustState.UNTRUSTED
        }
    } catch (e: Exception) {
        Napier.e("Failed to evaluate issuer trust status due to unexpected error", e)
        TrustState.UNKNOWN
    }

    fun evaluateTransactionTrust(
        leafCertificate: X509Certificate?,
        transaction: ApiController.Transaction
    ): TrustState {
        if (leafCertificate == null) return TrustState.UNKNOWN

        val serviceType = transaction.presentationExchangeRequest?.presentationDefinition?.inputDescriptors?.firstOrNull()?.id
            ?: transaction.dcqlRequest?.dcqlQuery?.credentials?.firstOrNull()?.id?.string
            ?: "unknown_service"

        Napier.i("TYPEEEEEEEEEEEEE:   $serviceType \n\n\n")
        Napier.i("TYPEEEEEEEEEEEEE:   $transaction \n\n\n")


        return evaluateIssuer(leafCertificate, serviceType)
    }

}

@Serializable
enum class TrustState {
    TRUSTED, UNTRUSTED, UNKNOWN
}


fun AuthnResponseResult.extractIssuerCertificate(): X509Certificate? {
    val vpResult = this.vpTokenValidationResult?.getOrNull() ?: return null

    return vpResult.presentationResults.firstNotNullOfOrNull { presentationResultKmm ->
        val successResult = presentationResultKmm.getOrNull() ?: return@firstNotNullOfOrNull null

        when (successResult) {
            is Verifier.VerifyPresentationResult.SuccessSdJwt -> {
                successResult.sdJwtSigned.jws.jwsHeader.certificateChain?.leaf
            }

            is Verifier.VerifyPresentationResult.Success -> {
                successResult.vp.jws.jws.jwsHeader.certificateChain?.leaf
            }

            is Verifier.VerifyPresentationResult.SuccessUnsigned -> {
                null
            }

            is Verifier.VerifyPresentationResult.SuccessIso -> {
                val issuerAuth = successResult.documents.firstOrNull()?.document?.issuerSigned?.issuerAuth
                val certBytes = issuerAuth?.unprotectedHeader?.certificateChain?.firstOrNull()
                    ?: issuerAuth?.protectedHeader?.certificateChain?.firstOrNull()

                certBytes?.let { X509Certificate.decodeFromDer(it) }
            }
        }
    }
}

fun Iso180137AnnexCVerifiedPresentationResult.extractIssuerCertificate(): X509Certificate? =
    documents.firstOrNull()?.document?.issuerSigned?.issuerAuth?.let { auth ->
        (auth.unprotectedHeader?.certificateChain?.firstOrNull()
            ?: auth.protectedHeader.certificateChain?.firstOrNull())
            ?.let { X509Certificate.decodeFromDer(it) }
    }
