package at.asit.wallet.relyingparty

import at.asitplus.catching
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.subjectAltName_2_5_29_17
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@SpringBootTest
@AutoConfigureMockMvc
class ProcessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var transactionStore: TransactionStore

    @MockitoBean
    private lateinit var wrpCertificateStore: WrpCertificateStore

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
        }.andExpect {
            status { isOk() }
        }.andReturn()

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
        assertTrue(decodedQrUrl.contains("response_uri=http://localhost:8080/transaction/result/${avProfile.id}"))
        assertTrue(decodedQrUrl.contains("client_id=redirect_uri:http://localhost:8080/transaction/result/${avProfile.id}"))
        assertTrue(decodedQrUrl.contains("/transaction/result/${avProfile.id}"))
        assertTrue(!decodedQrUrl.contains("client_metadata="))
        assertTrue(!decodedQrUrl.contains("request_uri="))

        val authnRequest = mockMvc.get("/transaction/get/${avProfile.id}") {
            accept = MediaType.ALL
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val decodedAuthnRequest = URLDecoder.decode(authnRequest, Charsets.UTF_8)
        assertTrue(decodedAuthnRequest.contains("/transaction/result/${avProfile.id}"))
    }

    @Test
    fun `includeWrpac uses wrpac certificate san dns as client id`() = runTest {
        val wrpacDnsName = "wrpac.example.com"
        val wrpacKeyMaterial = createSanDnsKeyMaterial(wrpacDnsName)
        Mockito.`when`(wrpCertificateStore.loadWrpacChain()).thenReturn(listOf(wrpacKeyMaterial.getCertificate()!!))
        Mockito.`when`(wrpCertificateStore.loadWrpacKeyMaterial()).thenReturn(wrpacKeyMaterial)

        val transactionResult = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(
                    includeWrpac = true,
                    presentationMechanism = PresentationMechanismEnum.PresentationExchange,
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

        val transactionResponse = Json.decodeFromString<TransactionResponse>(transactionResult.response.contentAsString)
        val profile = transactionResponse.profiles.first { it.name == DEFAULT_PROFILE }

        val requestObject = mockMvc.get("/transaction/get/${profile.id}") {
            accept = MediaType.valueOf("application/oauth-authz-req+jwt")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val payload = decodeJwtPart(requestObject, 1)
        val clientId = Json.parseToJsonElement(payload).jsonObject["client_id"]?.jsonPrimitive?.content
        assertEquals("x509_san_dns:$wrpacDnsName", clientId)
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
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val transactionResponse =
            Json.decodeFromString<TransactionResponse>(transactionResult.response.contentAsString)

        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(keyMaterial = holderKey)
        val issuer = IssuerAgent(
            statusListBaseUrl = "https://wallet.a-sit.at/m7/credentials/status",
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
                mockMvc.get(data.url).andReturn().response.contentAsString
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
        }.andExpect {
            status { isOk() }
        }

        val user = transactionStore.getApiItem(selectedProfile.id)
        assertNotNull(user)
        assertEquals(givenName, user!!.firstname)
    }

    private fun createSanDnsKeyMaterial(dnsName: String): KeyMaterial {
        val extensions =
            listOf(
                X509CertificateExtension(
                    KnownOIDs.subjectAltName_2_5_29_17, critical = false, Asn1EncapsulatingOctetString(
                        listOf(
                            Asn1.Sequence {
                                +Asn1Primitive(
                                    SubjectAltNameImplicitTags.dNSName, Asn1String.UTF8(dnsName).encodeToTlv().content
                                )
                            })
                    )
                )
            )
        return EphemeralKeyWithSelfSignedCert(extensions = extensions)
    }

    private fun decodeJwtPart(jwt: String, index: Int): String {
        val segment = jwt.split(".").getOrNull(index) ?: error("JWT segment $index missing")
        val padded = segment + "=".repeat((4 - segment.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
    }
}
