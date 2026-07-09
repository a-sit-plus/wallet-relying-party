package at.asit.wallet.relyingparty

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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

    @Test
    fun `DC API is offered for every profile with a dcApiUrl`() = runTest {
        val response = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT)
        assertEquals(
            setOf("HAIPd05", "AV", "MDOCd23", "EUDIW"),
            response.profiles.map { it.name }.toSet(),
        )
        response.profiles.forEach {
            assertTrue(it.supportedOptions.contains(SupportedOptions.DC_API), "profile ${it.name} missing DC_API")
            assertNotNull(it.dcApiUrl, "profile ${it.name} missing dcApiUrl")
        }
    }

    @Test
    fun `DC API signed OpenID4VP works for a formerly device-only profile`() = runTest {
        // EUDIW previously had no DC API at all; universal DC API must now produce a signed request for it.
        val eudiw = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first { it.name == "EUDIW" }
        val body = dcApiBody(eudiw.id, "?oid4vpMode=SIGNED&isoMdoc=false&encrypt=true")
        assertTrue(body.contains("openid4vp-v1-signed"), body)
    }

    @Test
    fun `DC API unsigned OpenID4VP is plaintext when encrypt is off, encrypted when on`() = runTest {
        // Unsigned exercises the x509_san_dns scheme (EUDIW), where the request body is inspectable (not a JWS).
        val eudiw = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first { it.name == "EUDIW" }

        val plaintext = dcApiBody(eudiw.id, "?oid4vpMode=UNSIGNED&isoMdoc=false&encrypt=false")
        assertTrue(plaintext.contains("openid4vp-v1-unsigned"), plaintext)
        assertTrue(plaintext.contains("dcql_query"), plaintext)
        assertTrue(plaintext.contains("expected_origins"), plaintext)
        assertTrue(plaintext.contains("\"dc_api\""), plaintext)
        assertFalse(plaintext.contains("dc_api.jwt"), plaintext)

        val encrypted = dcApiBody(eudiw.id, "?oid4vpMode=UNSIGNED&isoMdoc=false&encrypt=true")
        assertTrue(encrypted.contains("dc_api.jwt"), encrypted)
    }

    @Test
    fun `DC API offers ISO Annex C, with and without OpenID4VP encryption`() = runTest {
        val mdoc = createTransaction(ConstantIndex.CredentialRepresentation.ISO_MDOC).profiles.first { it.name == "MDOCd23" }

        // ISO alone, encrypt off (R1: ISO is HPKE-encrypted internally regardless of responseMode).
        assertTrue(dcApiBody(mdoc.id, "?oid4vpMode=NONE&isoMdoc=true&encrypt=false").contains("org-iso-mdoc"))

        // Signed OpenID4VP + ISO in one call (the safe multi-protocol combination).
        val combined = dcApiBody(mdoc.id, "?oid4vpMode=SIGNED&isoMdoc=true&encrypt=true")
        assertTrue(combined.contains("openid4vp-v1-signed"), combined)
        assertTrue(combined.contains("org-iso-mdoc"), combined)
    }

    @Test
    fun `DC API rejects an empty selection`() = runTest {
        val p = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        assertEquals(400, dcApiResult(p.id, "?oid4vpMode=NONE&isoMdoc=false").response.status)
    }

    @Test
    fun `DC API rejects an unknown OpenID4VP mode`() = runTest {
        val p = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        assertEquals(400, dcApiResult(p.id, "?oid4vpMode=BOGUS").response.status)
    }

    private suspend fun createTransaction(
        representation: ConstantIndex.CredentialRepresentation,
    ): TransactionResponse {
        val requestBuilder = CredentialPresentationRequestBuilder(
            listOf(
                TransactionRequestCredential(
                    credentialType = if (representation == ConstantIndex.CredentialRepresentation.ISO_MDOC)
                        AtomicAttribute2023.isoDocType else AtomicAttribute2023.sdJwtType,
                    representation = representation.name,
                    attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME),
                )
            ).map { it.toRequestOptionsCredential() }
        )
        val result = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(
                    presentationMechanism = PresentationMechanismEnum.DCQL,
                    dcqlQuery = requestBuilder.toDCQLRequest()?.dcqlQuery,
                )
            )
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()
        return Json.decodeFromString(result.response.contentAsString)
    }

    private fun dcApiResult(id: String, query: String): MvcResult =
        mockMvc.get("/transaction/get/dcapi/$id$query") { accept = MediaType.ALL }.andReturn().awaitAsync()

    private fun dcApiBody(id: String, query: String): String = dcApiResult(id, query).let {
        assertEquals(200, it.response.status, it.response.contentAsString)
        it.response.contentAsString
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
        assertEquals(givenName, user!!.credentials.firstNotNullOfOrNull { it.getClaim(AtomicAttribute2023.CLAIM_GIVEN_NAME) })
    }

    private fun MvcResult.awaitAsync(): MvcResult =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn() else this
}
