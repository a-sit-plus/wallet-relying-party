package at.asit.wallet.relyingparty

import at.asitplus.etsi.ListOfTrustedEntities
import at.asitplus.etsi.TrustListPayload
import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.IdentifierListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.TokenStatusInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.validation.CheckOutcome
import at.asitplus.wallet.lib.validation.CredentialTrustScopes
import at.asitplus.wallet.lib.validation.StatusListTokenChecks
import at.asitplus.wallet.lib.validation.StatusMechanismValidation
import at.asitplus.wallet.lib.validation.StatusPolicy
import at.asitplus.wallet.lib.validation.StatusSignerAnchors
import at.asitplus.wallet.lib.validation.StatusValidator
import at.asitplus.wallet.lib.validation.TrustPolicy
import at.asitplus.wallet.lib.validation.ValidationDecision
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Resolves status lists with [StatusListTokenFetcher] and validates them with [StatusValidator], as the verifiers do:
 * the status list signer is certified by a provider whose certificate the Pub-EAA providers list of a trust list stage
 * publishes as revocation service.
 */
class StatusListTokenFetcherTest {

    private val provider = runBlocking { TestTrust.certificateAuthority(PROVIDER) }
    private val statusListAgent = StatusListAgent(keyMaterial = runBlocking { TestTrust.certifiedBy(provider) })
    private val timePeriod = with(statusListAgent) { Clock.System.now().toTimePeriod() }

    @ParameterizedTest
    @ValueSource(
        strings = [
            MediaTypes.Application.STATUSLIST_CWT,
            "application/cbor",
            "application/octet-stream",
            "",
        ]
    )
    fun `CWT status list detects the revoked mdoc`(contentType: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.STATUS_LIST, revoke = true)
        val valid = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val validator = validatorServing(cwt(RevocationList.Kind.STATUS_LIST), contentType)

