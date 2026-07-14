package at.asitplus.wallet.rp

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthenticationCallbackTest {
    private val state = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    @Test
    fun acceptsExpectedCallback() {
        val callback = AuthenticationCallback.parse(
            "wallet-rp://auth/callback?transaction_id=transaction%20id&state=$state",
            state,
        )

        assertEquals("transaction id", callback.transactionId)
    }

    @Test
    fun rejectsWrongStateAndDuplicateCapabilities() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticationCallback.parse(
                "wallet-rp://auth/callback?transaction_id=one&transaction_id=two&state=$state",
                state,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticationCallback.parse(
                "wallet-rp://auth/callback?transaction_id=one&state=223e4567-e89b-12d3-a456-426614174000",
                state,
            )
        }
    }
}
