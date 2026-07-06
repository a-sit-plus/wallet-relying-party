package at.asit.wallet.relyingparty

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TransactionStore(
    configuration: AppConfigurationProperties,
) {
    private val lifetime = configuration.resultTtl
    private val mutex = Mutex()
    private val entries: MutableList<ResultEntry> = mutableListOf()

    suspend fun getApiItems(): List<ApiItem> = mutex.withLock {
        removeExpiredEntries()
        entries.map { it.apiItem }
    }

    suspend fun getApiItem(id: String): ApiItem? = mutex.withLock {
        removeExpiredEntries()
        entries.firstOrNull { it.id == id }?.apiItem
    }

    suspend fun put(id: String, user: User): Boolean? = mutex.withLock {
        removeExpiredEntries()
        user.toApiItem()?.let { entries.add(ResultEntry(id, it, Instant.now().plus(lifetime))) }
    }

    @Scheduled(fixedDelay = 60_000)
    fun removeExpiredEntriesScheduled() = runBlocking {
        mutex.withLock {
            removeExpiredEntries()
        }
    }

    private fun removeExpiredEntries() {
        val now = Instant.now()
        entries.removeAll { it.notAfter < now }
    }
}

private data class ResultEntry(val id: String, val apiItem: ApiItem, val notAfter: Instant)
