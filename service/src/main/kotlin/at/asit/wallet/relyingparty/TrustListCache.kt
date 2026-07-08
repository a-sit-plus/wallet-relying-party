package at.asit.wallet.relyingparty

import at.asitplus.etsi.TrustListPayload
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TrustListCache {
    private val cache = ConcurrentHashMap<String, TrustListPayload>()

    fun getPayload(url: String): TrustListPayload? = cache[url]

    fun updatePayload(url: String, payload: TrustListPayload) {
        cache[url] = payload
    }

    fun getAllPayloads(): List<TrustListPayload> = cache.values.toList()

    fun getTrackedUrls(): Set<String> = cache.keys
}