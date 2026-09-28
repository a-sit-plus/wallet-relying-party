package at.asit.wallet.relyingparty

import at.asitplus.dcapi.request.verifier.CredentialRequestOptions
import at.asitplus.dcapi.request.verifier.DigitalCredentialGetRequest
import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.RequestParametersFrom
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsGeneral
import at.asitplus.signum.indispensable.josef.JwsGeneralTyped
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.typed
import at.asitplus.signum.indispensable.pki.leaf
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.lib.openid.CredentialPresentationRequestBuilder
import at.asitplus.wallet.lib.openid.OpenId4VpHolder
import at.asitplus.wallet.lib.openid.VerifierSignature
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.mockito.Mockito
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.random.Random
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

    @MockitoBean
    private lateinit var wrpCertificateStore: WrpCertificateStore

    companion object {
        private const val TEST_WRPRC = "test-registration-certificate"

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Napier.takeLogarithm()
            Napier.base(AntilogSlf4jAdapter)
        }
    }

    @Test
    fun `simple transaction roundtrip, DCQL`() = runTest {
        runProcess()
    }

    @Test
    fun `selected WRPAC and WRPRC decrypt wallet response`() = runTest {
        val wrpacKey = EphemeralKeyWithSelfSignedCert()
        Mockito.`when`(wrpCertificateStore.accessCertificates).thenReturn(
            mapOf(0 to AccessCertificateData("Selected", wrpacKey, listOf(wrpacKey.getCertificate()!!)))
        )
        Mockito.`when`(wrpCertificateStore.registrationCertificates).thenReturn(
            mapOf(0 to (WrprcConfiguration("Selected", URI("file:/unused.pem")) to wrpacKey.getCertificate()!!.encodeToPEM().getOrThrow()))
        )
        runProcess(selectedWrpacId = 0, selectedWrprcId = 0, profileName = "HAIPd05")
    }

    @Test
    fun `credential query builder returns DCQL only`() = runTest {
        val result = mockMvc.post(Paths.Utilities.BuildCredentialQueriesUrl) {
            content = Json.encodeToString(
                listOf(
                    TransactionRequestCredential(
                        credentialType = AtomicAttribute2023.sdJwtType,
                        representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                        attributes = listOf(AtomicAttribute2023.CLAIM_GIVEN_NAME),
                    )
                )
            )
            contentType = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()

        assertEquals(200, result.response.status)
        assertTrue(result.response.contentAsString.contains("\"dcqlQuery\""))
        assertFalse(result.response.contentAsString.contains("presentationDefinition"))
    }

    @Test
    fun `transaction creation requires DCQL`() = runTest {
        val dcqlQuery = CredentialPresentationRequestBuilder(
            RequestOptionsCredential(AtomicAttribute2023)
        ).toDCQLRequest()!!.dcqlQuery

        val missingDcql = mockMvc.post(Paths.Transaction.CreateUrl) {
            content = "{}"
            contentType = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()
        assertEquals(400, missingDcql.response.status)

        val dcqlOnly = mockMvc.post(Paths.Transaction.CreateUrl) {
            content = Json.encodeToString(TransactionRequest(dcqlQuery = dcqlQuery))
            contentType = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()
        assertEquals(200, dcqlOnly.response.status)
    }

    @Test
    fun `mdoc transaction roundtrip, DCQL`() = runTest {
        runProcess(
            representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
            profileName = "MDOCd23",
        )
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
                    dcqlQuery = requestBuilder.toDCQLRequest()!!.dcqlQuery,
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
        assertFalse(decodedQrUrl.contains("presentation_definition="))
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
        val eudiw =
            createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first { it.name == "EUDIW" }
        val body = dcApiBody(eudiw.id, "?oid4vpMode=SIGNED&isoMdoc=false&encrypt=true")
        assertTrue(body.contains("openid4vp-v1-signed"), body)
        val request = joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(body)
            .digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpSigned
        val typed = request.data.request.typed<AuthenticationRequestParameters, JwsCompact>()
        assertNull(typed.payload.responseUrl)
    }

    @Test
    fun `DC API multisigned OpenID4VP carries distinct protected verifier identities over one payload`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT)
            .profiles.first { it.name == "EUDIW" }
        assertEquals(listOf("dns-verifier", "certificate-hash-verifier"), profile.dcApiSigners.map { it.id })

        val body = dcApiBody(
            profile.id,
            "?oid4vpMode=MULTISIGNED&isoMdoc=false&encrypt=true" +
                    "&signerId=dns-verifier&signerId=certificate-hash-verifier",
        )
        val request = joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(body)
            .digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpMultiSigned
        val typed = request.data.request.typed<AuthenticationRequestParameters, JwsGeneral>()

        assertNull(typed.payload.clientId)
        assertNull(typed.payload.verifierInfo)
        assertNull(typed.payload.redirectUrl)
        assertNull(typed.payload.responseUrl)
        assertEquals(2, typed.jws.signatureElements.size)
        assertEquals(2, typed.jws.jwsHeaders.mapNotNull { it.clientId }.distinct().size)
    }

    @Test
    fun `DC API multisigned OpenID4VP can forge a selected signature, which wallets report as invalid`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT)
            .profiles.first { it.name == "EUDIW" }
        val typed = multiSignedRequest(
            profile.id,
            "&signerId=dns-verifier&signerId=certificate-hash-verifier&forgedSignerId=dns-verifier",
        )
        assertEquals(2, typed.jws.signatureElements.size)

        val preparationState = holderForDcApi().startAuthorizationResponsePreparation(typed.asDcApiRequest())
            .getOrThrow()

        val signatures = preparationState.verifierSignatures
        assertNotNull(signatures)
        assertEquals(typed.jws.jwsHeaders.map { it.clientId }, signatures!!.map { it.clientId })
        assertEquals(
            listOf(VerifierSignature.Status.INVALID, VerifierSignature.Status.AUTHENTICATED),
            signatures.map { it.status },
        )
    }

    @Test
    fun `DC API multisigned OpenID4VP with every signature forged is rejected with invalid_request`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        val typed = multiSignedRequest(
            profile.id,
            "&signerId=dns-verifier&signerId=certificate-hash-verifier" +
                    "&forgedSignerId=dns-verifier&forgedSignerId=certificate-hash-verifier",
        )

        val failure = holderForDcApi().startAuthorizationResponsePreparation(typed.asDcApiRequest()).exceptionOrNull()

        assertInstanceOf(OAuth2Exception.InvalidRequest::class.java, failure)
        typed.jws.jwsHeaders.forEachIndexed { index, header ->
            assertTrue(failure!!.message!!.contains("signature $index (${header.clientId}) INVALID"), failure.message)
        }
    }

    @Test
    fun `DC API signed OpenID4VP with a forged signature is rejected with invalid_request`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        val body = dcApiBody(
            profile.id,
            "?oid4vpMode=SIGNED&isoMdoc=false&encrypt=true&signerId=certificate-hash-verifier" +
                    "&forgedSignerId=certificate-hash-verifier",
        )
        val typed = (joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(body)
            .digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpSigned)
            .data.request.typed<AuthenticationRequestParameters, JwsCompact>()

        val failure = holderForDcApi().startAuthorizationResponsePreparation(
            RequestParametersFrom.OpenId4VpDcApiSigned(
                jwsTyped = typed,
                credentialIds = listOf("1"),
                callingPackageName = "com.example.browser",
                callingOrigin = typed.payload.expectedOrigins!!.single(),
            )
        ).exceptionOrNull()

        assertInstanceOf(OAuth2Exception.InvalidRequest::class.java, failure)
        assertTrue(failure!!.message!!.contains("signature not verified"), failure.message)
    }

    @Test
    fun `DC API rejects forging outside signed modes or for a signer that is not selected`() = runTest {
        listOf(
            "?oid4vpMode=UNSIGNED&forgedSignerId=dns-verifier",
            "?oid4vpMode=MULTISIGNED&signerId=dns-verifier&signerId=certificate-hash-verifier&forgedSignerId=unknown",
            "?oid4vpMode=SIGNED&signerId=dns-verifier&forgedSignerId=certificate-hash-verifier",
        ).forEach { query ->
            val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
            assertEquals(400, dcApiResult(profile.id, query).response.status, query)
        }
    }

    private fun multiSignedRequest(transactionId: String, signerQuery: String) =
        (joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(
            dcApiBody(transactionId, "?oid4vpMode=MULTISIGNED&isoMdoc=false&encrypt=true$signerQuery")
        ).digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpMultiSigned)
            .data.request.typed<AuthenticationRequestParameters, JwsGeneral>()

    private fun JwsGeneralTyped<AuthenticationRequestParameters>.asDcApiRequest() =
        RequestParametersFrom.OpenId4VpDcApiMultiSigned(
            jwsTyped = this,
            credentialIds = listOf("1"),
            callingPackageName = "com.example.browser",
            callingOrigin = payload.expectedOrigins!!.single(),
        )

    // the test server's origin is plain http
    private fun holderForDcApi() = OpenId4VpHolder(allowedDcApiOriginSchemes = { setOf("http", "https") })

    @Test
    fun `DC API offers the WRPAC as a signer, carrying its full chain and registration certificate`() = runTest {
        stubWrpac()
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT)
            .profiles.first { it.name == "EUDIW" }
        assertEquals(
            listOf(false, false, true),
            profile.dcApiSigners.map { it.wrpac },
        )
        assertEquals(listOf(RegistrationCertificateDescriptor(0, "Test WRPRC")), profile.dcApiRegistrationCertificates)

        val typed = multiSignedRequest(
            profile.id,
            "&signerId=wrpac-0&signerId=dns-verifier&signerVerifierInfo=wrpac-0:0",
        )
        val wrpacHeader = typed.jws.jwsHeaders[0]
        assertTrue(wrpacHeader.clientId!!.startsWith("x509_hash:"), wrpacHeader.clientId)
        // leaf and registrar certificate, so that wallets can evaluate the chain against their trust lists
        assertEquals(2, wrpacHeader.certificateChain!!.size)

        val signatures = holderForDcApi().startAuthorizationResponsePreparation(typed.asDcApiRequest())
            .getOrThrow().verifierSignatures!!
        assertEquals(listOf(true, true), signatures.map { it.authenticated })
        assertEquals(TEST_WRPRC, signatures[0].verifierInfo!!.single().data)
        assertNull(signatures[1].verifierInfo)
    }

    @Test
    fun `DC API signs with the WRPAC and registration certificate selected for the transaction`() =
        runTest {
            stubWrpac()
            val profile = createTransaction(
                ConstantIndex.CredentialRepresentation.SD_JWT,
                selectedWrpacId = 0,
                selectedWrprcId = 0,
            ).profiles.first { it.name == "EUDIW" }

            val body = dcApiBody(profile.id, "?oid4vpMode=SIGNED&isoMdoc=false&encrypt=true")
            val typed = (joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(body)
                .digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpSigned)
                .data.request.typed<AuthenticationRequestParameters, JwsCompact>()

            assertTrue(typed.payload.clientId!!.startsWith("x509_hash:"), typed.payload.clientId)
            assertEquals(2, typed.jws.jwsHeader.certificateChain!!.size)
            assertEquals(TEST_WRPRC, typed.payload.verifierInfo!!.single().data)
        }

    @Test
    fun `DC API rejects a transaction selecting a WRPAC that is not configured`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT, selectedWrpacId = 0)
            .profiles.first()

        assertEquals(400, dcApiResult(profile.id, "?oid4vpMode=SIGNED&isoMdoc=false").response.status)
    }

    @Test
    fun `DC API only binds a registration certificate to another signer as a mismatch test`() = runTest {
        stubWrpac()
        val mismatched = "&signerId=wrpac-0&signerId=dns-verifier&signerVerifierInfo=dns-verifier:0"
        val rejected = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        assertEquals(
            400,
            dcApiResult(rejected.id, "?oid4vpMode=MULTISIGNED&isoMdoc=false$mismatched").response.status,
        )

        val allowed = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        val typed = multiSignedRequest(allowed.id, "$mismatched&allowMismatchedVerifierInfo=true")
        assertNotNull(typed.jws.jwsHeaders[1].verifierInfo)
        assertNull(typed.jws.jwsHeaders[0].verifierInfo)
    }

    private fun stubWrpac() {
        val material = wrpacKeyStoreMaterial()
        Mockito.`when`(wrpCertificateStore.accessCertificates).thenReturn(
            mapOf(0 to AccessCertificateData("Test WRPAC", material, material.getCertificateChain()))
        )
        Mockito.`when`(wrpCertificateStore.registrationCertificates).thenReturn(
            mapOf(0 to (WrprcConfiguration("Test WRPRC", URI("classpath:test-wrprc.jws")) to TEST_WRPRC))
        )
    }

    /** A WRPAC issued by a test registrar, i.e. a key store holding the leaf with its two-certificate chain. */
    private fun wrpacKeyStoreMaterial(): KeyStoreMaterial {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val registrarKeys = generator.generateKeyPair()
        val wrpKeys = generator.generateKeyPair()
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 24 * 60 * 60 * 1000)
        fun certificate(subject: String, publicKey: PublicKey, ca: Boolean) = JcaX509CertificateConverter()
            .getCertificate(
                JcaX509v3CertificateBuilder(
                    X500Name("CN=Test Registrar"),
                    BigInteger.valueOf(System.nanoTime()),
                    notBefore,
                    notAfter,
                    X500Name(subject),
                    publicKey,
                ).addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
                    .build(JcaContentSignerBuilder("SHA256withECDSA").build(registrarKeys.private))
            )
        val registrar = certificate("CN=Test Registrar", registrarKeys.public, ca = true)
        val wrpac = certificate("CN=Test Relying Party", wrpKeys.public, ca = false)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("wrpac", wrpKeys.private, charArrayOf(), arrayOf(wrpac, registrar))
        }
        return KeyStoreMaterial(keyStore, "wrpac", charArrayOf(), certAlias = "wrpac")
    }

    @Test
    fun `DC API enforces signer cardinality for signed and multisigned modes`() = runTest {
        val profile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        assertEquals(
            400,
            dcApiResult(profile.id, "?oid4vpMode=MULTISIGNED&signerId=dns-verifier").response.status,
        )

        val secondProfile = createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first()
        assertEquals(
            400,
            dcApiResult(
                secondProfile.id,
                "?oid4vpMode=SIGNED&signerId=dns-verifier&signerId=certificate-hash-verifier",
            ).response.status,
        )
    }

    @Test
    fun `DC API unsigned OpenID4VP is plaintext when encrypt is off, encrypted when on`() = runTest {
        // Unsigned exercises the x509_san_dns scheme (EUDIW), where the request body is inspectable (not a JWS).
        val eudiw =
            createTransaction(ConstantIndex.CredentialRepresentation.SD_JWT).profiles.first { it.name == "EUDIW" }

        val plaintext = dcApiBody(eudiw.id, "?oid4vpMode=UNSIGNED&isoMdoc=false&encrypt=false")
        assertTrue(plaintext.contains("openid4vp-v1-unsigned"), plaintext)
        assertTrue(plaintext.contains("dcql_query"), plaintext)
        assertFalse(plaintext.contains("presentation_definition"), plaintext)
        assertTrue(plaintext.contains("expected_origins"), plaintext)
        assertTrue(plaintext.contains("\"dc_api\""), plaintext)
        assertFalse(plaintext.contains("dc_api.jwt"), plaintext)
        val request = joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(plaintext)
            .digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpUnsigned
        assertNull(request.data.responseUrl)

        val encrypted = dcApiBody(eudiw.id, "?oid4vpMode=UNSIGNED&isoMdoc=false&encrypt=true")
        assertTrue(encrypted.contains("dc_api.jwt"), encrypted)
    }

    @Test
    fun `DC API offers ISO Annex C, with and without OpenID4VP encryption`() = runTest {
        val mdoc =
            createTransaction(ConstantIndex.CredentialRepresentation.ISO_MDOC).profiles.first { it.name == "MDOCd23" }

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

    @Test
    fun `DC API request uses the Android app origin supplied at transaction creation`() = runTest {
        val origin = "android:apk-key-hash:0123456789012345678901234567890123456789012"
        val eudiw = createTransaction(
            ConstantIndex.CredentialRepresentation.SD_JWT,
            dcApiOrigin = origin,
        ).profiles.first { it.name == "EUDIW" }

        val body = dcApiBody(eudiw.id, "?oid4vpMode=UNSIGNED&isoMdoc=false&encrypt=false")
        assertTrue(body.contains(origin), body)
    }

    @Test
    fun `transaction creation rejects malformed Android app origin`() = runTest {
        val dcqlQuery = CredentialPresentationRequestBuilder(
            RequestOptionsCredential(AtomicAttribute2023)
        ).toDCQLRequest()!!.dcqlQuery
        val result = mockMvc.post("/transaction/create") {
            content = Json.encodeToString(
                TransactionRequest(dcqlQuery = dcqlQuery, dcApiOrigin = "https://attacker.example")
            )
            contentType = MediaType.APPLICATION_JSON
        }.andReturn().awaitAsync()

        assertEquals(400, result.response.status)
    }

    @Test
    fun `selected WRPAC determines device and DC API request signing certificate`() = runTest {
        val first = EphemeralKeyWithSelfSignedCert()
        val second = EphemeralKeyWithSelfSignedCert()
        Mockito.`when`(wrpCertificateStore.accessCertificates).thenReturn(
            mapOf(
                0 to AccessCertificateData("First", first, listOf(first.getCertificate()!!)),
                1 to AccessCertificateData("Second", second, listOf(second.getCertificate()!!)),
            )
        )
        val profile = createTransaction(
            ConstantIndex.CredentialRepresentation.SD_JWT,
            selectedWrpacId = 1,
        ).profiles.first { it.name == "HAIPd05" }
        val body = mockMvc.get("/transaction/get/${profile.id}") {
            accept = MediaType.ALL
        }.andReturn().awaitAsync().response.contentAsString

        assertEquals(second.getCertificate(), JwsCompact(body).jwsHeader.certificateChain?.leaf)

        val dcApiRequest = joseCompliantSerializer.decodeFromString<CredentialRequestOptions>(
            dcApiBody(profile.id, "?oid4vpMode=SIGNED&isoMdoc=false&encrypt=true")
        ).digital.requests.single() as DigitalCredentialGetRequest.OpenId4VpSigned
        assertEquals(second.getCertificate(), dcApiRequest.data.request.jwsHeader.certificateChain?.leaf)
    }

    @Test
    fun `unknown WRPAC selection is rejected when the request is resolved`() = runTest {
        Mockito.`when`(wrpCertificateStore.accessCertificates).thenReturn(emptyMap())

        val profile = createTransaction(
            ConstantIndex.CredentialRepresentation.SD_JWT,
            selectedWrpacId = 42,
        ).profiles.first { it.name == "HAIPd05" }

        assertEquals(
            400,
            mockMvc.get("/transaction/get/${profile.id}") { accept = MediaType.ALL }
                .andReturn().awaitAsync().response.status,
        )
    }

    private suspend fun createTransaction(
        representation: ConstantIndex.CredentialRepresentation,
        dcApiOrigin: String? = null,
        selectedWrpacId: Int? = null,
        selectedWrprcId: Int? = null,
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
                    dcqlQuery = requestBuilder.toDCQLRequest()!!.dcqlQuery,
                    dcApiOrigin = dcApiOrigin,
                    selectedWrpacId = selectedWrpacId,
                    selectedWrprcId = selectedWrprcId,
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
        representation: ConstantIndex.CredentialRepresentation = ConstantIndex.CredentialRepresentation.SD_JWT,
        profileName: String? = null,
        selectedWrpacId: Int? = null,
        selectedWrprcId: Int? = null,
    ) {
        val givenName = uuid4().toString()
        val transactionResponse = createTransaction(
            representation,
            selectedWrpacId = selectedWrpacId,
            selectedWrprcId = selectedWrprcId,
        )

        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(keyMaterial = holderKey)
        val issuer = IssuerAgent(
            // mdoc `issuerAuth` is verified against the certificate transported in its COSE headers
            keyMaterial = EphemeralKeyWithSelfSignedCert(),
            identifier = UniformResourceIdentifier("https://example.com"),
        )
        holder.storeCredential(
            issuer.issueCredential(
                credentialToBeIssued(representation, givenName, holderKey.publicKey)
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
        assertEquals(
            givenName,
            user!!.credentials.firstNotNullOfOrNull { it.getClaim(AtomicAttribute2023.CLAIM_GIVEN_NAME) })
        assertTrue(user.credentials.all { it.trustState != null })
        if (representation == ConstantIndex.CredentialRepresentation.ISO_MDOC) {
            // the mdoc really was presented as one, and its issuerAuth was verified against the certificate
            // transported in the COSE headers
            val credential = user.credentials.single()
            assertEquals(AtomicAttribute2023.isoDocType, credential.credentialType)
            assertNotNull(credential.issuerCertificate)
        }
    }

    private fun credentialToBeIssued(
        representation: ConstantIndex.CredentialRepresentation,
        givenName: String,
        subjectPublicKey: CryptoPublicKey,
    ): CredentialToBeIssued = when (representation) {
        ConstantIndex.CredentialRepresentation.ISO_MDOC -> CredentialToBeIssued.Iso(
            issuerSignedItems = listOf(
                IssuerSignedItem(
                    digestId = 0U,
                    random = Random.nextBytes(16),
                    elementIdentifier = AtomicAttribute2023.CLAIM_GIVEN_NAME,
                    elementValue = givenName,
                )
            ),
            expiration = Clock.System.now() + 1.minutes,
            scheme = AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = dummyUserInfo(),
        )

        else -> CredentialToBeIssued.VcSd(
            claims = listOf(ClaimToBeIssued(AtomicAttribute2023.CLAIM_GIVEN_NAME, givenName)),
            expiration = Clock.System.now() + 1.minutes,
            scheme = AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = dummyUserInfo(),
        )
    }

    private fun dummyUserInfo() =
        OidcUserInfoExtended.fromJsonObject(buildJsonObject { put("sub", "foo") }).getOrThrow()

    private fun MvcResult.awaitAsync(): MvcResult =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn() else this
}
