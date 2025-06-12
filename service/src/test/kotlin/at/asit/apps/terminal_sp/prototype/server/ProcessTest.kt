package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import at.asitplus.wallet.lib.Initializer.initOpenIdModule
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.lib.openid.OpenId4VpHolder
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
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
    private lateinit var transactionStore: TransactionStore

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            initOpenIdModule()
            Napier.takeLogarithm()
            Napier.base(AntilogSlf4jAdapter)
        }
    }

    @Test
    fun `simple transaction roundtrip, presentation exchange`() = runTest {
        runProcess(PresentationMechanismEnum.PresentationExchange)
    }

    @Test
    fun `simple transaction roundtrip, DCQL`() = runTest {
        runProcess(PresentationMechanismEnum.DCQL)
    }

    private suspend fun runProcess(presentationMechanism: PresentationMechanismEnum) {
        val givenName = uuid4().toString()
        val transactionResult = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(
                    presentationMechanism = presentationMechanism,
                    credentials = listOf(
                        TransactionRequestCredential(
                            credentialType = AtomicAttribute2023.sdJwtType,
                            representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                            attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME),
                        )
                    )
                )
            )
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val transactionResponse =
            Json.decodeFromString<TransactionResponse>(transactionResult.response.contentAsString)

        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(keyMaterial = holderKey)
        val issuer = IssuerAgent(statusListBaseUrl = "https://wallet.a-sit.at/m6/credentials/status")
        holder.storeCredential(
            issuer.issueCredential(
                CredentialToBeIssued.VcSd(
                    claims = listOf(ClaimToBeIssued(AtomicAttribute2023.CLAIM_GIVEN_NAME, givenName)),
                    expiration = Clock.System.now() + 1.minutes,
                    scheme = AtomicAttribute2023,
                    subjectPublicKey = holderKey.publicKey,
                )
            ).getOrThrow().toStoreCredentialInput()
        )
        val wallet = OpenId4VpHolder(
            holder = holder,
            remoteResourceRetriever = { data ->
                mockMvc.get(data.url).andReturn().response.contentAsString
            })
        val firstProfile = transactionResponse.profiles.first()
        val authenticationResponseResult = wallet.createAuthnResponse(firstProfile.remoteWalletUrl).getOrThrow()

        authenticationResponseResult as AuthenticationResponseResult.Post
        mockMvc.post(authenticationResponseResult.url) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            authenticationResponseResult.params.forEach {
                param(it.key, it.value)
            }
        }.andExpect {
            status { isOk() }
        }

        val user = transactionStore.getApiItem(firstProfile.id)
        assertNotNull(user)
        assertEquals(givenName, user!!.firstname)
    }
}