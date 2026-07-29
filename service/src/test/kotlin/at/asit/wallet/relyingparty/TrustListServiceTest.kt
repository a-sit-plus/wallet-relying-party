package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.pki.X509Certificate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TrustListServiceTest {

    private val service = TrustListService(TrustListCache())

    @Test
    fun `trust is evaluated for each credential`() {
        val trustedCertificate = X509Certificate.decodeFromPem(
            checkNotNull(javaClass.getResource("/asit-root.pem")).readText()
        ).getOrThrow()
        val credentials = listOf(
            ApiItemCredential(
                credentialType = "eu.europa.ec.eudi.pid.1",
                issuerCertificate = trustedCertificate,
            ),
            ApiItemCredential(
                credentialType = "org.iso.18013.5.1.mDL",
                issuerCertificate = null,
            ),
        ).map { it.copy(trustState = service.evaluateCredentialIssuerTrust(it)) }

        assertEquals(listOf(TrustState.TRUSTED, TrustState.UNKNOWN), credentials.map { it.trustState })

        val apiItem = User(null, null, credentials, null).apiItem
        val serialized = Json.encodeToJsonElement(ApiItem.serializer(), apiItem).jsonObject
        assertFalse("trustState" in serialized)
        assertEquals(
            listOf("TRUSTED", "UNKNOWN"),
            serialized.getValue("credentials").jsonArray.map {
                it.jsonObject.getValue("trustState").jsonPrimitive.content
            },
        )
        assertFalse(serialized.toString().contains("issuerCertificate"))
    }
}
