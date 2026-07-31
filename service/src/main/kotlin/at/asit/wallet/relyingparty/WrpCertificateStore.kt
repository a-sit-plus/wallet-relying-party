package at.asit.wallet.relyingparty

import at.asitplus.catching
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import io.github.aakira.napier.Napier
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore

data class WrpCertificatePreview(
    val id: Int? = null,
    val label: String,
    val category: String,
    val type: String,
    val content: String,
) {
    companion object {
        const val JWS_TYPE = "jws"
        const val X509_TYPE = "x509-chain"
    }
}

@Component
class WrpCertificateStore(
    private val configuration: AppConfigurationProperties,
    private val resourceLoader: ResourceLoader,
) {
    fun hasCertificateChain(): Boolean = loadKeyMaterial()?.getCertificateChain()?.isNotEmpty() == true
    val hasRegistrationCertificates: Boolean =
        configuration.wrp?.rc?.isNotEmpty() == true && configuration.wrp.rc.all { resourceExists(URI(it.jws)) }
    private val keyStorePassword = configuration.wrp?.password
    var cachedRegistrationCertificates: Map<Int, Pair<WrprcConfiguration, String>>? = null
    fun loadRegistrationCertificates(): Map<Int, Pair<WrprcConfiguration, String>>? =
        cachedRegistrationCertificates ?: run {
            configuration.wrp?.rc?.mapIndexed { index, configuration ->
                index to Pair(configuration, loadResourceAsString(URI(configuration.jws)))
            }?.toMap()
        }?.also {
            cachedRegistrationCertificates = it
        }

    var cachedKeyMaterial: KeyStoreMaterial? = null
    fun loadKeyMaterial(): KeyStoreMaterial? = cachedKeyMaterial ?: catching {
        val password = keyStorePassword?.toCharArray()
        val keyStoreBytes = configuration.wrp?.keyStore?.let { loadResourceAsBytes(URI(it)) } ?: return@catching null
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(keyStoreBytes), password)
        }
        Napier.i("$keyStore")
        return@catching KeyStoreMaterial(
            keyStore = keyStore,
            keyAlias = configuration.wrp.alias,
            privateKeyPassword = password,
            certAlias = configuration.wrp.alias,
        )
    }.getOrNull()?.also {
        cachedKeyMaterial = it
    }

    fun loadCertificateChain(): CertificateChain? = loadKeyMaterial()?.getCertificateChain() ?: return null

    fun certificatePreviews(): List<WrpCertificatePreview> {
        val previews = mutableListOf<WrpCertificatePreview>()
        val pem = loadCertificateChain()?.mapNotNull { it.encodeToPEM().getOrNull() }?.joinToString(separator = "")
        pem?.let {
            previews += WrpCertificatePreview(
                label = CERT_ID_WRPAC,
                category = CERT_ID_WRPAC,
                type = WrpCertificatePreview.X509_TYPE,
                content = it.trim(),
            )
        }
        loadRegistrationCertificates()?.forEach { index, (config, content) ->
            previews += WrpCertificatePreview(
                id = index,
                label = config.label,
                category = CERT_ID_WRPRC,
                type = WrpCertificatePreview.JWS_TYPE,
                content = content.trim(),
            )
        }
        return previews
    }

    private fun loadResourceAsString(uri: URI): String =
        StreamUtils.copyToString(resourceLoader.getResource(uri.toString()).inputStream, StandardCharsets.UTF_8)

    private fun loadResourceAsBytes(uri: URI): ByteArray =
        resourceLoader.getResource(uri.toString()).inputStream.use { it.readBytes() }

    private fun resourceExists(uri: URI): Boolean = resourceLoader.getResource(uri.toString()).exists()

    companion object {
        const val CERT_ID_WRPAC = "wrpac"
        const val CERT_ID_WRPRC = "wrprc"
    }
}
