package at.asit.wallet.relyingparty

import at.asitplus.catching
import at.asitplus.dcapi.DigitalCredentialInterface
import at.asitplus.dcapi.IsoMdocResponse
import at.asitplus.dcapi.OpenId4VpResponse
import at.asitplus.dcapi.OpenId4VpResponseSigned
import at.asitplus.dcapi.OpenId4VpResponseUnsigned
import at.asitplus.signum.indispensable.CryptoPrivateKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.Hpke
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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
    private val configuration: AppConfigurationProperties,
    private val transactionStore: TransactionStore,
    private val profiles: VerifierProfiles,
) {
    private val statisticLogger = LoggerFactory.getLogger("statistic")
    private val transactions: MutableMap<String, Transaction> = HashMap()
    private val customerSuccessUrl = configuration.publicContext.appendPath(Paths.CustomerSuccessUrl)

    data class Transaction(
        val id: String,
        val request: TransactionRequest,
        val profile: Profile,
    )

    @GetMapping(Paths.Api.ItemsUrl)
    @ResponseBody
    fun apiItems(): List<ApiItem> = transactionStore.getApiItems()

    @GetMapping("${Paths.Api.SingleUrl}/{id}")
    @ResponseBody
    fun apiSingle(
        @PathVariable id: String,
    ): ResponseEntity<ApiItem> = transactionStore.getApiItem(id)?.let {
        Napier.i("${Paths.Api.SingleUrl}/$id returns $it")
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(it)
    } ?: ResponseEntity.noContent().build()

    @PostMapping(Paths.Api.RemoveUrl)
    @ResponseBody
    fun removeApiItem(
        @RequestBody id: String,
    ): ResponseEntity<ApiItem> = transactionStore.removeApiItem(id).let {
        ResponseEntity.ok(it)
    } ?: ResponseEntity.notFound().build()

    @OptIn(ExperimentalUuidApi::class)
    @PostMapping(Paths.Transaction.CreateUrl, produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    fun transactionCreate(
        @RequestBody request: TransactionRequest,
    ): ResponseEntity<TransactionResponse> = run {
        Napier.i("${Paths.Transaction.CreateUrl} called with $request")
        val profiles = profiles.knownProfiles.map {
            val transactionId = Uuid.random().toString()
            val transactionUrl = buildTransactionUrl(request, transactionId, it)
            val dcApiUrl = it.supportedOptions.any { it.isDcApi }.takeIf { it }
                ?.let { configuration.publicContext.appendPath("${Paths.Transaction.GetDcApiUrl}/$transactionId") }
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
                dcApiUrl = dcApiUrl,
                supportedOptions = it.supportedOptions
            )
        }
        val response = TransactionResponse(profiles)
        Napier.i("${Paths.Transaction.CreateUrl} returns $response")
        ResponseEntity.ok().body(response)
    }

    private fun ByteArray.toDataUrl(): String = "data:image/png;base64," + encodeToString(Base64())

    @GetMapping("${Paths.Transaction.GetUrl}/{id}")
    @ResponseBody
    fun transactionGet(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> = runBlocking {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("${Paths.Transaction.GetUrl}/$id called")
        statisticLogger.info("$id get (${request.getHeader(HttpHeaders.USER_AGENT)})")
        val transaction = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.GetUrl}/$id returns NOT_FOUND") }

        catching {
            if (transaction.profile.supportedOptions.none { it.isDevice }) {
                throw IllegalStateException("Profile does not support QR or same-device flow")
            }

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
    fun transactionGetDcApi(
        @PathVariable id: String,
        @RequestParam(name = "dcApiSignedOid4vp", required = false, defaultValue = "true") dcApiSignedOid4vp: Boolean,
        request: HttpServletRequest,
    ): ResponseEntity<String> = runBlocking {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("/transaction/get/dcapi/$id called (dcApiSignedOid4vp=$dcApiSignedOid4vp)")
        statisticLogger.info("$id get-dcapi (${request.getHeader(HttpHeaders.USER_AGENT)})")
        val transaction = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/dcapi/$id returns NOT_FOUND") }

        catching {
            if (transaction.profile.supportedOptions.none { it.isDcApi }) {
                throw IllegalStateException("Profile does not support DC API flow")
            }
            val responseUrl = configuration.publicContext.appendPath("${Paths.Transaction.ResultUrl}/${transaction.id}")
            val body = transaction.transactionGetDcApi(responseUrl, dcApiSignedOid4vp)
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
    } ?: ResponseEntity.noContent().build()

    /**
     * Expects OpenID4VP authn response as request body,
     * called from Wallet App upon answering authn request from [transactionGet].
     */
    @PostMapping("${Paths.Transaction.ResultUrl}/{id}")
    fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<OpenId4VpSuccess> = runBlocking {
        MDC.put(MDC_REQUEST_ID, id)
        Napier.i("${Paths.Transaction.ResultUrl}/$id called with $requestBody")
        val transaction = transactions.remove(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.ResultUrl}/$id returns NOT_FOUND") }
        val user = catching {
            val parsedResponse =
                catching { vckJsonSerializer.decodeFromString<DigitalCredentialInterface>(requestBody) }.getOrNull()
            if (parsedResponse.isMdocResponse(transaction)) {
                val verifier = transaction.profile.iso180137Verifier
                    ?: throw IllegalStateException("Missing verifier")
                verifier.validateResponse(
                    receivedData = (parsedResponse as IsoMdocResponse).data,
                    externalId = id,
                    decryptHpke = ::decryptHpke,
                    expectedOrigin = configuration.publicContext.toString()
                ).getOrThrow().toUser()
            } else if (parsedResponse.isOpenId4VpResponse(transaction)) {
                val dcApiSignedOid4vpRequired = transaction.profile.dcApiSignedOid4vpRequired
                require(dcApiSignedOid4vpRequired != null)
                if (dcApiSignedOid4vpRequired) {
                    check(parsedResponse is OpenId4VpResponseSigned) { "Expected signed response" }
                } else {
                    check(parsedResponse is OpenId4VpResponseUnsigned) { "Expected unsigned response" }
                }
                val verifier = transaction.profile.oid4vpVerifier
                    ?: throw IllegalStateException("Missing verifier")
                verifier.validateAuthnResponse(
                    input = parsedResponse as OpenId4VpResponse,
                    externalId = id
                ).convertToUser()
            } else if (transaction.profile.supportedOptions.any { it.isDevice }) {
                val verifier = transaction.profile.oid4vpVerifier
                    ?: throw IllegalStateException("Missing verifier")
                verifier.validateAuthnResponse(
                    input = requestBody,
                    externalId = id
                ).convertToUser()
            } else {
                throw IllegalStateException("Unsupported response")
            }
        }.getOrElse {
            Napier.w("${Paths.Transaction.ResultUrl}/$id extracted got error", it)
            statisticLogger.error("$id error (${request.getHeader(HttpHeaders.USER_AGENT)})", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.clientReason(HttpStatus.BAD_REQUEST), it)
        }
        Napier.i("${Paths.Transaction.ResultUrl}/$id extracted result $user")
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

    private fun DigitalCredentialInterface?.isMdocResponse(
        transaction: Transaction,
    ): Boolean =
        transaction.profile.supportedOptions.contains(SupportedOptions.ISO_MDOC_DC_API) && this is IsoMdocResponse

    private fun DigitalCredentialInterface?.isOpenId4VpResponse(
        transaction: Transaction,
    ): Boolean = transaction.profile.supportedOptions.contains(SupportedOptions.OID4VP_DC_API) &&
            (this is OpenId4VpResponseSigned || this is OpenId4VpResponseUnsigned)

    private fun buildTransactionUrl(
        request: TransactionRequest,
        transactionId: String,
        profile: Profile,
    ) = configuration.publicContext.appendPath("${Paths.Transaction.GetUrl}/$transactionId")
        .also { transactions[transactionId] = Transaction(transactionId, request, profile) }

    // TODO replace with signum implementation when available
    private suspend fun decryptHpke(
        enc: ByteArray,
        ciphertext: ByteArray,
        responseEncryptionKeySignum: CryptoPrivateKey.EC.WithPublicKey,
        cborEncodedSessionTranscript: ByteArray,
    ): ByteArray {
        val ecCurve = when (responseEncryptionKeySignum.curve) {
            ECCurve.SECP_256_R_1 -> EcCurve.P256
            ECCurve.SECP_384_R_1 -> EcCurve.P384
            ECCurve.SECP_521_R_1 -> EcCurve.P521
        }

        val privateKeyPem = responseEncryptionKeySignum.encodeToPEM().getOrThrow()
        val publicKeyPem = responseEncryptionKeySignum.publicKey.encodeToPEM().getOrThrow()
        val ecPublicKey = EcPublicKey.fromPem(publicKeyPem, ecCurve)
        val ecPrivateKey = EcPrivateKey.fromPem(privateKeyPem, ecPublicKey)

        val responseEncryptionKey = AsymmetricKey.anonymous(
            privateKey = ecPrivateKey,
            algorithm = ecCurve.defaultKeyAgreementAlgorithm
        )

        val decrypter = Hpke.getDecrypter(
            cipherSuite = Hpke.CipherSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM,
            receiverPrivateKey = responseEncryptionKey,
            encapsulatedKey = enc,
            info = cborEncodedSessionTranscript,
        )
        return decrypter.decrypt(
            ciphertext = ciphertext,
            aad = ByteArray(0),
        )
    }

    private fun AuthnResponseResult.convertToUser(): User = when (this) {
        is VerifiableDCQLPresentationValidationResults -> this.toUser()
        is Success -> this.toUser()
        is SuccessSdJwt -> this.toUser()
        is SuccessIso -> this.toUser()
        is VerifiablePresentationValidationResults -> this.toUser()
        is Error -> throw RuntimeException(this.reason, this.cause)
        is ValidationError -> throw RuntimeException("Failed: ${this.field}", this.cause)
        is IdToken -> throw RuntimeException("Only got id_token")
        is SuccessUnsigned -> this.toUser()
    }
}

fun AuthenticatedPrincipal.toApiItem() = if (this is User) this.apiItem else null

private fun Throwable.clientReason(status: HttpStatus): String =
    if (this is ClientFacingException) localizedMessage ?: message ?: status.reasonPhrase else status.reasonPhrase
