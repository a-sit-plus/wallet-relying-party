package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.PreparedHttpResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity

/** Pass VC-K's status, headers and already encoded body to Spring unchanged. */
fun PreparedHttpResponse.toResponseEntity(): ResponseEntity<String> = ResponseEntity
    .status(status.value)
    .headers(HttpHeaders().apply {
        this@toResponseEntity.headers.entries().forEach { (name, values) -> addAll(name, values) }
    })
    .body(body)
