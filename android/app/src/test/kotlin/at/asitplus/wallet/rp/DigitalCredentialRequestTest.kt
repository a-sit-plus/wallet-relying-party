package at.asitplus.wallet.rp

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalCredentialRequestTest {
    @Test
    fun unwrapsBrowserCredentialRequestOptionsForAndroid() {
        val request = JSONObject(
            digitalCredentialRequestJson(
                """{"mediation":"required","digital":{"requests":[{"protocol":"org-iso-mdoc"}]}}"""
            )
        )

        assertTrue(request.has("requests"))
        assertFalse(request.has("digital"))
    }
}
