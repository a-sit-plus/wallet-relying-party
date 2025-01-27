package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.RelyingPartyMetadata
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
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
import java.util.*
import kotlin.collections.set
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@Controller
class ApiController(
    @Value("\${app.public-url}")
    private val publicUrl: String,
    private val transactionStore: TransactionStore,
) {
    private val transactions: MutableMap<String, Transaction> = HashMap()
    private val customerSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
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
    @PostMapping("/transaction/create", produces = [MediaType.APPLICATION_JSON_VALUE])
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
                transactionId,
                it.name,
                it.label,
                it.urlPrefix,
                qrCodeBytes.toDataUrl(),
                qrCodeUrl,
                remoteWalletUrl
            )
        }
        val response = TransactionResponse(profiles)
        Napier.i("/transaction/create returns $response")
        ResponseEntity.ok().body(response)
    }

    private fun ByteArray.toDataUrl(): String = "data:image/png;base64," + encodeToString(Base64())

    @GetMapping("/transaction/get/{id}")
    @ResponseBody
    fun transactionGet(@PathVariable id: String): ResponseEntity<String> = runBlocking {
        Napier.i("/transaction/get/$id called")
        val transaction = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/$id returns NOT_FOUND") }

        try {
            val result = transaction.transactionGet(buildPostSuccessUrl(transaction.id))
                .also { Napier.i("/transaction/$id returns $it") }
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Napier.w("/transaction/get/$id error", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage)
        }
    }

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [transactionGet].
     */
    @PostMapping("/transaction/result/{id}")
    fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
    ): ResponseEntity<OpenId4VpSuccess> = runBlocking {
        Napier.i("/transaction/result/$id called with $requestBody")
        val transaction = transactions.remove(id)
        if (transaction == null) {
            Napier.w("/transaction/result/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val user = validateSiopResponse(requestBody, transaction.profile.verifier)
        Napier.i("Storing user for transaction $id: $user")
        transactionStore.put(id, user)
        val redirectUrlWithId = ServletUriComponentsBuilder
            .fromHttpUrl(customerSuccessUrl)
            .queryParam("id", id)
            .toUriString()
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(OpenId4VpSuccess(redirectUrlWithId))
    }

    private fun buildTransactionUrl(request: TransactionRequest, transactionId: String, profile: Profile) =
        runBlocking {
            transactions[transactionId] = Transaction(transactionId, request, profile)
            ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
                .pathSegment("transaction", "get", transactionId)
                .toUriString()
        }

    private fun buildPostSuccessUrl(transactionId: String) = runBlocking {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("transaction", "result", transactionId)
            .toUriString()
    }

    // TODO Maybe that's not needed at all, if nobody's using the client_metadata_uri
    @ResponseBody
    @GetMapping("/siopv2/metadata/{profilename}")
    fun siopv2Metadata(@PathVariable("profilename") profileName: String): ResponseEntity<RelyingPartyMetadata> =
        runBlocking {
            Napier.i("/siopv2/metadata/$profileName called")
            profiles.getVerifierByName(profileName)?.let { verifier ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifier.metadata)
            } ?: ResponseEntity.notFound().build()
        }

    @GetMapping(
        value = [OpenIdConstants.PATH_WELL_KNOWN_JAR_ISSUER, ],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun jarMetadata(
        request: HttpServletRequest,
    ): ResponseEntity<JwtVcIssuerMetadata> {
        val metadata = profiles.getJarMetadataByName("HAIP")
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
        val metadata = profiles.getJarMetadataByName(verifierId ?: "HAIP")
        Napier.i("${request.requestURI} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    private suspend fun validateSiopResponse(
        requestBody: String,
        verifier: OpenId4VpVerifier,
    ): Siop2User = when (val result = verifier.validateAuthnResponse(requestBody)) {
        is AuthnResponseResult.Success ->
            with(result.vp.toSiop2User()) {
                if (this == null) {
                    Napier.w("Cannot parse from VP: ${result.vp}")
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from VP")
                }
                this
            }

        is AuthnResponseResult.SuccessSdJwt ->
            result.toApiItemCredential().toSiop2User()

        is AuthnResponseResult.SuccessIso ->
            result.toApiItemCredentials().toSiop2User()

        is AuthnResponseResult.Error ->
            throw RuntimeException(result.reason)

        is AuthnResponseResult.ValidationError ->
            throw RuntimeException("Validation failed for field: ${result.field}")

        is AuthnResponseResult.VerifiablePresentationValidationResults ->
            result.toApiItemCredentials().toSiop2User()

        is AuthnResponseResult.IdToken ->
            throw RuntimeException("Only got id_token")
    }

}

fun AuthenticatedPrincipal.toApiItem() = if (this is Siop2User) this.apiItem else null

