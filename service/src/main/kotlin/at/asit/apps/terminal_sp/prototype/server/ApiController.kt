package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.oidc.AuthenticationResponseParameters
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import qrcode.QRCode
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.set
import kotlin.concurrent.withLock


@Controller
class ApiController(
    @Value("\${app.public-url}")
    private val publicUrl: String
) {

    companion object {
        private val secureRandomGenerator = SecureRandom()
        private val lock: Lock = ReentrantLock()
        private val authenticatedUsers: MutableMap<String, AuthenticatedPrincipal> = HashMap()
        private val verifierCryptoService: CryptoService = DefaultCryptoService()
        private val verifier: VerifierAgent =
            VerifierAgent.newDefaultInstance(verifierCryptoService.jsonWebKey.identifier)
        private val verifierProtocolMap: MutableMap<String, OidcSiopVerifier?> = HashMap()
    }

    private val customerSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("customer-success.html")
            .toUriString()
    }
    private val postSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2", "postsuccess")
            .toUriString()
    }
    private val metadataUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2", "metadata")
            .toUriString()
    }

    private val verifierProtocol: OidcSiopVerifier by lazy {
        newVerifier()
    }

    private fun newVerifier(): OidcSiopVerifier = OidcSiopVerifier.newInstance(
        verifier = verifier,
        cryptoService = verifierCryptoService,
        relyingPartyUrl = postSuccessUrl,
    )

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<ApiItem> = lock.withLock {
        return authenticatedUsers.mapNotNull { it.value.toApiItem() }
    }

    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(@RequestBody id: String): ResponseEntity<ApiItem> = lock.withLock {
        return authenticatedUsers.remove(id)?.toApiItem()?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/siopv2/generateQrCode")
    @ResponseBody
    fun generateQrCode(@RequestBody request: QrCodeRequest): ResponseEntity<ByteArray> = lock.withLock {
        Napier.i("/siopv2/generateQrCode called with $request")
        return runBlocking {
            val requestUrl = ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
                .pathSegment("siopv2", "request")
                .queryParam("credentialType", request.credentialType)
                .queryParam("representation", request.representation)
                .queryParam("urlprefix", request.urlprefix)
                .queryParam("attributes", request.attributes)
                .toUriString()
            val qrCodeUrl = ServletUriComponentsBuilder.fromUriString(request.urlprefix)
                .queryParam("request_uri", requestUrl)
                .queryParam("client_id", postSuccessUrl)
                .queryParam("client_metadata_uri", metadataUrl)
                .toUriString()
            val bytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            Napier.i("/siopv2/generateQrCode returns with qrCodeUrl $qrCodeUrl")
            Napier.i("/siopv2/generateQrCode returns with bytes ${bytes.size}")
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        }
    }

    /**
     * Creates SIOPv2 request object, with response_mode=post
     */
    @ResponseBody
    @GetMapping("/siopv2/request")
    fun siopv2RequestObject(
        @RequestParam attributes: Collection<String>?,
        @RequestParam representation: String?,
        @RequestParam urlprefix: String?,
        @RequestParam credentialType: String?,
    ): ResponseEntity<String> = lock.withLock {
        Napier.i("/siopv2/request called with $urlprefix, $representation, $credentialType, $attributes")
        val state = createSafeState()
        val verifierProtocol = newVerifier()
        verifierProtocolMap[state] = verifierProtocol
        return runBlocking {
            val credentialScheme = credentialType?.let { AttributeIndex.resolveAttributeType(it) }
                ?: EuPidScheme
            val parsedRep = CredentialRepresentation.entries.firstOrNull { it.name == representation }
                ?: CredentialRepresentation.SD_JWT
            verifierProtocol.createAuthnRequestAsSignedRequestObject()
            val requestObjectJws = verifierProtocol.createAuthnRequestAsSignedRequestObject(
                requestOptions = OidcSiopVerifier.RequestOptions(
                    responseMode = OpenIdConstants.ResponseModes.DIRECT_POST,
                    representation = parsedRep,
                    state = state,
                    credentialScheme = credentialScheme,
                    requestedAttributes = attributes?.ifEmpty { null }?.toList(),
                ),
            ).getOrElse {
                Napier.w("/siopv2/request error", it)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
            }
            ResponseEntity.ok(requestObjectJws.serialize())
        }
    }

    /**
     * Returns signed metadata.
     */
    @ResponseBody
    @GetMapping("/siopv2/metadata")
    fun siopv2Metadata(): ResponseEntity<String> {
        Napier.i("/siopv2/metadata called")
        return runBlocking {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(verifierProtocol.createSignedMetadata().getOrThrow().payload.decodeToString())
        }
    }

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [siopv2RequestObject].
     */
    @PostMapping("/siopv2/postsuccess")
    fun siopv2PostSuccessPage(@RequestBody requestBody: String): ResponseEntity<String> = lock.withLock {
        Napier.i("/siopv2/postsuccess called with $requestBody")
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        return runBlocking {
            val user = validateSiopResponse(params)
            Napier.i("Storing user at ${user.apiItem.id}: $this")
            authenticatedUsers[user.apiItem.id] = user
            ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, customerSuccessUrl).build()
        }
    }

    private suspend fun validateSiopResponse(params: AuthenticationResponseParameters): Siop2User {
        Napier.i("validateSiopResponse with $params")
        val state = params.state ?: throw RuntimeException("Bad state")
        val verifierProtocol = verifierProtocolMap.remove(state) ?: throw RuntimeException("No Protocol")
        return when (val result = verifierProtocol.validateAuthnResponse(params)) {
            is OidcSiopVerifier.AuthnResponseResult.Success ->
                with(Siop2User.fromVerifiablePresentation(result.vp)) {
                    if (this == null) {
                        Napier.w("Cannot parse from VP: ${result.vp}")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from VP")
                    }
                    this
                }

            is OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt ->
                Siop2User.fromDisclosures(result.disclosures)

            is OidcSiopVerifier.AuthnResponseResult.SuccessIso ->
                Siop2User.fromMdoc(result.document)

            is OidcSiopVerifier.AuthnResponseResult.Error ->
                throw RuntimeException(result.reason)

            is OidcSiopVerifier.AuthnResponseResult.ValidationError ->
                throw RuntimeException("Validation failed for field: ${result.field}")
        }
    }

    private fun createSafeState(): String {
        var state: String
        val randomBytes = ByteArray(32)
        do {
            secureRandomGenerator.nextBytes(randomBytes)
            state = Base64.getEncoder().encodeToString(randomBytes)
        } while (verifierProtocolMap.containsKey(state))
        return state
    }

    private fun AuthenticatedPrincipal.toApiItem() = when (this) {
        is Siop2User -> this.apiItem
        else -> null
    }

}
