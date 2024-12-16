package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import at.asitplus.wallet.lib.Initializer.initOpenIdModule
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.oidc.AuthenticationResponseResult
import at.asitplus.wallet.lib.oidc.OidcSiopWallet
import com.benasher44.uuid.uuid4
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.time.Duration.Companion.minutes

@SpringBootTest
@AutoConfigureMockMvc
class ProcessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionStore: TransactionStore

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            initOpenIdModule()
            Napier.takeLogarithm()
            Napier.base(AntilogSlf4jAdapter())
        }
    }

    @Test
    fun `simple transaction roundtrip`() = runTest {
        val givenName = uuid4().toString()
        val transactionResult = mockMvc.post("/transaction/create") {
            content = objectMapper.writeValueAsString(
                TransactionRequest(
                    credentialType = AtomicAttribute2023.sdJwtType,
                    representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                    attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME)
                )
            )
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val transactionResponse = objectMapper.readValue<TransactionResponse>(
            transactionResult.response.contentAsString,
            TransactionResponse::class.java
        )

        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(keyMaterial = holderKey)
        holder.storeCredential(
            IssuerAgent().issueCredential(
                CredentialToBeIssued.VcSd(
                    claims = listOf(ClaimToBeIssued(AtomicAttribute2023.CLAIM_GIVEN_NAME, givenName)),
                    expiration = Clock.System.now() + 1.minutes,
                    scheme = AtomicAttribute2023,
                    subjectPublicKey = holderKey.publicKey,
                )
            ).getOrThrow().toStoreCredentialInput()
        )
        val wallet = OidcSiopWallet(
            holder = holder,
            remoteResourceRetriever = { url ->
                mockMvc.get(url).andReturn().response.contentAsString
            })
        val authenticationResponseResult = wallet.createAuthnResponse(transactionResponse.remoteWalletUrl).getOrThrow()

        authenticationResponseResult as AuthenticationResponseResult.Post
        mockMvc.post(authenticationResponseResult.url) {
            content = StringBuilder().apply {
                authenticationResponseResult.params.forEach { k, v -> append("&$k=$v") }
            }
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }.andExpect {
            status { isOk() }
        }

        val user = transactionStore.getApiItem(transactionResponse.id)
        assertNotNull(user)
        assertEquals(givenName, user!!.firstname)
    }
}