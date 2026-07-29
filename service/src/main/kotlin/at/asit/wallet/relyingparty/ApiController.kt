package at.asit.wallet.relyingparty

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.dcapi.DigitalCredentialInterface
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifiedPresentationResult
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.CredentialPresentationRequestBuilder
import at.asitplus.wallet.lib.openid.DcApiResponseResult
import at.asitplus.wallet.lib.openid.Iso180137AnnexCWrapper
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import qrcode.QRCode
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Controller
class ApiController(
    private val configuration: AppConfigurationProperties,
    private val transactionStore: TransactionStore,
    private val profiles: VerifierProfiles,
    private val trustListService: TrustListService
) {
    private val statisticLogger = LoggerFactory.getLogger("statistic")
    private val transactionMutex = Mutex()
    private val transactions: MutableMap<String, StoredTransaction> = HashMap()
    private val customerSuccessUrl = configuration.publicContext.appendPath(Paths.CustomerSuccessUrl)

    data class Transaction(
        val id: String,
        val profile: PreparedProfile,
        val dcApiOrigin: String,
        val presentationMechanism: PresentationMechanismEnum,
        val presentationExchangeRequest: CredentialPresentationRequest.PresentationExchangeRequest? = null,
        val dcqlRequest: CredentialPresentationRequest.DCQLRequest? = null,
    )

    @GetMapping(Paths.Api.ItemsUrl)
    @ResponseBody
    suspend fun apiItems(): List<ApiItem> = transactionStore.getApiItems()

    @GetMapping("${Paths.Api.SingleUrl}/{id}")
    @ResponseBody
    suspend fun apiSingle(
        @PathVariable id: String,
    ): ResponseEntity<ApiItem> = transactionStore.getApiItem(id)?.let {
        Napier.i("${Paths.Api.SingleUrl}/$id returns $it")
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(it)
    } ?: ResponseEntity.noContent().build()

    @PostMapping(Paths.Utilities.BuildCredentialQueriesUrl)
    @ResponseBody
    suspend fun buildCredentialQueries(
        @RequestBody credentials: List<TransactionRequestCredential>,
    ): ResponseEntity<TransactionRequestQueries> = CredentialPresentationRequestBuilder(
        credentials.map { it.toRequestOptionsCredential() }
    ).let {
        val presentationDefinition = catching {
            it.toPresentationExchangeRequest().presentationDefinition
        }
        val dcqlQuery = catching {
            it.toDCQLRequest()?.dcqlQuery
        }
        ResponseEntity.ok(
            TransactionRequestQueries(
                presentationDefinition = presentationDefinition.getOrNull(),
                presentationDefinitionError = presentationDefinition.exceptionOrNull()?.message,
                dcqlQuery = dcqlQuery.getOrNull(),
                dcqlQueryError = dcqlQuery.exceptionOrNull()?.message,
            )
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @PostMapping(Paths.Transaction.CreateUrl, produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    suspend fun transactionCreate(
        @RequestBody request: TransactionRequest,
    ): ResponseEntity<TransactionResponse> {
        Napier.i("${Paths.Transaction.CreateUrl} called with $request")
        val dcApiOrigin = request.dcApiOrigin ?: configuration.publicContext.toString()
        if (request.dcApiOrigin != null && !ANDROID_APP_ORIGIN.matches(dcApiOrigin)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Android app origin")
        }
        val profiles = profiles.knownProfiles.map {
            val transactionId = Uuid.random().toString()
            val transactionContext = buildTransactionContext(transactionId, it.supportedOptions, dcApiOrigin)
            val preparedProfile = profiles.prepare(it, transactionContext)
            val transaction = Transaction(
                id = transactionId,
                profile = preparedProfile,
                dcApiOrigin = dcApiOrigin,
                presentationMechanism = request.presentationMechanism,
                presentationExchangeRequest = request.presentationDefinition?.let {
                    CredentialPresentationRequest.PresentationExchangeRequest(it)
                },
                dcqlRequest = request.dcqlQuery?.let {
                    CredentialPresentationRequest.DCQLRequest(it)
                },
            )
            putTransaction(transaction)
            val qrCodeUrl = preparedProfile.buildWalletUrl(transaction, transactionContext)
            val qrCodeBytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            TransactionProfile(
                id = transactionId,
                name = preparedProfile.name,
                description = preparedProfile.description,
                label = preparedProfile.label,
                prefix = preparedProfile.urlPrefix,
                png = qrCodeBytes.toDataUrl(),
                url = qrCodeUrl,
                dcApiUrl = transactionContext.dcApiUrl,
                supportedOptions = preparedProfile.supportedOptions
            )
        }
        val response = TransactionResponse(profiles)
        Napier.i("${Paths.Transaction.CreateUrl} returns $response")
        return ResponseEntity.ok().body(response)
    }

    private fun ByteArray.toDataUrl(): String = "data:image/png;base64," + encodeToString(Base64())

    @GetMapping("${Paths.Transaction.GetUrl}/{id}")
    @ResponseBody
    suspend fun transactionGet(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("${Paths.Transaction.GetUrl}/$id called")
        statisticLogger.info("$id get (${request.getHeader(HttpHeaders.USER_AGENT)})")
        val transaction = getTransaction(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.GetUrl}/$id returns NOT_FOUND") }

        return catching {
            check(transaction.profile.supportedOptions.any { it.isUrlOrQrCode }) { "Profile does not device flow" }

            val responseUrl = configuration.publicContext.appendPath("${Paths.Transaction.ResultUrl}/${transaction.id}")
            val body = transaction.transactionGet(responseUrl)
                .also { Napier.i("${Paths.Transaction.GetUrl}/$id returns $it") }
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/" + JwsContentTypeConstants.OAUTH_AUTHZ_REQUEST))
                .body(body)
        }.getOrElse {
            Napier.w("${Paths.Transaction.GetUrl}/$id error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.clientReason(HttpStatus.BAD_REQUEST))
        }
    }

    @GetMapping("${Paths.Transaction.GetDcApiUrl}/{id}", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    suspend fun transactionGetDcApi(
        @PathVariable id: String,
        @RequestParam(name = "oid4vpMode", required = false, defaultValue = "SIGNED") oid4vpMode: Oid4vpDcApiMode,
        @RequestParam(name = "isoMdoc", required = false, defaultValue = "false") isoMdoc: Boolean,
        @RequestParam(name = "encrypt", required = false, defaultValue = "true") encrypt: Boolean,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("/transaction/get/dcapi/$id called (oid4vpMode=$oid4vpMode, isoMdoc=$isoMdoc, encrypt=$encrypt)")
        statisticLogger.info("$id get-dcapi (${request.getHeader(HttpHeaders.USER_AGENT)})")
        val transaction = getTransaction(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/dcapi/$id returns NOT_FOUND") }

        if (oid4vpMode == Oid4vpDcApiMode.NONE && !isoMdoc)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one DC API request type")

        return catching {
            check(transaction.profile.supportedOptions.any { it.isDcApi }) { "Profile does not support DC API flow" }
            val responseUrl = configuration.publicContext.appendPath("${Paths.Transaction.ResultUrl}/${transaction.id}")
            val body = transaction.transactionGetDcApi(responseUrl, oid4vpMode, isoMdoc, encrypt)
                .also { Napier.i("${Paths.Transaction.GetUrl}/$id returns $it") }
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        }.getOrElse {
            Napier.w("/transaction/get/dcapi/$id error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.clientReason(HttpStatus.BAD_REQUEST))
        }
    }

    @GetMapping("${Paths.LogsUrl}/{id}", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionLogs(
        @PathVariable id: String,
    ): ResponseEntity<Collection<String>> = run {
        MDC.put(MDC_REQUEST_ID, id)
        AntilogSlf4jAdapter.transactionLogs[id].takeIf { it != null }.let {
            ResponseEntity.ok(it)
        }
    }

    /**
     * Expects OpenID4VP authn response as request body,
     * called from Wallet App upon answering authn request from [transactionGet].
     */
    @PostMapping("${Paths.Transaction.ResultUrl}/{id}")
    suspend fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<OpenId4VpSuccess> {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("${Paths.Transaction.ResultUrl}/$id called with $requestBody")
        val transaction = removeTransaction(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.ResultUrl}/$id returns NOT_FOUND") }
        val validationResult = catching {
            val isDcApiResponse = catching {
                joseCompliantSerializer.decodeFromString<DigitalCredentialInterface>(requestBody)
            }.getOrNull() != null
            if (isDcApiResponse && transaction.profile.supportedOptions.any { it.isDcApi }) {
                checkNotNull(transaction.profile.dcApiVerifier) { "Missing verifier" }
                    .validateAuthnResponse(
                        input = requestBody,
                        externalId = id,
                        expectedOrigin = transaction.dcApiOrigin,
                    ).getOrThrow()
            } else if (transaction.profile.supportedOptions.any { it.isUrlOrQrCode }) {
                checkNotNull(transaction.profile.oid4vpVerifier) { "Missing verifier" }
                    .validateAuthnResponse(
                        input = requestBody,
                    ).getOrThrow()
            } else {
                error("Unsupported response for transaction $id")
            }
        }.getOrElse {
            Napier.w("${Paths.Transaction.ResultUrl}/$id extracted got error", it)
            statisticLogger.error("$id error (${request.getHeader(HttpHeaders.USER_AGENT)})", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.clientReason(HttpStatus.BAD_REQUEST), it)
        }
        val user = validationResult.convertToUser(trustListService::evaluateCredentialIssuerTrust)
        Napier.i("${Paths.Transaction.ResultUrl}/$id extracted result $user")
        statisticLogger.info("$id success $user (${request.getHeader(HttpHeaders.USER_AGENT)})")
        transactionStore.put(id, user)
        val redirectUrlWithId = ServletUriComponentsBuilder
            .fromUriString(customerSuccessUrl)
            .queryParam("id", id)
            .toUriString()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(OpenId4VpSuccess(redirectUrlWithId))
    }

    private fun buildTransactionContext(
        transactionId: String,
        supportedOptions: Set<SupportedOptions>,
        dcApiOrigin: String,
    ) = TransactionContext(
        id = transactionId,
        transactionGetUrl = configuration.publicContext.appendPath("${Paths.Transaction.GetUrl}/$transactionId"),
        responseUrl = configuration.publicContext.appendPath("${Paths.Transaction.ResultUrl}/${transactionId}"),
        dcApiOrigin = dcApiOrigin,
        dcApiUrl = supportedOptions.any { it.isDcApi }
            .takeIf { it }
            ?.let { configuration.publicContext.appendPath("${Paths.Transaction.GetDcApiUrl}/$transactionId") }
    )

    private suspend fun putTransaction(transaction: Transaction) = transactionMutex.withLock {
        removeExpiredTransactions()
        transactions[transaction.id] = StoredTransaction(
            transaction = transaction,
            notAfter = Instant.now().plus(configuration.transactionTtl),
        )
    }

    private suspend fun getTransaction(id: String): Transaction? = transactionMutex.withLock {
        removeExpiredTransactions()
        transactions[id]?.transaction
    }

    private suspend fun removeTransaction(id: String): Transaction? = transactionMutex.withLock {
        removeExpiredTransactions()
        transactions.remove(id)?.transaction
    }

    @Scheduled(fixedDelay = 60_000)
    fun removeExpiredTransactionsScheduled() = runBlocking {
        transactionMutex.withLock {
            removeExpiredTransactions()
        }
    }

    private fun removeExpiredTransactions() {
        val now = Instant.now()
        transactions.entries.removeAll { it.value.notAfter < now }
    }

    private companion object {
        val ANDROID_APP_ORIGIN = Regex("android:apk-key-hash:[A-Za-z0-9+/]{43}")
    }

}

private data class StoredTransaction(
    val transaction: ApiController.Transaction,
    val notAfter: Instant,
)

fun AuthenticatedPrincipal.toApiItem() = if (this is User) this.apiItem else null

private fun Throwable.clientReason(status: HttpStatus): String =
    if (this is ClientFacingException) localizedMessage ?: message ?: status.reasonPhrase else status.reasonPhrase
