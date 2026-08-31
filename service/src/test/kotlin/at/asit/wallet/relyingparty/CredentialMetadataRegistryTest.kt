package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch

/**
 * Verifies that the registry works against the cached type metadata documents in `src/test/resources` (served by
 * [CachedTypeMetadataConfiguration]) — i.e. that the snapshot is complete, parses, and matches [CredentialCatalog].
 */
@SpringBootTest
@AutoConfigureMockMvc
class CredentialMetadataRegistryTest {

    @Autowired
    private lateinit var registry: RemoteCredentialMetadataRegistry

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `every catalog entry resolves from the cached documents`() = runTest {
        CredentialCatalog.entries.forEach { entry ->
            val resolved = registry.findEntry(entry.identifier, entry.representation)
            assertNotNull(resolved) { "${entry.identifier}: no cached document for ${entry.fileName}" }
            assertEquals(entry.vct, resolved!!.metadata.vct.string) { "${entry.fileName}: vct mismatch" }
        }
    }

    @Test
    fun `login config is generated from the cached documents`() {
        val result = mockMvc.get("/js/login-config.js").andReturn()
        val body = (if (result.request.isAsyncStarted) mockMvc.perform(asyncDispatch(result)).andReturn() else result)
            .response.contentAsString
        CredentialCatalog.entries.forEach { entry ->
            assertTrue(body.contains("\"${entry.identifier}\"")) { "login config misses ${entry.identifier}" }
        }
        assertTrue(!body.contains("\"presentationMechanisms\"")) { "login config exposes a mechanism selector" }
    }
}
