package at.asit.wallet.relyingparty

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URL
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppConfigurationProperties(
    /** Public URL of this instance, used for several URLs in messages sent to the Wallet. */
    val publicContext: URL = URL("http://localhost:8080/"),
    /** Key used for signing authn requests */
    val verifierKey: KeyConfiguration = KeyConfiguration(),
    /** How long pending wallet presentation transactions remain valid. */
    val transactionTtl: Duration = Duration.ofMinutes(30),
    /** How long validated demo results remain available through the result API. */
    val resultTtl: Duration = Duration.ofMinutes(30),
    /** Wallet relying party certificate configuration */
    val wrp: WrpConfigurationProperties? = null,
    /** Verifier identities available for signed OpenID4VP requests over the Digital Credentials API. */
    val dcApiSigners: List<DcApiSignerConfiguration> = emptyList(),
)

fun URL.appendPath(path: String): String = UriComponentsBuilder.fromUri(toURI()).path(path).toUriString()

data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
    val keystore: KeyStoreConfiguration? = null,
)

data class KeyFileConfiguration(
    val privateKey: URI,
    val publicKey: URI?,
    val certificate: URI?,
)

data class KeyStoreConfiguration(
    val path: URI,
    val type: String,
    val provider: String? = null,
    val password: String? = null,
    val alias: String,
    val aliasPassword: String? = null,
) {
    override fun toString() = "KeyStoreConfiguration(path=$path, type=$type, provider=$provider, " +
            "password=${password.redacted()}, alias=$alias, aliasPassword=${aliasPassword.redacted()})"
}

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
}

data class WrpConfigurationProperties(
    val ac: List<WrpacConfiguration> = listOf(),
    val rc: List<WrprcConfiguration> = listOf(),
)

data class DcApiSignerConfiguration(
    val id: String,
    val label: String,
    val scheme: DcApiSignerScheme,
    val key: KeyConfiguration = KeyConfiguration(),
    /** Compact verifier-attestation JWT, required for VERIFIER_ATTESTATION. */
    val verifierAttestation: URI? = null,
)

enum class DcApiSignerScheme {
    X509_SAN_DNS,
    X509_HASH,
    VERIFIER_ATTESTATION,
}

data class WrpacConfiguration(
    val label: String,
    val keystore: KeyStoreConfiguration,
)

data class WrprcConfiguration(
    val label: String,
    val path: URI,
)

/** Keeps secrets out of logs, while still showing whether one is configured. */
private fun String?.redacted() = if (this == null) "null" else "***"
