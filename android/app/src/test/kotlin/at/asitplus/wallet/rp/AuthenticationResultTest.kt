package at.asitplus.wallet.rp

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationResultTest {
    @Test
    fun parsesPortraitAndCredentialClaims() {
        val result = AuthenticationResult.parse(
            """{
                "imageDataBase64": "data:image/png;base64,SGk=",
                "credentials": [{
                    "credentialType": "eu.europa.ec.eudi.pid.1",
                    "allFields": {"portrait": "ignored", "given_name": "Ada", "age_over_18": true}
                }]
            }""",
        )

        assertEquals("Hi", result.portrait?.decodeToString())
        assertEquals("eu.europa.ec.eudi.pid.1", result.credentials.single().type)
        assertEquals(
            listOf(
                AuthenticationResult.Claim("age_over_18", "true"),
                AuthenticationResult.Claim("given_name", "Ada"),
            ),
            result.credentials.single().claims,
        )
    }
}
