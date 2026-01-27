package at.asit.wallet.relyingparty

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URL

@ConfigurationProperties(prefix = "app")
data class AppConfigurationProperties(
    /** Public URL of this instance, used for several URLs in messages sent to the Wallet. */
    val publicContext: URL = URL("http://localhost:8080/"),
    /** Key used for signing authn requests */
    val verifierKey: KeyConfiguration = KeyConfiguration(),
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


