package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.wallet.lib.agent.KeyMaterial
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

    val hasRegistrationCertificates: Boolean =
        configuration.wrp?.rc?.isNotEmpty() == true && configuration.wrp.rc.all { resourceExists(it.path) }

    val registrationCertificates: Map<Int, Pair<WrprcConfiguration, String>>? =
        configuration.wrp?.rc?.mapIndexed { index, configuration ->
            index to Pair(configuration, loadResourceAsString(configuration.path))
        }?.toMap()

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

}
