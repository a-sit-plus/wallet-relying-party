package at.asit.wallet.relyingparty

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class ConfigurationRedactionTest {

    @Test
    fun `configuration does not print key store passwords`() {
        val keyStore = KeyStoreConfiguration(
            path = URI("file:/keys/verifier.p12"),
            type = "PKCS12",
            password = "store-secret",
            alias = "verifier",
            aliasPassword = "alias-secret",
        )
        val configuration = AppConfigurationProperties(
            verifierKey = KeyConfiguration(type = KeyType.KEYSTORE, keystore = keyStore),
            wrp = WrpConfigurationProperties(
                ac = listOf(
                    WrpacConfiguration(
                        label = "WRPAC",
                        keystore = keyStore.copy(path = URI("file:/keys/wrp.p12"), password = "wrp-secret"),
                    )
                ),
            ),
            dcApiSigners = listOf(
                DcApiSignerConfiguration(
                    id = "signer",
                    label = "Signer",
                    scheme = DcApiSignerScheme.X509_HASH,
                    key = KeyConfiguration(type = KeyType.KEYSTORE, keystore = keyStore),
                )
            ),
        ).toString()

        listOf("store-secret", "alias-secret", "wrp-secret").forEach {
            assertFalse(configuration.contains(it), "$it in $configuration")
        }
        // everything else is still shown, to debug the configuration
        assertTrue(configuration.contains("file:/keys/wrp.p12"), configuration)
        assertTrue(configuration.contains("alias=verifier"), configuration)
    }
}
