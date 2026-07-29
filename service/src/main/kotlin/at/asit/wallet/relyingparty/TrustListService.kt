package at.asit.wallet.relyingparty

import at.asitplus.etsi.TrustListPayload
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.etsi.LoTEFilterCriteria
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.LoTEServiceType
import at.asitplus.wallet.lib.etsi.isTrustedBy
import at.asitplus.wallet.lib.jws.VerifyJwsObjectJades
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.springframework.stereotype.Service
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.accept
import io.ktor.client.request.get
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

    private val asitIssuerCert = X509Certificate.decodeFromPem(
        checkNotNull(javaClass.getResource("/asit-root.pem")) { "Missing ASIT root certificate" }.readText()
    ).getOrThrow()
    private val loTeFilterService = LoTEFilterService()

    @PostConstruct
    fun initCacheBootstrap() = runBlocking {
        try {
            Napier.i("Initializing baseline Trust List sync...")
            refreshAll()
        } catch (e: Exception) {
            Napier.e("Failed to initialize baseline Trust List sync during startup. Continuing with empty/stale cache.", e)
        }
    }

    /**
     * Background routine executing once every hour.
     */
    @Scheduled(fixedRate = 3600000)
    fun scheduledRefresh() = runBlocking {
        refreshAll()
    }

    suspend fun refreshAll() {
        LoTEServiceType.defaultUrls.forEach { url ->
            syncSingleUrl(url)
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

    /**
     * Evaluates if a given issuer is trusted based on the internal root cert and LoTEs.
     */
    fun evaluateIssuer(
        issuer: X509Certificate,
        serviceType: LoTEServiceType
    ): TrustState = try {
        if (issuer.isTrustedBy(listOf(asitIssuerCert)).isSuccess) {
            return TrustState.TRUSTED
        }

        val criteria = LoTEFilterCriteria(expectedServiceType = serviceType)
        val allLoTes = trustListCache.getAll()

        val certificateList: List<X509Certificate> = allLoTes
            .flatMap { lote -> loTeFilterService.extractTrustedCertificates(lote.key, lote.value.loTe, criteria) }
            .mapNotNull { it.certificate }

        if (certificateList.isEmpty()) {
            return TrustState.UNTRUSTED
        }

        val validationResult = issuer.isTrustedBy(certificateList)
        if (validationResult.isSuccess) TrustState.TRUSTED else TrustState.UNTRUSTED
    } catch (e: Exception) {
        Napier.e("Failed to evaluate issuer trust status due to unexpected error", e)
        TrustState.UNKNOWN
    }

    fun evaluateCredentialIssuerTrust(credential: ApiItemCredential): TrustState =
        credential.issuerCertificate?.let {
            evaluateIssuer(it, LoTEServiceType.fromSchemeIdentifier(credential.credentialType))
        } ?: TrustState.UNKNOWN
}

@Serializable
enum class TrustState {
    TRUSTED, UNTRUSTED, UNKNOWN
}
