package at.asit.wallet.relyingparty

import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.agent.validation.TokenStatusResolverImpl
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.IdentifierListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class StatusListTokenFetcherTest {

    private val statusListAgent = StatusListAgent()
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
        val resolver = resolverServing(cwt(RevocationList.Kind.STATUS_LIST), contentType)

        assertEquals(TokenStatus.Invalid, resolver(revoked).getOrThrow())
        assertEquals(TokenStatus.Valid, resolver(valid).getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = [MediaTypes.Application.IDENTIFIERLIST_CWT, "application/cbor", ""])
    fun `CWT identifier list detects the revoked mdoc`(contentType: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = true)
        val valid = storeReference(RevocationList.Kind.IDENTIFIER_LIST, revoke = false)
        val resolver = resolverServing(cwt(RevocationList.Kind.IDENTIFIER_LIST), contentType)

        assertEquals(TokenStatus.Invalid, resolver(revoked).getOrThrow())
        assertEquals(TokenStatus.Valid, resolver(valid).getOrThrow())
    }

    @Test
    fun `CWT tagged as CWT is accepted`() = runTest {
        val revoked = storeReference(RevocationList.Kind.STATUS_LIST, revoke = true)
        val resolver = resolverServing(
            byteArrayOf(0xD8.toByte(), 0x3D) + cwt(RevocationList.Kind.STATUS_LIST),
            "application/cwt",
        )

        assertEquals(TokenStatus.Invalid, resolver(revoked).getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = [MediaTypes.Application.STATUSLIST_JWT, "application/jwt", "text/plain", ""])
    fun `JWT status list detects the revoked credential`(contentType: String) = runTest {
        val revoked = storeReference(RevocationList.Kind.STATUS_LIST, revoke = true)
        val jwt = statusListAgent.issueStatusListJwt(timePeriod, RevocationList.Kind.STATUS_LIST).jws.toString()
        val resolver = resolverServing(jwt.encodeToByteArray(), contentType)

        assertEquals(TokenStatus.Invalid, resolver(revoked).getOrThrow())
    }

    @Test
    fun `unavailable status list is rejected`() = runTest {
        val reference = storeReference(RevocationList.Kind.STATUS_LIST, revoke = false)
        val resolver = TokenStatusResolverImpl(
            resolveStatusListToken = StatusListTokenFetcher(HttpClient(MockEngine {
                respond(cwt(RevocationList.Kind.STATUS_LIST), HttpStatusCode.NotFound)
            }))
        )

        assertTrue(resolver(reference).isFailure)
    }

    private suspend fun cwt(kind: RevocationList.Kind): ByteArray =
        coseCompliantSerializer.encodeToByteArray(statusListAgent.issueStatusListCwt(timePeriod, kind))

    private fun resolverServing(body: ByteArray, contentType: String) = TokenStatusResolverImpl(
        resolveStatusListToken = StatusListTokenFetcher(HttpClient(MockEngine {
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
}
