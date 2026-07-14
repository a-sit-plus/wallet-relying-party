package at.asitplus.wallet.rp

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class AuthenticationCallback(val transactionId: String) {
    companion object {
        fun parse(url: String, expectedState: UUID): AuthenticationCallback {
            val uri = URI(url)
            require(uri.scheme.equals("wallet-rp", ignoreCase = true))
            require(uri.host.equals("auth", ignoreCase = true))
            require(uri.path == "/callback")

            val parameters = uri.rawQuery.orEmpty()
                .split('&')
                .filter(String::isNotEmpty)
                .map { parameter ->
                    val parts = parameter.split('=', limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
                }
            val transactionIds = parameters.filter { it.first == "transaction_id" }.map { it.second }
            val states = parameters.filter { it.first == "state" }.map { it.second }

            require(transactionIds.size == 1 && transactionIds.single().isNotEmpty())
            require(states.size == 1 && UUID.fromString(states.single()) == expectedState)
            return AuthenticationCallback(transactionIds.single())
        }

        private fun decode(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
