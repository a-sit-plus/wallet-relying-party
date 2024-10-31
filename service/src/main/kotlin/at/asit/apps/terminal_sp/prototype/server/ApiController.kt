package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.openid.AuthenticationResponseParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.CertificateSanDns
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import io.github.aakira.napier.Napier
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
import org.springframework.web.util.UriComponentsBuilder
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
    private val extensions = listOf(X509CertificateExtension(
        KnownOIDs.subjectAltName_2_5_29_17,
        critical = false,
        Asn1EncapsulatingOctetString(listOf(
            Asn1.Sequence {
                +Asn1Primitive(
                    SubjectAltNameImplicitTags.dNSName,
                    Asn1String.UTF8(publicUrl.getDnsName()).encodeToTlv().content
                )
            }
        ))))
    private val verifierKeyMaterial = EphemeralKeyWithSelfSignedCert(extensions = extensions)
    private val verifier: VerifierAgent = VerifierAgent(verifierKeyMaterial)
    private val customerSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("customer-success.html")
            .toUriString()
    }
    private val metadataUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2", "metadata")
            .toUriString()
    }
    private val verifierProtocol: OidcSiopVerifier by lazy { runBlocking { newVerifier() } }

    private suspend fun newVerifier(): OidcSiopVerifier = OidcSiopVerifier(
        verifier = verifier,
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = CertificateSanDns(listOf(verifierKeyMaterial.getCertificate()!!), publicUrl.getDnsName())
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
        val transactionId = Uuid.random().toString()
        val qrCodeUrl = buildQrCodeUrl(request, transactionId)
        val qrCodeBytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
        val response = TransactionResponse(qrCodeBytes, qrCodeUrl, transactionId)
        Napier.i("/transaction/create returns $transactionId with $qrCodeUrl")
        ResponseEntity.ok().body(response)
    }

    @GetMapping("/transaction/get/{id}")
    @ResponseBody
    fun transactionGet(@PathVariable id: String): ResponseEntity<String> = runBlocking {
        Napier.i("/transaction/$id called")
        val transactionRequest = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/$id returns NOT_FOUND") }
        val state = createSafeState()
        val requestOptions = OidcSiopVerifier.RequestOptions(
            state = state,
            responseMode = OpenIdConstants.ResponseMode.DirectPost,
            responseUrl = buildPostSuccessUrl(id),
            credentials = transactionRequest.request.toRequestOptionsCredentials(),
        )
        val requestObjectJws = verifierProtocol.createAuthnRequestAsSignedRequestObject(requestOptions).getOrElse {
            Napier.w("/transaction/$id error", it)
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
        if (transactions.remove(id) == null) {
            Napier.w("/transaction/result/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        val user = validateSiopResponse(params)
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

    private fun buildQrCodeUrl(request: TransactionRequest, transactionId: String) =
        ServletUriComponentsBuilder.fromUriString(request.urlprefix)
            .queryParam("request_uri", buildTransactionUrl(request, transactionId))
            .queryParam("client_id", publicUrl.getDnsName())
            .queryParam("client_metadata_uri", metadataUrl)
            .toUriString()

    private fun buildTransactionUrl(request: TransactionRequest, transactionId: String) = runBlocking {
        transactions[transactionId] = Transaction(transactionId, request)
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("transaction", "get", transactionId)
            .toUriString()
    }

    private fun buildPostSuccessUrl(transactionId: String) = runBlocking {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("transaction", "result", transactionId)
            .toUriString()
    }

    /**
     * Returns signed metadata.
     */
    @ResponseBody
    @GetMapping("/siopv2/metadata")
    fun siopv2Metadata(): ResponseEntity<String> = runBlocking {
        Napier.i("/siopv2/metadata called")
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(verifierProtocol.createSignedMetadata().getOrThrow().payload.decodeToString())
    }

    private suspend fun validateSiopResponse(params: AuthenticationResponseParameters): Siop2User {
        Napier.i("validateSiopResponse with $params")
        return when (val result = verifierProtocol.validateAuthnResponse(params)) {
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

    private fun createSafeState() = Base64.getEncoder().encodeToString(Random.nextBytes(32))

}

fun AuthenticatedPrincipal.toApiItem() = if (this is Siop2User) this.apiItem else null

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"

