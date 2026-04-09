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
    /** Terminal service point registration/certificate configuration */
    val wrp: WrpConfiguration = WrpConfiguration(),
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
)

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
}

data class WrpConfiguration(
    val certificates: WrpCertificateConfiguration = WrpCertificateConfiguration(),
)

data class WrpCertificateConfiguration(
    val wrpac: WrpacConfiguration? = null,
    val wrprc: Map<String, WrprcConfiguration> = emptyMap(),
)

data class WrpacConfiguration(
    val label: String? = null,
    val chain: URI? = null,
    val keyStore: URI? = null,
    val password: String? = null,
)

data class WrprcConfiguration(
    val label: String? = null,
    val jws: URI? = null,
)
