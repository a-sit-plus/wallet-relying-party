package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import io.github.aakira.napier.Napier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
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
        at.asitplus.wallet.taxid.Initializer.initWithVCK()
        at.asitplus.wallet.eupid.Initializer.initWithVCK()
        at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()
        at.asitplus.wallet.mdl.Initializer.initWithVCK()
        at.asitplus.wallet.cor.Initializer.initWithVCK()
        at.asitplus.wallet.por.Initializer.initWithVCK()
        at.asitplus.wallet.ehic.Initializer.initWithVCK()
        at.asitplus.wallet.ageverification.Initializer.initWithVCK()   
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
