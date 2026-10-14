package at.asit.wallet.relyingparty

import at.asitplus.etsi.TrustListPayload
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.etsi.LoteProfile
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled

/**
 * Fetches the Lists of Trusted Entities of the configured stages, verifies their signatures, and caches them in
 * [TrustListCache], from where [credentialTrustScopes] reads them.
 */
@Service
class TrustListService(
    private val trustListCache: TrustListCache,
    configuration: AppConfigurationProperties,
) {
    /** Every list of every configured trust infrastructure stage. */
    val trustListUrls: List<String> = LoteProfile.fetchUrls(configuration.trustListStages)

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

    /**
     * Fetches every list on its own: a list that can not be fetched or verified, e.g. one a stage does not publish,
     * keeps its cached version, if any, and does not keep the other lists from being refreshed.
     */
    suspend fun refreshAll(sync: suspend (String) -> Unit = ::syncSingleUrl) {
        trustListUrls.forEach { url ->
            try {
                sync(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Failed to refresh Trust List from $url, keeping the cached one, if any", e)
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
}
