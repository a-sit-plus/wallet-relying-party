package at.asit.wallet.relyingparty

import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class TransactionStore(
    configuration: AppConfigurationProperties,
) {
    private val lifetime = configuration.resultTtl
    private val lock = ReentrantLock()
    private val entries: MutableList<ResultEntry> = mutableListOf()

    fun getApiItems(): List<ApiItem> = lock.withLock {
        removeExpiredEntries()
        entries.map { it.apiItem }
    }

    fun getApiItem(id: String): ApiItem? = lock.withLock {
        removeExpiredEntries()
        entries.firstOrNull { it.id == id }?.apiItem
    }

    fun removeApiItem(id: String): ApiItem? = lock.withLock {
        removeExpiredEntries()
        val entry = entries.firstOrNull { it.id == id }
        if (entry != null) {
            entries.remove(entry)
        }
        return entry?.apiItem
    }

    fun put(id: String, user: User): Boolean? = lock.withLock {
        removeExpiredEntries()
        user.toApiItem()?.let { entries.add(ResultEntry(id, it, Instant.now().plus(lifetime))) }
    }

    @Scheduled(fixedDelay = 60_000)
    fun removeExpiredEntriesScheduled() = lock.withLock {
        removeExpiredEntries()
    }

    private fun removeExpiredEntries() {
        val now = Instant.now()
        entries.removeAll { it.notAfter < now }
    }

}

private data class ResultEntry(val id: String, val apiItem: ApiItem, val notAfter: Instant)
