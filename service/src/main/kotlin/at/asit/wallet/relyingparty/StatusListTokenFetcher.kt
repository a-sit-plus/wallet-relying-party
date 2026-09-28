package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.StatusListToken
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock

/**
 * Fetches the status list or identifier list a credential references, as JWT or CWT.
 *
 * Servers do not always label the response with the media types of the Token Status List resp. ISO/IEC 18013-5,
 * so for any other content type a CWT is detected from the body, falling back to a JWT.
 */
class StatusListTokenFetcher(
    private val httpClient: HttpClient,
    private val clock: Clock = Clock.System,
) : StatusListTokenResolver {

    override suspend fun invoke(statusListUrl: UniformResourceIdentifier): StatusListToken {
        Napier.i("Resolving token status from $statusListUrl")
        val response = httpClient.get(statusListUrl.string) {
            header(HttpHeaders.Accept, ACCEPTED_MEDIA_TYPES.joinToString(", "))
        }
        check(response.status.isSuccess()) { "Status list $statusListUrl responded with ${response.status}" }
        val body = response.body<ByteArray>()
        return when (response.contentType()?.withoutParameters()?.toString()?.lowercase()) {
            MediaTypes.Application.STATUSLIST_JWT -> body.toJwt()
            MediaTypes.Application.STATUSLIST_CWT,
            MediaTypes.Application.IDENTIFIERLIST_CWT -> body.toCwt()

            else -> runCatching { body.toCwt() }.getOrNull() ?: body.toJwt()
        }
    }

    private fun ByteArray.toJwt() = StatusListJwt(
        JwsCompactTyped<StatusListTokenPayload>(decodeToString().trim()),
        clock.now(),
    )

    /** A CWT may be tagged (RFC 8392, tag 61) around the tagged COSE_Sign1. */
    private fun ByteArray.toCwt() = StatusListCwt(
        coseCompliantSerializer.decodeFromByteArray<CoseSigned<ByteArray>>(
            if (size > 2 && this[0] == CWT_TAG[0] && this[1] == CWT_TAG[1]) copyOfRange(2, size) else this
        ),
        clock.now(),
    )

    private companion object {
        val ACCEPTED_MEDIA_TYPES = listOf(
            MediaTypes.Application.STATUSLIST_JWT,
            MediaTypes.Application.STATUSLIST_CWT,
            MediaTypes.Application.IDENTIFIERLIST_CWT,
        )
        val CWT_TAG = byteArrayOf(0xD8.toByte(), 0x3D)
    }
}
