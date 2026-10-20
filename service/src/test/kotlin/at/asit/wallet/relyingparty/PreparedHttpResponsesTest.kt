package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.HttpBody
import at.asitplus.wallet.lib.PreparedHttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreparedHttpResponsesTest {
    @Test
    fun `adapter preserves status repeated headers and encoded body`() {
        val prepared = PreparedHttpResponse(
            status = HttpStatusCode.Created,
            headers = Headers.build {
                append("Cache-Control", "no-store")
                append("X-Values", "first")
                append("X-Values", "second")
            },
            body = HttpBody(
                contentType = ContentType.Application.Json,
                text = "{\"redirect_uri\":\"https://example.com/result?id=123\"}",
            ),
        )

        val response = prepared.toResponseEntity()

        assertEquals(prepared.status.value, response.statusCode.value())
        prepared.headers.entries().forEach { (name, values) -> assertEquals(values, response.headers[name]) }
        assertEquals(listOf("application/json"), response.headers["Content-Type"])
        assertEquals(prepared.body?.text, response.body)
    }

    @Test
    fun `adapter writes no body and no content type without a body`() {
        val response = PreparedHttpResponse(status = HttpStatusCode.NoContent).toResponseEntity()

        assertEquals(HttpStatusCode.NoContent.value, response.statusCode.value())
        assertNull(response.headers.contentType)
        assertNull(response.body)
    }
}
