package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.StatusListToken
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Fetches the status list or identifier list a credential references, as JWT or CWT.
 *
 * Servers do not always label the response with the media types of the Token Status List resp. ISO/IEC 18013-5,
 * so for any other content type a CWT is detected from the body, falling back to a JWT. A token that cannot be parsed
 * is reported with the content type it has been served with.
 *
 * A fetched token is reused for later presentations until its `ttl` elapses (draft-ietf-oauth-status-list-21, 8.3
 * step 4.d) or it expires, but for at most [maxCacheAge], so that verifying a presentation needs no network access for
 * a recently used list. It keeps the time it has been resolved at, from which the validation still checks its `ttl`.
 * Concurrent requests for the same list share one fetch; a failed fetch is not cached.
 */
class StatusListTokenFetcher(
    private val httpClient: HttpClient,
    private val clock: Clock = Clock.System,
    private val maxCacheAge: Duration = 5.minutes,
) : StatusListTokenResolver {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedToken>()
    private val inFlight = mutableMapOf<String, Deferred<StatusListToken>>()

    /** Runs fetches independently of the presentation that started them, which other presentations may await. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun invoke(statusListUrl: UniformResourceIdentifier): StatusListToken {
        val key = statusListUrl.string
        val fetch = mutex.withLock {
            val now = clock.now()
            cache[key]?.takeIf { now < it.validUntil }?.let { return it.token }
            inFlight.getOrPut(key) {
                scope.async {
                    try {
                        fetch(statusListUrl).also { token ->
                            mutex.withLock {
                                cache.values.removeAll { clock.now() >= it.validUntil }
                                cache[key] = CachedToken(token, token.cacheableUntil())
                            }
                        }
                    } finally {
                        mutex.withLock { inFlight.remove(key) }
                    }
                }
            }
        }
        return fetch.await()
    }

    /** Until the `ttl` of this token elapses or it expires, at most [maxCacheAge] after it has been resolved. */
    private fun StatusListToken.cacheableUntil(): Instant {
        val resolved = resolvedAt ?: clock.now()
        val payload = parsedPayload.getOrNull()
        return listOfNotNull(
            resolved + maxCacheAge,
            payload?.timeToLive?.let { resolved + it.duration },
            payload?.expirationTime,
        ).min()
    }

    private suspend fun fetch(statusListUrl: UniformResourceIdentifier): StatusListToken {
        Napier.i("Resolving token status from $statusListUrl")
        val response = httpClient.get(statusListUrl.string) {
            header(HttpHeaders.Accept, ACCEPTED_MEDIA_TYPES.joinToString(", "))
        }
        check(response.status.isSuccess()) { "Status list $statusListUrl responded with ${response.status}" }
        val body = response.body<ByteArray>()
        val contentType = response.contentType()?.withoutParameters()?.toString()?.lowercase()
        return runCatching {
            when (contentType) {
                MediaTypes.Application.STATUSLIST_JWT -> body.toJwt()
                MediaTypes.Application.STATUSLIST_CWT,
                MediaTypes.Application.IDENTIFIERLIST_CWT -> body.toCwt()

                else -> runCatching { body.toCwt() }.getOrNull() ?: body.toJwt()
            }
        }.getOrElse {
            throw IllegalStateException(
                "Status list $statusListUrl served as ${contentType ?: "unknown content type"} is invalid: ${it.message}",
                it,
            )
        }
    }

    private fun ByteArray.toJwt() = StatusListJwt(
        JwsCompactTyped<StatusListTokenPayload>(decodeToString().trim()),
        clock.now(),
    )

    /** A CWT may be tagged as CWT (RFC 8392, tag 61) and/or as COSE_Sign1 (RFC 9052, tag 18). */
    private fun ByteArray.toCwt() = StatusListCwt(
        coseCompliantSerializer.decodeFromByteArray<CoseSigned<ByteArray>>(
            withoutPrefix(CWT_TAG).withoutPrefix(COSE_SIGN1_TAG)
        ),
        clock.now(),
    )

    private fun ByteArray.withoutPrefix(prefix: ByteArray): ByteArray =
        if (size > prefix.size && prefix.indices.all { this[it] == prefix[it] }) copyOfRange(prefix.size, size) else this

    private class CachedToken(val token: StatusListToken, val validUntil: Instant)

    private companion object {
        val ACCEPTED_MEDIA_TYPES = listOf(
            MediaTypes.Application.STATUSLIST_JWT,
            MediaTypes.Application.STATUSLIST_CWT,
            MediaTypes.Application.IDENTIFIERLIST_CWT,
        )
        val CWT_TAG = byteArrayOf(0xD8.toByte(), 0x3D)
        val COSE_SIGN1_TAG = byteArrayOf(0xD2.toByte())
    }
}