        assertEquals(TokenStatus.Invalid, validator.validateMechanism(revoked).observed)
        assertEquals(TokenStatus.Valid, validator.validateMechanism(valid).observed)
    }

    @ParameterizedTest
    @ValueSource(strings = [MediaTypes.Application.IDENTIFIERLIST_CWT, "application/cbor", ""])
    fun `CWT identifier list detects the revoked mdoc`(contentType: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = true)
        val valid = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = false)
        val validator = validatorServing(cwt(RevocationList.Kind.IDENTIFIER_LIST), contentType)

        assertEquals(TokenStatus.Invalid, validator.validateMechanism(revoked).observed)
        assertEquals(TokenStatus.Valid, validator.validateMechanism(valid).observed)
    }

    @Test
    fun `CWT tagged as CWT is accepted`() = runTest {
        val revoked = storeReference(RevocationList.Kind.STATUS_LIST, revoke = true)
        val validator = validatorServing(
            byteArrayOf(0xD8.toByte(), 0x3D) + cwt(RevocationList.Kind.STATUS_LIST),
            "application/cwt",
        )

        assertEquals(TokenStatus.Invalid, validator.validateMechanism(revoked).observed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["D2", "D83DD2"])
    fun `CWT identifier list tagged as COSE_Sign1 detects the revoked mdoc`(tags: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = true)
        val valid = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = false)
        val validator = validatorServing(
            tags.hexToByteArray() + cwt(RevocationList.Kind.IDENTIFIER_LIST),
            MediaTypes.Application.IDENTIFIERLIST_CWT,
        )

        assertEquals(TokenStatus.Invalid, validator.validateMechanism(revoked).observed)
        assertEquals(TokenStatus.Valid, validator.validateMechanism(valid).observed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["text/html", MediaTypes.Application.STATUSLIST_JWT, MediaTypes.Application.STATUSLIST_CWT])
    fun `HTML page served successfully blocks the status, naming its content type`(contentType: String) = runTest {
        val reference = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val validator = validatorServing("<html><body>Not here</body></html>".encodeToByteArray(), contentType)

        val mechanism = validator.validateMechanism(reference)
        assertTrue(mechanism.outcome is CheckOutcome.Blocked, mechanism.toString())
        assertNull(mechanism.observed)
        val retrieval = (mechanism.token.checks as StatusListTokenChecks).retrieval
        assertTrue(retrieval is CheckOutcome.Blocked, retrieval.toString())
        val cause = (retrieval as CheckOutcome.Blocked).cause?.message.orEmpty()
        assertTrue(cause.contains("served as $contentType"), cause)
    }

    @ParameterizedTest
    @ValueSource(strings = [MediaTypes.Application.STATUSLIST_JWT, "application/jwt", "text/plain", ""])
    fun `JWT status list detects the revoked credential`(contentType: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.STATUS_LIST, revoke = true)
        val jwt = statusListAgent.issueStatusListJwt(timePeriod, RevocationList.Kind.STATUS_LIST).jws.toString()
        val validator = validatorServing(jwt.encodeToByteArray(), contentType)

        val mechanism = validator.validateMechanism(revoked)
        assertEquals(TokenStatus.Invalid, mechanism.observed)
        assertTrue(mechanism.outcome is CheckOutcome.Failed, mechanism.toString())
    }

    @Test
    fun `unavailable status list blocks the status`() = runTest {
        val reference = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val validator = StatusValidator(StatusListTokenFetcher(HttpClient(MockEngine {
            respond(cwt(RevocationList.Kind.STATUS_LIST), HttpStatusCode.NotFound)
        })))

        val mechanism = validator.validateMechanism(reference)
        assertTrue(mechanism.outcome is CheckOutcome.Blocked, mechanism.toString())
        assertNull(mechanism.observed)
        val retrieval = (mechanism.token.checks as StatusListTokenChecks).retrieval
        assertTrue(retrieval is CheckOutcome.Blocked, retrieval.toString())
        val cause = (retrieval as CheckOutcome.Blocked).cause?.message.orEmpty()
        assertTrue(cause.contains("404"), cause)
    }

    @Test
    fun `status list signer is trusted through the revocation service of the Pub-EAA list`() = runTest {
        val reference = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val validator = validatorServing(cwt(RevocationList.Kind.STATUS_LIST), MediaTypes.Application.STATUSLIST_CWT)

        val mechanism = validator.validateMechanism(reference)
        assertEquals(ValidationDecision.ACCEPTED, mechanism.token.decision)
        val signerTrust = (mechanism.token.checks as StatusListTokenChecks).signerTrust
        assertEquals(CheckOutcome.Passed, signerTrust.outcome)
        assertTrue(signerTrust.source!!.contains(LoteProfile.EAA.fetchUrl(LoTEStage.ACCEPTANCE)), signerTrust.source)
    }

    @Test
    fun `status list signer is not trusted through an issuance service`() = runTest {
        val reference = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val validator = validatorServing(cwt(RevocationList.Kind.STATUS_LIST), MediaTypes.Application.STATUSLIST_CWT)

        val mechanism = validator.validateMechanism(reference, serviceType = PUB_EAA_ISSUANCE)
        assertTrue(mechanism.outcome is CheckOutcome.Blocked, mechanism.toString())
        assertEquals(ValidationDecision.REJECTED, mechanism.token.decision)
        val signerTrust = (mechanism.token.checks as StatusListTokenChecks).signerTrust
        assertTrue(signerTrust.outcome is CheckOutcome.Failed, signerTrust.toString())
    }

    @Test
    fun `a fetched status list is reused until the maximum cache age`() = runTest {
        val clock = MutableClock()
        val requests = AtomicInteger()
        val body = cwt(RevocationList.Kind.STATUS_LIST)
        val fetcher = StatusListTokenFetcher(countingClient(requests, body), clock, maxCacheAge = 5.minutes)

        val first = fetcher(STATUS_LIST_URI)
        clock.now += 4.minutes
        assertSame(first, fetcher(STATUS_LIST_URI))
        assertEquals(1, requests.get())

        clock.now += 2.minutes
        val refetched = fetcher(STATUS_LIST_URI)
        assertEquals(2, requests.get())
        assertEquals(clock.now, refetched.resolvedAt)
    }

    @Test
    fun `concurrent requests for a status list share one fetch`() = runTest {
        val requests = AtomicInteger()
        val fetcher = StatusListTokenFetcher(
            countingClient(requests, cwt(RevocationList.Kind.STATUS_LIST), delayMillis = 200)
        )

        val tokens = List(5) { async { fetcher(STATUS_LIST_URI) } }.awaitAll()

        assertEquals(1, requests.get())
        assertTrue(tokens.all { it === tokens.first() })
    }

    @Test
    fun `a failed fetch is not cached`() = runTest {
        val requests = AtomicInteger()
        val body = cwt(RevocationList.Kind.STATUS_LIST)
        val fetcher = StatusListTokenFetcher(HttpClient(MockEngine {
            if (requests.incrementAndGet() == 1) respondError(HttpStatusCode.ServiceUnavailable)
            else respond(body, headers = headersOf(HttpHeaders.ContentType, MediaTypes.Application.STATUSLIST_CWT))
        }))

        assertTrue(runCatching { fetcher(STATUS_LIST_URI) }.isFailure)
        fetcher(STATUS_LIST_URI)
        assertEquals(2, requests.get())
    }

    private fun countingClient(requests: AtomicInteger, body: ByteArray, delayMillis: Long = 0) =
        HttpClient(MockEngine {
            requests.incrementAndGet()
            delay(delayMillis)
            respond(body, headers = headersOf(HttpHeaders.ContentType, MediaTypes.Application.STATUSLIST_CWT))
        })

    private class MutableClock(var now: Instant = Clock.System.now()) : Clock {
        override fun now() = now
    }

    /**
     * Validates the status of a credential of [CREDENTIAL_TYPE] referencing [reference], with the status list signer
     * trusted as the production trust scopes do, for a Pub-EAA list on which [provider] offers a [serviceType].
     */
    private suspend fun StatusValidator.validateMechanism(
        reference: RevocationListInfo,
        serviceType: String = PUB_EAA_REVOCATION,
    ): StatusMechanismValidation {
        val pubEaaList = pubEaaList(serviceType)
        return validateMechanism(reference, pubEaaList)
    }

    private suspend fun StatusValidator.validateMechanism(
        reference: RevocationListInfo,
        pubEaaList: ListOfTrustedEntities,
    ): StatusMechanismValidation = validate(
        status = TokenStatusInfo(
            statusList = reference as? StatusListInfo,
            identifierList = reference as? IdentifierListInfo,
        ),
        signerAnchors = StatusSignerAnchors.OfCredentialType(
            CredentialTrustScopes(
                credentialTrustScopes(
                    identifiers = listOf(CREDENTIAL_TYPE),
                    stages = listOf(LoTEStage.ACCEPTANCE),
                    asitRootCertificate = CredentialValidationConfiguration().asitRootCertificate(),
                ) { url ->
                    if (url == LoteProfile.EAA.fetchUrl(LoTEStage.ACCEPTANCE)) listOf(pubEaaList)
                    else emptyList()
                }
            ),
            CREDENTIAL_TYPE,
        ),
        policy = StatusPolicy.ValidateIfPresent(TrustPolicy.RequireAuthorizedSigner),
        timeLeeway = 5.minutes,
        evaluatedAt = Clock.System.now(),
    ).mechanisms.single()

    /** The Pub-EAA providers list, with [provider] offering a service of [serviceType] with its certificate. */
    private suspend fun pubEaaList(serviceType: String): ListOfTrustedEntities {
        val template = checkNotNull(javaClass.getResource("/lote/pub-eaa-providers.json")).readText()
        val certificate = Base64.getEncoder().encodeToString(checkNotNull(provider.getCertificate()).encodeToDer())
        return joseCompliantSerializer.decodeFromString<TrustListPayload>(
            template.replace("\${CERTIFICATE}", certificate).replace("\${SERVICE_TYPE}", serviceType)
        ).loTe
    }

    private suspend fun cwt(kind: RevocationList.Kind): ByteArray =
        coseCompliantSerializer.encodeToByteArray(statusListAgent.issueStatusListCwt(timePeriod, kind))

    private fun validatorServing(body: ByteArray, contentType: String) = StatusValidator(
        StatusListTokenFetcher(HttpClient(MockEngine {
            respond(
                content = body,
                headers = if (contentType.isEmpty()) headersOf()
                else headersOf(HttpHeaders.ContentType, contentType),
            )
        }))
    )

    private suspend fun storeReference(kind: RevocationList.Kind, revoke: Boolean): RevocationListInfo {
        val credential = CredentialToBeIssued.Iso(
            issuerSignedItems = listOf(
                IssuerSignedItem(0U, Random.nextBytes(16), AtomicAttribute2023.CLAIM_GIVEN_NAME, "Susanne")
            ),
            expiration = Clock.System.now() + 1.minutes,
            scheme = AtomicAttribute2023,
            subjectPublicKey = EphemeralKeyWithoutCert().publicKey,
            userInfo = OidcUserInfoExtended.fromJsonObject(buildJsonObject { put("sub", "foo") }).getOrThrow(),
            revocationKind = kind,
        )
        return statusListAgent.provideStatusReference(credential, Clock.System.now()).also {
            if (revoke) when (it) {
                is StatusListInfo ->
                    statusListAgent.revokeCredentialByIndex(timePeriod, it.index)

                is IdentifierListInfo ->
                    statusListAgent.revokeCredentialByIdentifier(timePeriod, it.identifier)
            }
        }
    }

    private companion object {
        val STATUS_LIST_URI = UniformResourceIdentifier("https://status.example.com/statuslists/1")
        const val PROVIDER = "Test EAA Provider"
        const val CREDENTIAL_TYPE = "urn:eudi:ehic:1"
        const val PUB_EAA_REVOCATION = "http://uri.etsi.org/19602/SvcType/PubEAA/Revocation"
        const val PUB_EAA_ISSUANCE = "http://uri.etsi.org/19602/SvcType/PubEAA/Issuance"
    }
}
