package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate as JcaX509Certificate

data class WrpCertificatePreview(
    val id: String,
    val label: String,
    val type: String,
    val content: String,
)

@Component
class WrpCertificateStore(
    private val configuration: AppConfigurationProperties,
    private val resourceLoader: ResourceLoader,
) {
    private val configuredWrpac = configuration.wrp.certificates.wrpac?.takeIf { it.chain != null && it.keyStore != null }
    private val configuredWrprc = configuration.wrp.certificates.wrprc?.takeIf { it.jws != null }
    private val wrpacPassword = configuration.wrp.certificates.wrpac?.password?.takeIf { it.isNotBlank() }

    fun loadWrpacPem(): String? = configuredWrpac?.chain?.let { loadResourceAsString(it) }

    fun loadWrprcJws(): String? = configuredWrprc?.jws?.let { loadResourceAsString(it) }

    fun hasWrpac(): Boolean = hasWrpacChain() && hasWrpacKeyMaterial()

    fun hasWrpacChain(): Boolean = configuredWrpac?.chain?.let { resourceExists(it) } == true

    fun hasWrpacKeyMaterial(): Boolean = configuredWrpac?.keyStore?.let { resourceExists(it) } == true && wrpacPassword != null

    fun hasWrprc(): Boolean = configuredWrprc?.jws?.let { resourceExists(it) } == true

    fun loadWrpacKeyMaterial(): KeyMaterial? {
        val password = wrpacPassword ?: return null
        val keyStoreBytes = configuredWrpac?.keyStore?.let { loadResourceAsBytes(it) } ?: return null
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(keyStoreBytes), password.toCharArray())
        }
        return KeyStoreMaterial(
            keyStore = keyStore,
            keyAlias = WRPAC_ALIAS,
            privateKeyPassword = password.toCharArray(),
            certAlias = WRPAC_ALIAS,
        )
    }

    fun loadWrpacChain(): CertificateChain? {
        val pem = loadWrpacPem() ?: return null
        val certificates = parsePemCertificates(pem)
        if (certificates.isEmpty()) return null
        val decodedTransportCertificates =
            certificates.mapNotNull { X509Certificate.decodeFromByteArray(it.encoded) }
        if (decodedTransportCertificates.isEmpty()) return null
        return decodedTransportCertificates
    }

    fun certificatePreviews(): List<WrpCertificatePreview> {
        val previews = mutableListOf<WrpCertificatePreview>()
        loadWrpacPem()?.let {
            previews += WrpCertificatePreview(
                id = CERT_ID_WRPAC,
                label = configuredWrpac?.let { "WRPAC (configured)" } ?: "WRPAC",
                type = "x509-chain",
                content = it.trim(),
            )
        }
        loadWrprcJws()?.let {
            previews += WrpCertificatePreview(
                id = CERT_ID_WRPRC,
                label = configuredWrprc?.let { "WRPRC (configured)" } ?: "WRPRC",
                type = "jws",
                content = it.trim(),
            )
        }
        return previews
    }

    private fun parsePemCertificates(pem: String): List<JcaX509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return pem.split("-----END CERTIFICATE-----")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { block ->
                val normalized = "$block\n-----END CERTIFICATE-----\n"
                factory.generateCertificate(ByteArrayInputStream(normalized.toByteArray(StandardCharsets.UTF_8))) as JcaX509Certificate
            }
    }

    private fun loadResourceAsString(uri: java.net.URI): String =
        StreamUtils.copyToString(resourceLoader.getResource(uri.toString()).inputStream, StandardCharsets.UTF_8)

    private fun loadResourceAsBytes(uri: java.net.URI): ByteArray =
        resourceLoader.getResource(uri.toString()).inputStream.use { it.readBytes() }

    private fun resourceExists(uri: java.net.URI): Boolean = resourceLoader.getResource(uri.toString()).exists()

    companion object {
        const val CERT_ID_WRPAC = "wrpac"
        const val CERT_ID_WRPRC = "wrprc"
        private const val WRPAC_ALIAS = "wrpac"
    }
}
