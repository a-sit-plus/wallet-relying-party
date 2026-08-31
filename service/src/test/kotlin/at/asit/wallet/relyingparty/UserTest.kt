package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.openid.VpTokenValidationResultPresentationExchange
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class UserTest {

    @Suppress("DEPRECATION")
    @Test
    fun `non-DCQL validation results are rejected`() {
        shouldThrow<IllegalArgumentException> {
            VpTokenValidationResultPresentationExchange(emptyMap()).requireDcql()
        }
    }
}
