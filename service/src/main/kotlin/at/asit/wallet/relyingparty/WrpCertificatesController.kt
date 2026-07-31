package at.asit.wallet.relyingparty

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WrpCertificateAvailability(
    val hasCertificateChain: Boolean,
    val hasRegistrationCertificates: Boolean,
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
            hasCertificateChain = store.hasCertificateChain(),
            hasRegistrationCertificates = store.hasRegistrationCertificates,
        )
}
