package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromByteArray
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.net.URI
import java.security.KeyStore
import kotlin.io.encoding.Base64

open class WrpPreviewData(
    open val label: String,
    open val content: String,
)

data class RegistrationCertificatePreviewData(
    val id: Int,
    val label: String,
    /** The WRPRC as compact JWS, if configured. */
    val jwt: String?,
    /** The WRPRC as base64url-encoded COSE_Sign1, if configured. */
    val cwt: String?,
)

/** One registration of the relying party, see [WrprcConfiguration]. */
class RegistrationCertificateData(
    val label: String,
    /** Sent in OpenID4VP `verifier_info`. */
    val jwt: String?,
    /** The untagged COSE_Sign1, sent in ISO 18013-7 Annex C `euWrprc`. */
    val cwt: ByteArray?,
)

data class AccessCertificatePreviewData(
    override val label: String,
    override val content: String,
    val id: Int,
) : WrpPreviewData(label, content)

data class AccessCertificateData(
    val label: String,
    val keyMaterial: KeyMaterial,
    val certificateChain: CertificateChain?,
)

@Component
class WrpCertificateStore(
    private val configuration: AppConfigurationProperties,
    private val resourceLoader: ResourceLoader,
) {
    val hasAccessCertificates: Boolean
        get() = accessCertificates.values.any { it.certificateChain?.isNotEmpty() == true }

    val registrationCertificates: Map<Int, RegistrationCertificateData> = configuration.wrp?.rc.orEmpty()
        .mapIndexed { index, wrprcConfiguration -> index to loadRegistrationCertificate(wrprcConfiguration) }
        .toMap()

    val hasRegistrationCertificates: Boolean
        get() = registrationCertificates.isNotEmpty()

    val accessCertificates: Map<Int, AccessCertificateData> = configuration.wrp?.ac.orEmpty()
        .mapIndexed { index, wrpacConfiguration ->
            val config = wrpacConfiguration.keystore
            val keyMaterial = KeyStoreMaterial(
                keyStore = KeyStore.getInstance(config.type, config.provider ?: "BC").apply {
                    load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
                },
                keyAlias = config.alias,
                privateKeyPassword = config.aliasPassword?.toCharArray() ?: charArrayOf(),
                certAlias = config.alias,
            )
            Napier.i("Loaded key store for WRPAC '${wrpacConfiguration.label}' from ${config.path}")
            index to AccessCertificateData(
                label = wrpacConfiguration.label,
                keyMaterial = keyMaterial,
                certificateChain = keyMaterial.getCertificateChain(),
            )
        }
        .toMap()

    fun accessCertificatePreview(): List<AccessCertificatePreviewData> =
        accessCertificates.mapNotNull { (index, data) ->
            data.certificateChain
                ?.mapNotNull { it.encodeToPEM().getOrNull() }
                ?.joinToString(separator = "\n")
                ?.let {
                    AccessCertificatePreviewData(
                        id = index,
                        label = data.label,
                        content = it.trim(),
                    )
                }
        }

    fun registrationCertificatePreview(): List<RegistrationCertificatePreviewData> =
        registrationCertificates.map { (index, data) ->
            RegistrationCertificatePreviewData(
                id = index,
                label = data.label,
                jwt = data.jwt,
                cwt = data.cwt?.let { Base64.UrlSafe.encode(it) },
            )
        }

    private fun loadRegistrationCertificate(config: WrprcConfiguration): RegistrationCertificateData {
        require(config.path != null || config.cwtPath != null) {
            "WRPRC '${config.label}' needs a path or cwt-path"
        }
        val fromPath = config.path?.let { loadResource(it) }
        val cwtFromPath = fromPath?.decodeCwtOrNull()
        val cwt = config.cwtPath?.let { path ->
            require(cwtFromPath == null) { "WRPRC '${config.label}' has a CWT in both path and cwt-path" }
            requireNotNull(loadResource(path).decodeCwtOrNull()) {
                "WRPRC '${config.label}' in $path is not a COSE_Sign1, neither binary nor base64-encoded"
            }
        } ?: cwtFromPath
        val jwt = fromPath?.takeIf { cwtFromPath == null }?.decodeToString()?.trim()?.takeIf { it.isNotBlank() }
        require(jwt != null || cwt != null) { "WRPRC '${config.label}' has neither a JWT nor a CWT" }
        Napier.i("Loaded WRPRC '${config.label}' (JWT: ${jwt != null}, CWT: ${cwt != null})")
        return RegistrationCertificateData(label = config.label, jwt = jwt, cwt = cwt)
    }

    private fun loadResource(uri: URI): ByteArray =
        resourceLoader.getResource(uri.toString()).inputStream.use { it.readBytes() }

    /**
     * The untagged COSE_Sign1 of a binary or base64-encoded CWT, which may be tagged as CWT (RFC 8392, tag 61)
     * and/or as COSE_Sign1 (RFC 9052, tag 18).
     */
    private fun ByteArray.decodeCwtOrNull(): ByteArray? {
        val text = runCatching { decodeToString(throwOnInvalidSequence = true).trim() }.getOrNull()
        val candidates = listOf(this) + listOf(
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL),
            Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL),
        ).mapNotNull { base64 -> text?.let { runCatching { base64.decode(it) }.getOrNull() } }
        return candidates
            .map { it.withoutPrefix(CWT_TAG).withoutPrefix(COSE_SIGN1_TAG) }
            .filter { it.firstOrNull() == UNTAGGED_COSE_SIGN1 }
            .firstOrNull { runCatching { coseCompliantSerializer.decodeFromByteArray<CoseSigned<ByteArray>>(it) }.isSuccess }
    }

    private fun ByteArray.withoutPrefix(prefix: ByteArray): ByteArray =
        if (size > prefix.size && prefix.indices.all { this[it] == prefix[it] }) copyOfRange(prefix.size, size) else this

    private companion object {
        val CWT_TAG = byteArrayOf(0xD8.toByte(), 0x3D)
        val COSE_SIGN1_TAG = byteArrayOf(0xD2.toByte())

        /** COSE_Sign1 is a CBOR array of four items, i.e. starts with `0x84` when not tagged. */
        const val UNTAGGED_COSE_SIGN1 = 0x84.toByte()
    }

}
