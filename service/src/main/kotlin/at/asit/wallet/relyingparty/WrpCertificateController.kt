package at.asit.wallet.relyingparty

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class WrpCertificateAvailability(
    val hasWrpac: Boolean,
    val hasWrprc: Boolean,
)

data class SaveWrprcRequest(
    val jws: String,
)

@RestController
@RequestMapping("/api/wrp")
class WrpCertificateController(
    private val store: WrpCertificateStore,
) {
    @GetMapping("/cert-options")
    fun certificateOptions(): List<WrpCertificateOption> =
        store.certificateOptions()

    @GetMapping("/certs")
    fun certificatePreviews(): List<WrpCertificatePreview> =
        store.certificatePreviews()

    @GetMapping("/availability")
    fun certificateAvailability(): WrpCertificateAvailability =
        WrpCertificateAvailability(
            hasWrpac = store.hasWrpac(),
            hasWrprc = store.hasWrprc(),
        )

    @PostMapping("/wrprc")
    fun saveWrprc(
        @RequestBody request: SaveWrprcRequest,
    ) {
        store.saveWrprc(request.jws.trim())
    }

    @DeleteMapping("/wrprc")
    fun deleteWrprc() {
        store.deleteWrprc()
    }

    @PostMapping(
        value = ["/wrpac"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun saveWrpac(
        @RequestPart("chainPem") chainPem: String,
        @RequestPart("keyStore") keyStore: MultipartFile,
        @RequestPart("thumbprint", required = false) thumbprint: String?,
    ) {
        store.saveWrpac(
            chainPem = chainPem.trim(),
            keyStoreBytes = keyStore.bytes,
            thumbprint = thumbprint?.trim().orEmpty(),
        )
    }

    @DeleteMapping("/wrpac")
    fun deleteWrpac() {
        store.deleteWrpac()
    }
}
