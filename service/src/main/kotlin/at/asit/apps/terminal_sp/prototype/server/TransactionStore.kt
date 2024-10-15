package at.asit.apps.terminal_sp.prototype.server

import okio.withLock
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantLock

@Service
class TransactionStore {
    private val lock = ReentrantLock()
    private val entries: MutableList<Entry> = mutableListOf()

    fun getApiItems(): List<ApiItem> = entries.map { it.apiItem }

    fun getApiItem(id: String): ApiItem? = entries.firstOrNull { it.id == id }?.apiItem

    fun removeApiItem(id: String): ApiItem? = lock.withLock {
        val entry = entries.firstOrNull { it.id == id }
        if (entry != null) {
            entries.remove(entry)
        }
        return entry?.apiItem
    }

    fun put(id: String, user: Siop2User) {
        lock.withLock {
            user.toApiItem()?.let { entries.add(Entry(id, it)) }
        }
    }
}


data class Entry(val id: String, val apiItem: ApiItem)

