package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate as JcaX509Certificate

@Serializable
data class WrpStoredState(
    val wrpIdentifier: String? = null,
    val requestStatus: String? = null,
    val serviceUri: String? = null,
    val wrpacThumbprint: String? = null,
)

data class WrpCertificatePreview(
    val id: String,
    val label: String,
    val type: String,
    val content: String,
)

data class WrpCertificateOption(
    val id: String,
    val label: String,
    val scheme: String,
)

@Component
class WrpCertificateStore(
    @Value("\${app.wrp.storage-dir}") storageDir: String,
) {
    private val json = Json { prettyPrint = true }
    private val storagePath: Path = Paths.get(storageDir)
    private val statePath = storagePath.resolve("state.json")
    private val wrpacChainPath = storagePath.resolve("wrpac-chain.pem")
    private val wrpacKeyStorePath = storagePath.resolve("wrpac.p12")
    private val wrprcPath = storagePath.resolve("wrprc.jws")

    init {
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath)
        }
    }

    fun loadState(): WrpStoredState =
        if (Files.exists(statePath)) {
            json.decodeFromString(WrpStoredState.serializer(), Files.readString(statePath))
        } else {
            WrpStoredState()
        }

    fun saveState(state: WrpStoredState) {
        Files.writeString(statePath, json.encodeToString(state), StandardCharsets.UTF_8)
    }

    fun saveWrpac(chainPem: String, keyStoreBytes: ByteArray, thumbprint: String) {
        Files.writeString(wrpacChainPath, chainPem, StandardCharsets.UTF_8)
        Files.write(wrpacKeyStorePath, keyStoreBytes)
        val current = loadState()
        saveState(current.copy(wrpacThumbprint = thumbprint))
    }

    fun saveWrprc(jws: String) {
        Files.writeString(wrprcPath, jws, StandardCharsets.UTF_8)
    }

    fun deleteWrpac() {
        Files.deleteIfExists(wrpacChainPath)
        Files.deleteIfExists(wrpacKeyStorePath)
        val current = loadState()
        if (current.wrpacThumbprint != null) {
            saveState(current.copy(wrpacThumbprint = null))
        }
    }

    fun deleteWrprc() {
        Files.deleteIfExists(wrprcPath)
    }

    fun loadWrpacPem(): String? =
        if (Files.exists(wrpacChainPath)) Files.readString(wrpacChainPath) else null

    fun loadWrprcJws(): String? =
        if (Files.exists(wrprcPath)) Files.readString(wrprcPath) else null

    fun hasWrpac(): Boolean = Files.exists(wrpacChainPath) && Files.exists(wrpacKeyStorePath)

    fun hasWrprc(): Boolean = Files.exists(wrprcPath)

    fun loadWrpacKeyMaterial(): KeyMaterial? {
        if (!Files.exists(wrpacKeyStorePath)) {
            return null
        }
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(Files.newInputStream(wrpacKeyStorePath), WRPAC_PASSWORD.toCharArray())
        }
        return KeyStoreMaterial(
            keyStore = keyStore,
            keyAlias = WRPAC_ALIAS,
            privateKeyPassword = WRPAC_PASSWORD.toCharArray(),
            certAlias = WRPAC_ALIAS,
        )
    }

    fun loadWrpacChain(): CertificateChain? {
        val pem = loadWrpacPem() ?: return null
        val certificates = parsePemCertificates(pem)
        if (certificates.isEmpty()) {
            return null
        }
        val signumCerts = certificates.mapNotNull { X509Certificate.decodeFromByteArray(it.encoded) }
        if (signumCerts.isEmpty()) {
            return null
        }
        return signumCerts
    }

    fun certificatePreviews(): List<WrpCertificatePreview> {
        val previews = mutableListOf<WrpCertificatePreview>()
        loadWrpacPem()?.let {
            previews += WrpCertificatePreview(
                id = CERT_ID_WRPAC,
                label = "WRPAC",
                type = "x509-chain",
                content = it.trim(),
            )
        }
        loadWrprcJws()?.let {
            previews += WrpCertificatePreview(
                id = CERT_ID_WRPRC,
                label = "WRPRC",
                type = "jws",
                content = it.trim(),
            )
        }
        return previews
    }

    fun certificateOptions(): List<WrpCertificateOption> {
        val options = mutableListOf(
            WrpCertificateOption(
                id = CERT_ID_VERIFIER,
                label = "Default verifier",
                scheme = "x509_san_dns",
            )
        )
        if (Files.exists(wrpacKeyStorePath)) {
            options += WrpCertificateOption(
                id = CERT_ID_WRPAC,
                label = "WRPAC (x509_hash)",
                scheme = "x509_hash",
            )
        }
        return options
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

    companion object {
        const val CERT_ID_VERIFIER = "verifier"
        const val CERT_ID_WRPAC = "wrpac"
        const val CERT_ID_WRPRC = "wrprc"
        private const val WRPAC_ALIAS = "wrpac"
        private const val WRPAC_PASSWORD = "changeit"
    }
}
