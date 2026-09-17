package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import io.github.aakira.napier.Napier
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
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
        configuration.wrp?.rc?.isNotEmpty() == true && configuration.wrp.rc.all { resourceExists(it.path) }

    val registrationCertificates: Map<Int, Pair<WrprcConfiguration, String>>? =
        configuration.wrp?.rc?.mapIndexed { index, configuration ->
            index to Pair(configuration, loadResourceAsString(configuration.path))
        }?.toMap()

    val keyMaterial: KeyStoreMaterial? = configuration.wrp?.keystore?.let { config ->
        KeyStoreMaterial(
            keyStore = KeyStore.getInstance(config.type, config.provider ?: "BC").apply {
                load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
            },
            keyAlias = config.alias,
            privateKeyPassword = config.aliasPassword?.toCharArray() ?: charArrayOf(),
            certAlias = config.alias,
        )
    }?.also {
        Napier.i("Loaded key store for WRPAC from ${configuration.wrp.keystore.path}")
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

    private fun resourceExists(uri: URI): Boolean = resourceLoader.getResource(uri.toString()).exists()

    companion object {
        const val CERT_ID_WRPAC = "wrpac"
    }

}