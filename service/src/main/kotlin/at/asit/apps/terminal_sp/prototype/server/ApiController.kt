package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.openid.AuthenticationResponseParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.RelyingPartyMetadata
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.PreRegistered
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import qrcode.QRCode
import java.util.*
import kotlin.collections.set
import kotlin.random.Random
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

    data class Transaction(
        val id: String,
        val request: TransactionRequest,
        val profile: Profile,
    )

    data class Profile(
        val name: String,
        val label: String,
        val urlPrefix: String,
        val clientId: String,
        val verifier: OidcSiopVerifier,
    )

    private val knownProfiles: List<Profile> = listOf(
        "AT-GV-EGIZ-CUSTOMVERIFIER".let { clientId ->
            Profile(
                name = "HAIP",
                label = "HAIP (Potential)",
                urlPrefix = "haip://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId)
                )
            )
        },
        publicUrl.let { clientId ->
            Profile(
                name = "EUDI",
                label = "EUDI",
                urlPrefix = "eudi-openid4vp://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId)
                )
            )
        },
        publicUrl.let { clientId ->
            Profile(
                name = "MDOC",
                label = "ISO 18013-7",
                urlPrefix = "mdoc-openid4vp://",
                clientId = clientId,
                verifier = OidcSiopVerifier(
                    verifier = VerifierAgent(clientId),
                    keyMaterial = EphemeralKeyWithoutCert(),
                    clientIdScheme = PreRegistered(clientId)
                )
            )
        }
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
        val profiles = knownProfiles.map {
            val transactionId = Uuid.random().toString()
            val transactionUrl = buildTransactionUrl(request, transactionId, it)
            val qrCodeUrl = buildQrCodeUrl(it, transactionUrl)
            val qrCodeBytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            val remoteWalletPrefix = "https://wallet.a-sit.at/remote/" + if (request.simple) "simple" else ""
            val remoteWalletUrl = buildQrCodeUrl(it, transactionUrl)
            TransactionProfile(it.name, it.label, it.urlPrefix, qrCodeBytes.toDataUrl(), qrCodeUrl, remoteWalletUrl)
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
        val transactionRequest = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/$id returns NOT_FOUND") }
        val state = createSafeState()
        val requestOptions = OidcSiopVerifier.RequestOptions(
            state = state,
            responseMode = OpenIdConstants.ResponseMode.DirectPost,
            responseUrl = buildPostSuccessUrl(id),
            credentials = transactionRequest.request.toRequestOptionsCredentials(), // TODO Attributes optional!
        )
        // TODO may not always be a requestObjectJws!
        val requestObjectJws =
            transactionRequest.profile.verifier.createAuthnRequestAsSignedRequestObject(requestOptions).getOrElse {
                Napier.w("/transaction/get/$id error", it)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
            }
        val result = requestObjectJws.serialize()
            .also { Napier.i("/transaction/$id returns $it") }
        ResponseEntity.ok(result)
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
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        val user = validateSiopResponse(params, transaction.profile.verifier)
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

    private fun buildQrCodeUrl(profile: Profile, requestUri: String) =
        ServletUriComponentsBuilder.fromUriString(profile.urlPrefix)
            .queryParam("request_uri", requestUri)
            .queryParam("client_id", profile.clientId)
            // TODO may be client_metadata ... or even nothing at all!
            .queryParam(
                "client_metadata_uri", ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
                    .pathSegment("siopv2", "metadata", profile.name)
                    .toUriString()
            )
            .toUriString()

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

    @ResponseBody
    @GetMapping("/siopv2/metadata/{profilename}")
    fun siopv2Metadata(@PathVariable("profilename") profileName: String): ResponseEntity<RelyingPartyMetadata> =
        runBlocking {
            Napier.i("/siopv2/metadata/$profileName called")
            knownProfiles.firstOrNull { it.name == profileName }?.verifier?.let { verifier ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifier.createSignedMetadata().getOrThrow().payload)
            } ?: ResponseEntity.notFound().build()
        }

    private suspend fun validateSiopResponse(
        params: AuthenticationResponseParameters,
        verifier: OidcSiopVerifier,
    ): Siop2User {
        Napier.i("validateSiopResponse with $params")
        return when (val result = verifier.validateAuthnResponse(params)) {
            is OidcSiopVerifier.AuthnResponseResult.Success ->
                with(result.vp.toSiop2User()) {
                    if (this == null) {
                        Napier.w("Cannot parse from VP: ${result.vp}")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from VP")
                    }
                    this
                }

            is OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt ->
                result.toApiItemCredential().toSiop2User()

            is OidcSiopVerifier.AuthnResponseResult.SuccessIso ->
                result.toApiItemCredentials().toSiop2User()

            is OidcSiopVerifier.AuthnResponseResult.Error ->
                throw RuntimeException(result.reason)

            is OidcSiopVerifier.AuthnResponseResult.ValidationError ->
                throw RuntimeException("Validation failed for field: ${result.field}")

            is OidcSiopVerifier.AuthnResponseResult.VerifiablePresentationValidationResults ->
                result.toApiItemCredentials().toSiop2User()

            is OidcSiopVerifier.AuthnResponseResult.IdToken ->
                throw RuntimeException("Only got id_token")
        }
    }

    private fun createSafeState() = Random.nextBytes(32).encodeToString(Base64())

}

fun AuthenticatedPrincipal.toApiItem() = if (this is Siop2User) this.apiItem else null

