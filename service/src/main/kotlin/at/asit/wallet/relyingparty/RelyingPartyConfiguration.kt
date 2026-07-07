package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.eupid.EuPidItemValueSerializerMap
import at.asitplus.wallet.eupid.EuPidJsonValueEncoder
import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import at.asitplus.wallet.mdl.MobileDrivingLicenceItemValueSerializerMap
import at.asitplus.wallet.mdl.MobileDrivingLicenceJsonValueEncoder
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.PublicKey
import java.security.Security
import kotlin.time.Clock


@Configuration
@EnableConfigurationProperties(AppConfigurationProperties::class)
class RelyingPartyConfiguration {

    @Autowired
    private lateinit var configuration: AppConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    init {
        Napier.takeLogarithm()
        Napier.base(AntilogSlf4jAdapter)
        Security.addProvider(BouncyCastleProvider())
    }

    /**
     * Fetches the SD-JWT Type Metadata documents live from the hosted collection. Tests override this with a
     * [io.ktor.client.engine.mock.MockEngine] serving the cached documents from test resources
     * (see `CachedTypeMetadataConfiguration` in the test sources), so no test ever talks to GitHub.
     */
    @Bean
    fun metadataHttpClient(): HttpClient = HttpClient()

    /**
     * Credential schemes are derived from remote SD-JWT Type Metadata documents (see [CredentialCatalog]); register
     * the registry once at startup. ISO mdoc credentials with non-primitive values still need their value serializers
     * registered from code — only mDL and EU PID (ISO) require these.
     */
    @Bean
    fun credentialMetadataRegistry(metadataHttpClient: HttpClient): RemoteCredentialMetadataRegistry {
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = MobileDrivingLicenceJsonValueEncoder,
            itemValueSerializerMap = MobileDrivingLicenceItemValueSerializerMap,
        )
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = EuPidJsonValueEncoder,
            itemValueSerializerMap = EuPidItemValueSerializerMap,
        )
        return RemoteCredentialMetadataRegistry(
            httpClient = metadataHttpClient,
            clock = Clock.System,
            documentUrls = CredentialCatalog.documentUrls(),
            aliases = CredentialCatalog.aliases(),
        ).also { LibraryInitializer.registerCredentialMetadataRegistry(it) }
    }

    /**
     * Resolve all catalog documents once at startup, so an unreachable or broken document surfaces in the log
     * immediately instead of on the first transaction; failures are logged, not fatal, since the registry retries on
     * every lookup anyway.
     */
    @Bean
    fun credentialMetadataStartupResolver(registry: RemoteCredentialMetadataRegistry) = ApplicationRunner {
        runBlocking {
            CredentialCatalog.entries.forEach { entry ->
                if (registry.findEntry(entry.identifier, entry.representation) == null)
                    Napier.w("Type metadata for ${entry.identifier} not resolvable from ${entry.url}")
                else
                    Napier.i("Type metadata for ${entry.identifier} resolved from ${entry.url}")
            }
        }
    }

    @Bean("verifierKeyMaterial")
    fun verifierKeyMaterial(): KeyMaterial = when (configuration.verifierKey.type) {
        KeyType.FILE -> loadKeyFile(configuration.verifierKey.file!!, resourceLoader)
        KeyType.KEYSTORE -> loadKeyStore(configuration.verifierKey.keystore!!)
        KeyType.MEMORY -> EphemeralKeyWithSelfSignedCert(extensions = listOf(
            X509CertificateExtension(
                KnownOIDs.subjectAltName_2_5_29_17,
                critical = false,
                Asn1EncapsulatingOctetString(
                    listOf(
                        Asn1.Sequence {
                            +Asn1Primitive(
                                SubjectAltNameImplicitTags.dNSName,
                                Asn1String.UTF8(configuration.publicContext.host).encodeToTlv().content
                            )
                        }
                    )))
        ))
    }

    fun loadKeyStore(config: KeyStoreConfiguration) = KeyStoreMaterial(
        keyStore = KeyStore.getInstance(config.type, config.provider ?: "BC").apply {
            load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        },
        keyAlias = config.alias,
        privateKeyPassword = config.aliasPassword?.toCharArray() ?: charArrayOf(),
        certAlias = config.alias
    )

    fun loadKeyFile(file: KeyFileConfiguration, resourceLoader: ResourceLoader): KeyStoreMaterial {
        val privateKeyString = loadResource(resourceLoader, file.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        val privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val (jcaKey, jcaCert) = loadCertOrPubKey(file.publicKey, file.certificate, resourceLoader)
        return KeyStoreMaterial(
            keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("alias", privateKey, charArrayOf(), jcaCert?.let { arrayOf(it) })
            },
            keyAlias = "alias",
            privateKeyPassword = charArrayOf(),
            certAlias = jcaCert?.let { "alias" }
        )
    }

    private fun loadCertOrPubKey(
        publicKey: URI?,
        certificate: URI?,
        resourceLoader: ResourceLoader,
    ): Pair<PublicKey, java.security.cert.X509Certificate?> {
        if (publicKey == null && certificate == null)
            throw RuntimeException("Neither cert nor public key configured. Set one!")
        if (publicKey != null && certificate != null)
            throw RuntimeException("Both public key and certificate set. Set either but not both!")
        return (publicKey?.let {
            val publicKeyString = loadResource(resourceLoader, it.toString())
            val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
            JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo) to null
        } ?: certificate?.let {
            loadCertificate(resourceLoader, it).let { it.publicKey to it }
        })!!
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())

    private fun loadCertificate(resourceLoader: ResourceLoader, src: URI) =
        JcaX509CertificateConverter().apply { setProvider("BC") }.getCertificate(
            PEMParser(StringReader(loadResource(resourceLoader, src.toString()))).readObject() as X509CertificateHolder
        )

}
