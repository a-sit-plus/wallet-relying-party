package at.asit.wallet.relyingparty

import at.asitplus.catching
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import qrcode.QRCode
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Controller
class ApiController(
    @param:Value($$"${app.public-url}")
    private val publicUrl: String,
    private val transactionStore: TransactionStore,
) {
    private val statisticLogger = LoggerFactory.getLogger("statistic")
    private val transactions: MutableMap<String, Transaction> = HashMap()
    private val profiles = VerifierProfiles(publicUrl)
    private val customerSuccessUrl = ServletUriComponentsBuilder.fromUriString(publicUrl)
        .pathSegment("customer-success.html")
        .toUriString()

    data class Transaction(
        val id: String,
        val request: TransactionRequest,
        val profile: Profile,
    )

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<ApiItem> = transactionStore.getApiItems()

    @GetMapping("/api/single/{id}")
    @ResponseBody
    fun apiSingle(
        @PathVariable id: String,
    ): ResponseEntity<ApiItem> =
        transactionStore.getApiItem(id)?.let {
            Napier.i("/api/single/$id returns $it")
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(it)
        } ?: ResponseEntity.notFound().build()

    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(
        @RequestBody id: String,
    ): ResponseEntity<ApiItem> =
        transactionStore.removeApiItem(id).let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @OptIn(ExperimentalUuidApi::class)
    @PostMapping("/transaction/create", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionCreate(
        @RequestBody request: TransactionRequest,
    ): ResponseEntity<TransactionResponse> = run {
        Napier.i("/transaction/create called with $request")
        val profiles = profiles.knownProfiles.map {
            val transactionId = Uuid.random().toString()
            val transactionUrl = buildTransactionUrl(request, transactionId, it)
            val qrCodeUrl = it.buildQrCodeUrl(transactionUrl)
            val qrCodeBytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            TransactionProfile(
                id = transactionId,
                name = it.name,
                description = it.description,
                label = it.label,
                prefix = it.urlPrefix,
                png = qrCodeBytes.toDataUrl(),
                url = qrCodeUrl,
            )
        }
        val response = TransactionResponse(profiles)
        Napier.i("/transaction/create returns $response")
        ResponseEntity.ok().body(response)
    }

    private fun ByteArray.toDataUrl(): String = "data:image/png;base64," + encodeToString(Base64())

    @GetMapping("/transaction/get/{id}")
    @ResponseBody
    fun transactionGet(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> = runBlocking {
        Napier.i("/transaction/get/$id called")
        statisticLogger.info("$id get (${request.getHeader(HttpHeaders.USER_AGENT)})")
        MDC.put(MDC_REQUEST_ID, id)
        val transaction = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/$id returns NOT_FOUND") }

        catching {
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/" + JwsContentTypeConstants.OAUTH_AUTHZ_REQUEST))
                .body(
                    transaction.transactionGet(buildPostSuccessUrl(transaction.id))
                        .also { Napier.i("/transaction/$id returns $it") })
        }.getOrElse {
            Napier.w("/transaction/get/$id error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
        }
    }

    @GetMapping("/logs/{id}", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionLogs(
        @PathVariable id: String,
    ): ResponseEntity<Collection<String>> = run {
        MDC.put(MDC_REQUEST_ID, id)
        val logs = AntilogSlf4jAdapter.transactionLogs[id]?.ifEmpty { null }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        ResponseEntity.ok(logs)
    }

    /**
     * Expects OpenID4VP authn response as request body,
     * called from Wallet App upon answering authn request from [transactionGet].
     */
    @PostMapping("/transaction/result/{id}")
    fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<OpenId4VpSuccess> = runBlocking {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("/transaction/result/$id called with $requestBody")
        val transaction = transactions.remove(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/result/$id returns NOT_FOUND") }
        val user = catching {
            validateAuthnResponse(transaction.profile.verifier, requestBody)
        }.getOrElse {
            Napier.w("/transaction/result/$id extracted got error", it)
            statisticLogger.error("$id error (${request.getHeader(HttpHeaders.USER_AGENT)})", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage, it)
        }
        Napier.i("/transaction/result/$id extracted result $user")
        statisticLogger.info("$id success $user (${request.getHeader(HttpHeaders.USER_AGENT)})")
        transactionStore.put(id, user)
        val redirectUrlWithId = ServletUriComponentsBuilder
            .fromUriString(customerSuccessUrl)
            .queryParam("id", id)
            .toUriString()
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(OpenId4VpSuccess(redirectUrlWithId))
    }

    private fun buildTransactionUrl(
        request: TransactionRequest,
        transactionId: String,
        profile: Profile,
    ) = ServletUriComponentsBuilder.fromUriString(publicUrl)
        .pathSegment("transaction", "get", transactionId)
        .toUriString()
        .also { transactions[transactionId] = Transaction(transactionId, request, profile) }

    private fun buildPostSuccessUrl(transactionId: String) =
        ServletUriComponentsBuilder.fromUriString(publicUrl)
            .pathSegment("transaction", "result", transactionId)
            .toUriString()

    private suspend fun validateAuthnResponse(
        verifier: OpenId4VpVerifier,
        authnResponse: String,
    ): OpenId4VpUser = when (val result = verifier.validateAuthnResponse(authnResponse)) {
        is VerifiableDCQLPresentationValidationResults -> result.validationResults.toOpenId4VpUser()
        is Success -> result.vp.toApiItemCredential().toOpenId4VpUser()
        is SuccessSdJwt -> result.toApiItemCredential().toOpenId4VpUser()
        is SuccessIso -> result.toApiItemCredentials().toOpenId4VpUser()
        is Error -> throw RuntimeException(result.reason, result.cause)
        is ValidationError -> throw RuntimeException("Failed: ${result.field}", result.cause)
        is VerifiablePresentationValidationResults -> result.toApiItemCredentials().toOpenId4VpUser()
        is IdToken -> throw RuntimeException("Only got id_token")
    }

}

fun AuthenticatedPrincipal.toApiItem() = if (this is OpenId4VpUser) this.apiItem else null

