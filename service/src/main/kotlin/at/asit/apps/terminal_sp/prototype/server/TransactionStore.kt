package at.asit.apps.terminal_sp.prototype.server

import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.stereotype.Service
import java.util.HashMap

@Service
class TransactionStore {
    private val transactionToUser: MutableMap<String, AuthenticatedPrincipal> = HashMap()

    fun getApiItems(): List<ApiItem> = transactionToUser.mapNotNull { it.value.toApiItem() }
    fun getApiItem(id: String): ApiItem? = transactionToUser[id]?.toApiItem()
    fun removeApiItem(id: String): ApiItem? = transactionToUser.remove(id)?.toApiItem()
    fun put(id: String, user: Siop2User) {
        transactionToUser[id] = user
    }

}