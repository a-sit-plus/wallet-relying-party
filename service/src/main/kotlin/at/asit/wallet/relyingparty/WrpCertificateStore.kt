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

open class WrpPreviewData(
    open val label: String,
    open val content: String,
)

data class RegistrationCertificatePreviewData(
    override val label: String,
    override val content: String,
    val id: Int,
) : WrpPreviewData(label, content)

@Component
class WrpCertificateStore(
    private val configuration: AppConfigurationProperties,
    private val resourceLoader: ResourceLoader,
) {
    fun hasCertificateChain(): Boolean = keyMaterial?.getCertificateChain()?.isNotEmpty() == true
    val hasRegistrationCertificates: Boolean =
        configuration.wrp?.rc?.isNotEmpty() == true && configuration.wrp.rc.all { resourceExists(it.jws) }
    private val keyStorePassword = configuration.wrp?.password
    val registrationCertificates: Map<Int, Pair<WrprcConfiguration, String>>? =
        configuration.wrp?.rc?.mapIndexed { index, configuration ->
            index to Pair(configuration, loadResourceAsString(configuration.jws))
        }?.toMap()

    val keyMaterial: KeyStoreMaterial? = configuration.wrp?.keyStore?.let {
        catching {
            val password = keyStorePassword?.toCharArray()
            val keyStoreBytes = configuration.wrp?.keyStore?.let { loadResourceAsBytes(it) }
                ?: throw Throwable("Unable to load resource at path ${configuration.wrp?.keyStore}")
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(ByteArrayInputStream(keyStoreBytes), password)
            }
            Napier.i("Loaded key store for WRPAC from ${configuration.wrp}")
            return@catching KeyStoreMaterial(
                keyStore = keyStore,
                keyAlias = configuration.wrp.alias,
                privateKeyPassword = password,
                certAlias = configuration.wrp.alias,
            )
        }.getOrElse { error ->
            throw Throwable("Unable to initialize KeyStore!", error)
        }
    }

    fun loadCertificateChain(): CertificateChain? = keyMaterial?.getCertificateChain() ?: return null

    fun accessCertificatePreview(): WrpPreviewData? =
        loadCertificateChain()?.mapNotNull { it.encodeToPEM().getOrNull() }?.joinToString(separator = "\n")?.let {
            WrpPreviewData(
                label = CERT_ID_WRPAC,
                content = it.trim(),
            )
        }

    fun registrationCertificatePreview(): List<RegistrationCertificatePreviewData>? =
        registrationCertificates?.map { (index, pair) ->
            val (config, content) = pair
            RegistrationCertificatePreviewData(
                id = index,
                label = config.label,
                content = content.trim(),
            )
        }

    private fun loadResourceAsString(uri: URI): String =
        StreamUtils.copyToString(resourceLoader.getResource(uri.toString()).inputStream, StandardCharsets.UTF_8)

    private fun loadResourceAsBytes(uri: URI): ByteArray =
        resourceLoader.getResource(uri.toString()).inputStream.use { it.readBytes() }

    private fun resourceExists(uri: URI): Boolean = resourceLoader.getResource(uri.toString()).exists()

    companion object {
        const val CERT_ID_WRPAC = "wrpac"
    }
}
