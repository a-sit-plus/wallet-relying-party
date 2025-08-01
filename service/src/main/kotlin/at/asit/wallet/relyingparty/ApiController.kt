package at.asit.wallet.relyingparty

import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.openid.AuthnResponseResult
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
    @Value("\${app.public-url}")
    private val publicUrl: String,
    private val transactionStore: TransactionStore,
) {
    private val statisticLogger = LoggerFactory.getLogger("statistic")
    private val transactions: MutableMap<String, Transaction> = HashMap()
    private val customerSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromUriString(publicUrl)
            .pathSegment("customer-success.html")
            .toUriString()
    }
    private val profiles = VerifierProfiles(publicUrl)

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
    fun apiSingle(@PathVariable id: String): ResponseEntity<ApiItem> =
        transactionStore.getApiItem(id)?.let {
            Napier.i("/api/single/$id returns $it")
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(it)
        } ?: ResponseEntity.notFound().build()

    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(@RequestBody id: String): ResponseEntity<ApiItem> =
        transactionStore.removeApiItem(id).let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @OptIn(ExperimentalUuidApi::class)
    @PostMapping("/transaction/create", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionCreate(@RequestBody request: TransactionRequest): ResponseEntity<TransactionResponse> = runBlocking {
        Napier.i("/transaction/create called with $request")
        val profiles = profiles.knownProfiles.map {
            val transactionId = Uuid.random().toString()
            val transactionUrl = buildTransactionUrl(request, transactionId, it)
            val qrCodeUrl = it.buildQrCodeUrl(transactionUrl, it.urlPrefix)
            val qrCodeBytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            val remoteWalletPrefix = "https://wallet.a-sit.at/remote/" + if (request.simple) "simple" else ""
            val remoteWalletUrl = it.buildQrCodeUrl(transactionUrl, remoteWalletPrefix)
            TransactionProfile(
                id = transactionId,
                name = it.name,
                description = it.description,
                label = it.label,
                prefix = it.urlPrefix,
                png = qrCodeBytes.toDataUrl(),
                url = qrCodeUrl,
                remoteWalletUrl = remoteWalletUrl
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

        try {
            val result = transaction.transactionGet(buildPostSuccessUrl(transaction.id))
                .also { Napier.i("/transaction/$id returns $it") }
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/" + JwsContentTypeConstants.OAUTH_AUTHZ_REQUEST))
                .body(result)
        } catch (e: Exception) {
            Napier.w("/transaction/get/$id error", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage)
        }
    }

    @GetMapping("/logs/{id}", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionLogs(@PathVariable id: String): ResponseEntity<Collection<String>> = runBlocking {
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
        if (transaction == null) {
            Napier.w("/transaction/result/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val user = try {
            validateAuthnResponse(id, requestBody, transaction.profile.verifier)
        } catch (e: Exception) {
            statisticLogger.error("$id error (${request.getHeader(HttpHeaders.USER_AGENT)})", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage, e)
        }
        if (user == null) {
            statisticLogger.error("$id error (${request.getHeader(HttpHeaders.USER_AGENT)})")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from VP")
        }
        statisticLogger.info("$id success $user (${request.getHeader(HttpHeaders.USER_AGENT)})")
        Napier.i("Storing user for transaction $id: $user")
        transactionStore.put(id, user)
        val redirectUrlWithId = ServletUriComponentsBuilder
            .fromUriString(customerSuccessUrl)
            .queryParam("id", id)
            .toUriString()
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(OpenId4VpSuccess(redirectUrlWithId))
    }

    private fun buildTransactionUrl(request: TransactionRequest, transactionId: String, profile: Profile) =
        runBlocking {
            transactions[transactionId] = Transaction(transactionId, request, profile)
            ServletUriComponentsBuilder.fromUriString(publicUrl)
                .pathSegment("transaction", "get", transactionId)
                .toUriString()
        }

    private fun buildPostSuccessUrl(transactionId: String) = runBlocking {
        ServletUriComponentsBuilder.fromUriString(publicUrl)
            .pathSegment("transaction", "result", transactionId)
            .toUriString()
    }

    @GetMapping(
        value = [OpenIdConstants.PATH_WELL_KNOWN_JAR_ISSUER],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun jarMetadata(
        request: HttpServletRequest,
    ): ResponseEntity<JwtVcIssuerMetadata> {
        val metadata = profiles.getJarMetadataByName(DEFAULT_PROFILE)
        Napier.i("${request.requestURI} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @GetMapping(
        value = ["${OpenIdConstants.PATH_WELL_KNOWN_JAR_ISSUER}/{id}"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun jarMetadataNamed(
        @PathVariable("id") verifierId: String?,
        request: HttpServletRequest,
    ): ResponseEntity<JwtVcIssuerMetadata> {
        val metadata = profiles.getJarMetadataByName(verifierId ?: DEFAULT_PROFILE)
        Napier.i("${request.requestURI} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    private suspend fun validateAuthnResponse(
        id: String,
        requestBody: String,
        verifier: OpenId4VpVerifier,
    ): OpenId4VpUser? = when (val result = verifier.validateAuthnResponse(requestBody).also {
        Napier.i("/transaction/result/$id extracted result $it")
    }) {
        is AuthnResponseResult.VerifiableDCQLPresentationValidationResults -> result.validationResults.toOpenId4VpUser()
        is AuthnResponseResult.Success -> result.vp.toApiItemCredential().toOpenId4VpUser()
        is AuthnResponseResult.SuccessSdJwt -> result.toApiItemCredential().toOpenId4VpUser()
        is AuthnResponseResult.SuccessIso -> result.toApiItemCredentials().toOpenId4VpUser()
        is AuthnResponseResult.Error -> throw RuntimeException(result.reason, result.cause)
        is AuthnResponseResult.ValidationError -> throw RuntimeException("Failed: ${result.field}", result.cause)
        is AuthnResponseResult.VerifiablePresentationValidationResults -> result.toApiItemCredentials().toOpenId4VpUser()
        is AuthnResponseResult.IdToken -> throw RuntimeException("Only got id_token")
    }

}

fun AuthenticatedPrincipal.toApiItem() = if (this is OpenId4VpUser) this.apiItem else null

