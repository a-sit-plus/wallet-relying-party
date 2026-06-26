package at.asit.wallet.relyingparty

import at.asitplus.catching
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.lib.openid.CredentialPresentationRequestBuilder
import at.asitplus.wallet.lib.openid.OpenId4VpHolder
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import java.net.URLDecoder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@SpringBootTest
@AutoConfigureMockMvc
class ProcessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var transactionStore: TransactionStore

    @Autowired
    private lateinit var configuration: AppConfigurationProperties

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
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

    @Test
    fun `AV transaction roundtrip uses transaction scoped redirect uri`() = runTest {
        val requestBuilder = CredentialPresentationRequestBuilder(
            listOf(
                TransactionRequestCredential(
                    credentialType = AtomicAttribute2023.sdJwtType,
                    representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                    attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME),
                )
            ).map {
                it.toRequestOptionsCredential()
            }
        )
        val transactionResult = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(
                    presentationMechanism = PresentationMechanismEnum.DCQL,
                    presentationDefinition = requestBuilder.toPresentationExchangeRequest().presentationDefinition,
                    dcqlQuery = requestBuilder.toDCQLRequest()?.dcqlQuery,
                    deviceRequest = catching {
                        requestBuilder.toIso180137AnnexCDeviceRequest()
                    }.getOrNull(),
                )
            )
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()

        val transactionResponse =
            Json.decodeFromString<TransactionResponse>(transactionResult.response.contentAsString)
        val avProfile = transactionResponse.profiles.first { it.name == "AV" }
        val decodedQrUrl = URLDecoder.decode(avProfile.url, Charsets.UTF_8)
        assertTrue(decodedQrUrl.startsWith("av://?"))
        assertTrue(!decodedQrUrl.startsWith("av://localhost"))
        assertTrue(decodedQrUrl.contains("dcql_query="))
        assertTrue(decodedQrUrl.contains("response_mode=direct_post"))
        assertTrue(decodedQrUrl.contains("nonce="))
        assertTrue(decodedQrUrl.contains("state=${avProfile.id}"))
        val resultUrl = configuration.publicContext.appendPath("${Paths.Transaction.ResultUrl}/${avProfile.id}")
        assertTrue(decodedQrUrl.contains("response_uri=$resultUrl"))
        assertTrue(decodedQrUrl.contains("client_id=redirect_uri:$resultUrl"))
        assertTrue(decodedQrUrl.contains("/transaction/result/${avProfile.id}"))
        assertTrue(!decodedQrUrl.contains("client_metadata="))
        assertTrue(!decodedQrUrl.contains("request_uri="))

        val authnRequest = mockMvc.get("/transaction/get/${avProfile.id}") {
            accept = MediaType.ALL
        }.andReturn().awaitAsync().response.contentAsString

        val decodedAuthnRequest = URLDecoder.decode(authnRequest, Charsets.UTF_8)
        assertTrue(decodedAuthnRequest.contains("/transaction/result/${avProfile.id}"))
    }

    private suspend fun runProcess(
        presentationMechanism: PresentationMechanismEnum,
        profileName: String? = null,
    ) {
        val givenName = uuid4().toString()
        val requestBuilder = CredentialPresentationRequestBuilder(
            listOf(
                TransactionRequestCredential(
                    credentialType = AtomicAttribute2023.sdJwtType,
                    representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                    attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME),
                )
            ).map {
                it.toRequestOptionsCredential()
            }
        )
        val transactionResult = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(
                    presentationMechanism = presentationMechanism,
                    presentationDefinition = requestBuilder.toPresentationExchangeRequest().presentationDefinition,
                    dcqlQuery = requestBuilder.toDCQLRequest()?.dcqlQuery,
                    deviceRequest = catching {
                        requestBuilder.toIso180137AnnexCDeviceRequest()
                    }.getOrNull(),
                )
            )
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()

        val transactionResponse =
            Json.decodeFromString<TransactionResponse>(transactionResult.response.contentAsString)

        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(keyMaterial = holderKey)
        val issuer = IssuerAgent(
            statusListBaseUrl = "https://wallet-issuer.a-sit.plus/credentials/status",
            identifier = UniformResourceIdentifier("https://example.com"),
        )
        holder.storeCredential(
            issuer.issueCredential(
                CredentialToBeIssued.VcSd(
                    claims = listOf(ClaimToBeIssued(AtomicAttribute2023.CLAIM_GIVEN_NAME, givenName)),
                    expiration = Clock.System.now() + 1.minutes,
                    scheme = AtomicAttribute2023,
                    subjectPublicKey = holderKey.publicKey,
                    userInfo = OidcUserInfoExtended.fromJsonObject(buildJsonObject { put("sub", "foo") }).getOrThrow()
                )
            ).getOrThrow().toStoreCredentialInput()
        )
        val wallet = OpenId4VpHolder(
            holder = holder,
            remoteResourceRetriever = { data ->
                mockMvc.get(data.url).andReturn().awaitAsync().response.contentAsString
            })
        val selectedProfile = profileName?.let { expectedProfileName ->
            transactionResponse.profiles.first { it.name == expectedProfileName }
        } ?: transactionResponse.profiles.first()
        val authenticationResponseResult = wallet.createAuthnResponse(selectedProfile.url).getOrThrow()

        authenticationResponseResult as AuthenticationResponseResult.Post
        mockMvc.post(authenticationResponseResult.url) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            authenticationResponseResult.params.forEach {
                param(it.key, it.value)
            }
        }.andReturn().awaitAsync()

        val user = transactionStore.getApiItem(selectedProfile.id)
        assertNotNull(user)
        assertEquals(givenName, user!!.firstname)
    }

    private fun MvcResult.awaitAsync(): MvcResult =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn() else this
}
