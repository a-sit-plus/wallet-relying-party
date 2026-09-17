package at.asit.wallet.relyingparty

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WrpCertificateAvailability(
    val hasAccessCertificates: Boolean,
    val hasRegistrationCertificates: Boolean,
)

@RestController
@RequestMapping("/api/wrp")
class WrpCertificatesController(
    private val store: WrpCertificateStore,
) {
    @GetMapping("/certs/access")
    fun accessCertificatePreview(): List<AccessCertificatePreviewData> =
        store.accessCertificatePreview()

    @GetMapping("/certs/registrations")
    fun registrationCertificatePreview(): List<RegistrationCertificatePreviewData>? =
        store.registrationCertificatePreview()

    @GetMapping("/availability")
    fun certificateAvailability(): WrpCertificateAvailability =
        WrpCertificateAvailability(
            hasAccessCertificates = store.hasAccessCertificates,
            hasRegistrationCertificates = store.hasRegistrationCertificates,
        )
}
