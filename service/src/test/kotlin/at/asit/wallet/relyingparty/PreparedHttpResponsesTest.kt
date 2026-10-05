package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.PreparedHttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreparedHttpResponsesTest {
    @Test
    fun `adapter preserves status repeated headers and encoded body`() {
        val prepared = PreparedHttpResponse(
            status = HttpStatusCode.Created,
            headers = Headers.build {
                append("Content-Type", "application/json")
                append("Cache-Control", "no-store")
                append("X-Values", "first")
                append("X-Values", "second")
            },
            body = "{\"redirect_uri\":\"https://example.com/result?id=123\"}",
        )

        val response = prepared.toResponseEntity()

        assertEquals(prepared.status.value, response.statusCode.value())
        prepared.headers.entries().forEach { (name, values) -> assertEquals(values, response.headers[name]) }
        assertEquals(prepared.body, response.body)
    }
}
