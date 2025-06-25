package at.asit.apps.terminal_sp.prototype.server

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.minutes

@Service
class TransactionStore {
    private val lifetime = 5.minutes
    private val lock = ReentrantLock()
    private val entries: MutableList<Entry> = mutableListOf()

    fun getApiItems(): List<ApiItem> = entries.map { it.apiItem }

    fun getApiItem(id: String): ApiItem? = entries.firstOrNull { it.id == id }?.apiItem

    @OptIn(InternalAPI::class)
    fun removeApiItem(id: String): ApiItem? = lock.withLock {
        removeExpiredEntries()
        val entry = entries.firstOrNull { it.id == id }
        if (entry != null) {
            entries.remove(entry)
        }
        return entry?.apiItem
    }

    @OptIn(InternalAPI::class)
    fun put(id: String, user: Siop2User): Boolean? = lock.withLock {
        removeExpiredEntries()
        user.toApiItem()?.let { entries.add(Entry(id, it, Clock.System.now().plus(lifetime))) }
    }

    private fun removeExpiredEntries() {
        entries.removeAll { it.notAfter < Clock.System.now() }
    }

}


data class Entry(val id: String, val apiItem: ApiItem, val notAfter: Instant)

