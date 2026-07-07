package at.asit.wallet.relyingparty

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Serves the SD-JWT Type Metadata documents from the snapshot in `src/test/resources/credentials-collection/` instead
 * of fetching them from GitHub, keeping tests deterministic and offline. Production resolves live (see
 * [RelyingPartyConfiguration.metadataHttpClient]).
 *
 * Deliberately a plain [Configuration] (not `@TestConfiguration`): test classes in this package are on the classpath
 * of the main application's component scan when tests run, so every Spring test picks this up without an explicit
 * `@Import`.
 *
 * Refresh the snapshot by re-downloading the documents listed in [CredentialCatalog] from [CredentialCatalog.BASE_URL].
 */
@Configuration
class CachedTypeMetadataConfiguration {

    @Bean
    @Primary
    fun cachedTypeMetadataHttpClient(): HttpClient = HttpClient(MockEngine { request ->
        val fileName = request.url.segments.last()
        javaClass.getResourceAsStream("/credentials-collection/$fileName")?.use {
            respond(it.readBytes(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        } ?: respondError(HttpStatusCode.NotFound, "No cached type metadata document: $fileName")
    })
}
