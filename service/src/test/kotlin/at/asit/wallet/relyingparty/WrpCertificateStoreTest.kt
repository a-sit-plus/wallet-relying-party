package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.cbor.SignCose
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.io.File
import java.net.URI
import kotlin.io.encoding.Base64

class WrpCertificateStoreTest {

    @TempDir
    lateinit var dir: File

    private val jwt = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJycCJ9.c2lnbmF0dXJl"

    @Test
    fun `JWT in path and binary CWT in cwt-path form one WRPRC`() = runTest {
        val cwt = cwt()
        val store = store(WrprcConfiguration("Both", path = file("rc.jws", jwt + "\n"), cwtPath = file("rc.cwt", cwt)))

        val wrprc = store.registrationCertificates.getValue(0)
        assertEquals(jwt, wrprc.jwt)
        assertArrayEquals(cwt, wrprc.cwt)
        store.registrationCertificatePreview().single().let {
            assertEquals(jwt, it.jwt)
            assertEquals(Base64.UrlSafe.encode(cwt), it.cwt)
        }
    }

    @Test
    fun `base64-encoded and CWT-tagged COSE_Sign1 are loaded untagged`() = runTest {
        val cwt = cwt()
        val tagged = byteArrayOf(0xD8.toByte(), 0x3D) + cwt
        val store = store(
            WrprcConfiguration("Base64url", cwtPath = file("a.cwt", Base64.UrlSafe.encode(cwt))),
            WrprcConfiguration("Base64", cwtPath = file("b.cwt", Base64.Default.encode(cwt) + "\n")),
            WrprcConfiguration("Tagged", cwtPath = file("c.cwt", tagged)),
        )

        store.registrationCertificates.values.forEach {
            assertNull(it.jwt)
            assertArrayEquals(cwt, it.cwt, it.label)
        }
    }

    @Test
    fun `CWT in path is used as CWT, for compatibility`() = runTest {
        val cwt = cwt()
        val store = store(WrprcConfiguration("Legacy", path = file("rc.txt", Base64.UrlSafe.encode(cwt))))

        val wrprc = store.registrationCertificates.getValue(0)
        assertNull(wrprc.jwt)
        assertArrayEquals(cwt, wrprc.cwt)
    }

    @Test
    fun `invalid configurations are rejected`() = runTest {
        val cwt = file("rc.cwt", cwt())
        assertThrows<IllegalArgumentException> { store(WrprcConfiguration("None")) }
        assertThrows<IllegalArgumentException> {
            store(WrprcConfiguration("Not a CWT", cwtPath = file("rc.jws", jwt)))
        }
        assertThrows<IllegalArgumentException> {
            store(WrprcConfiguration("Two CWTs", path = cwt, cwtPath = cwt))
        }
    }

    private suspend fun cwt(): ByteArray = coseCompliantSerializer.encodeToByteArray(
        SignCose<ByteArray>(EphemeralKeyWithSelfSignedCert())(null, null, byteArrayOf(1, 2, 3), ByteArraySerializer())
            .getOrThrow()
    )

    private fun file(name: String, content: String): URI = file(name, content.encodeToByteArray())

    private fun file(name: String, content: ByteArray): URI = File(dir, name).apply { writeBytes(content) }.toURI()

    private fun store(vararg rc: WrprcConfiguration) = WrpCertificateStore(
        AppConfigurationProperties(wrp = WrpConfigurationProperties(rc = rc.toList())),
        DefaultResourceLoader(),
    )
}
