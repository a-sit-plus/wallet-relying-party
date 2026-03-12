package at.asit.wallet.relyingparty

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WrpCertificateAvailability(
    val hasWrpac: Boolean,
    val hasWrpacChain: Boolean,
    val hasWrpacKeyMaterial: Boolean,
    val hasWrprc: Boolean,
)

@RestController
@RequestMapping("/api/wrp")
class WrpCertificatesController(
    private val store: WrpCertificateStore,
) {
    @GetMapping("/certs")
    fun certificatePreviews(): List<WrpCertificatePreview> =
        store.certificatePreviews()

    @GetMapping("/availability")
    fun certificateAvailability(): WrpCertificateAvailability =
        WrpCertificateAvailability(
            hasWrpac = store.hasWrpac(),
            hasWrpacChain = store.hasWrpacChain(),
            hasWrpacKeyMaterial = store.hasWrpacKeyMaterial(),
            hasWrprc = store.hasWrprc(),
        )
}
