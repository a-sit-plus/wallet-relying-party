package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme.Attributes
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.ConstantIndex
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import qrcode.QRCode
import java.security.SecureRandom
import java.time.Instant
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
        private val walletUrl = "https://wallet.a-sit.at/mobile"
    }

    private val publicIndexUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .toUriString()
    }
    private val postSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2").pathSegment("postsuccess")
            .toUriString()
    }
    private val siopRequestUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2", "request").toUriString()
    }
    private val metadataUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("siopv2", "metadata").toUriString()
    }

    private val qrCodeSiopUrl by lazy {
        ServletUriComponentsBuilder.fromUriString(walletUrl)
            .queryParam("request_uri", siopRequestUrl)
            .queryParam("client_id", postSuccessUrl)
            .queryParam("client_metadata_uri", metadataUrl)
            .toUriString()
    }

    private val verifierProtocol: OidcSiopVerifier by lazy {
        newVerifier()
    }

    private fun newVerifier(): OidcSiopVerifier = OidcSiopVerifier.newInstance(
        verifier = verifier,
        cryptoService = verifierCryptoService,
        credentialScheme = IdAustriaScheme,
        relyingPartyUrl = postSuccessUrl,
    )

    @GetMapping("/api/qrcodesiop")
    @ResponseBody
    fun apiQrCodeSiop(): ResponseEntity<ByteArray> = lock.withLock {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(
            QRCode.ofSquares().build(qrCodeSiopUrl).render().getBytes()
        )
    }

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<ApiItem> = lock.withLock {
        return authenticatedUsers.mapNotNull { it.value.toApiItem(it.key) }
    }

    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(@RequestBody id: String): ResponseEntity<ApiItem> = lock.withLock {
        return authenticatedUsers.remove(id)?.toApiItem(id)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build<ApiItem>()
    }

    /**
     * Triggers login in browser with IDA, see [WebSecurityConfiguration]
     * Link contained in `customer.html`
     */
    @GetMapping(path = ["/oauth2"])
    fun customerLogin(@AuthenticationPrincipal user: OidcUser): ResponseEntity<String> = lock.withLock {
        authenticatedUsers[user.identifier] = user
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, publicIndexUrl).build()
    }

    /**
     * Redirects to Wallet URL with SIOPv2 authn request, with response_mode=post.
     * Link contained in `customer.html`
     */
    @GetMapping("/siopv2/start")
    fun siopv2StartPost(): ResponseEntity<String> = lock.withLock {
        val state = createSafeState()
        val verifierProtocol = newVerifier()
        verifierProtocolMap[state] = verifierProtocol

        return runBlocking {
            val location = verifierProtocol.createAuthnRequestUrl(
                walletUrl = walletUrl,
                representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
                responseMode = OpenIdConstants.ResponseModes.POST,
                state = state,
            )
            ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build()
        }
    }

    /**
     * Redirects to Wallet URL with SIOPv2 authn request, with response_mode=post, and SD-JWTs
     * Link contained in `customer.html`
     */
    @GetMapping("/siopv2/start-sd")
    fun siopv2StartSdJwtPost(): ResponseEntity<String> = lock.withLock {
        // TODO  Attributes.MAIN_ADDRESS
        val requestedAttributes =
            listOf(Attributes.PORTRAIT, Attributes.FIRSTNAME, Attributes.LASTNAME)
        val state = createSafeState()
        val verifierProtocol = newVerifier()
        verifierProtocolMap[state] = verifierProtocol

        return runBlocking {
            val location = verifierProtocol.createAuthnRequestUrl(
                walletUrl = walletUrl,
                representation = ConstantIndex.CredentialRepresentation.SD_JWT,
                requestedAttributes = requestedAttributes,
                responseMode = OpenIdConstants.ResponseModes.POST,
                state = state,
            )
            ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build()
        }
    }

    /**
     * Creates SIOPv2 request object, with response_mode=post
     *
     * URL contained in [qrCodeSiopUrl].
     */
    @ResponseBody
    @GetMapping("/siopv2/request")
    fun siopv2RequestObject(): ResponseEntity<String> = lock.withLock {
        // TODO Attributes.MAIN_ADDRESS
        val requestedAttributes =
            listOf(Attributes.PORTRAIT, Attributes.FIRSTNAME, Attributes.LASTNAME)
        val state = createSafeState()
        val verifierProtocol = newVerifier()
        verifierProtocolMap[state] = verifierProtocol
        return runBlocking {
            val location = verifierProtocol.createAuthnRequestUrlWithRequestObject(
                walletUrl = walletUrl,
                representation = ConstantIndex.CredentialRepresentation.SD_JWT,
                requestedAttributes = requestedAttributes,
                responseMode = OpenIdConstants.ResponseModes.POST,
                state = state,
            ).getOrElse {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
            }
            ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build()
        }
    }


    /**
     * Returns signed metadata.
     * URL contained in [qrCodeSiopUrl].
     */
    @ResponseBody
    @GetMapping("/siopv2/metadata")
    fun siopv2Metadata(): String {
        Napier.i("/siopv2/metadata called")
        return runBlocking {
            verifierProtocol.createSignedMetadata().getOrThrow().serialize()
        }
    }

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [siopv2StartPost] or [siopv2StartSdJwtPost]
     */
    @PostMapping("/siopv2/postsuccess")
    fun siopv2PostSuccessPage(@RequestBody requestBody: String): ResponseEntity<String> = lock.withLock {
        Napier.i("/siopv2/postsuccess called with $requestBody")
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        return runBlocking {
            validateSiopResponse(params)
        }
    }

    private suspend fun validateSiopResponse(params: AuthenticationResponseParameters): ResponseEntity<String> {
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
                    Napier.i("Storing user at ${apiItem.id}")
                    authenticatedUsers[apiItem.id] = this
                    ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, publicIndexUrl).build()
                }

            is OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt ->
                with(Siop2User.fromDisclosures(result.disclosures)) {
                    if (this == null) {
                        Napier.w("Cannot parse from disclosures: ${result.disclosures}")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from disclosures")
                    }
                    Napier.i("Storing user at ${apiItem.id}")
                    authenticatedUsers[apiItem.id] = this
                    ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, publicIndexUrl).build()
                }

            is OidcSiopVerifier.AuthnResponseResult.SuccessIso ->
                with(Siop2User.fromMdoc(result.document)) {
                    if (this == null) {
                        Napier.w("Cannot parse from document: ${result.document}")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse from document")
                    }
                    Napier.i("Storing user at ${apiItem.id}")
                    authenticatedUsers[apiItem.id] = this
                    ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, publicIndexUrl).build()
                }

            is OidcSiopVerifier.AuthnResponseResult.Error ->
                throw RuntimeException(result.reason)

            is OidcSiopVerifier.AuthnResponseResult.ValidationError ->
                throw RuntimeException("Validation failed for field: ${result.field}")
        }
    }

    private val AuthenticatedPrincipal.identifier: String
        get() = when (this) {
            is OidcUser -> this.subject
            is Siop2User -> apiItem.id
            else -> "unknown"
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

    private fun AuthenticatedPrincipal.toApiItem(index: String) = when (this) {
        is OidcUser -> this.toApiItem(index)
        is Siop2User -> this.apiItem
        else -> null
    }

    private fun OidcUser.toApiItem(index: String) = ApiItem(
        id = index,
        firstname = givenName,
        lastname = familyName,
        address = getClaimAsString("urn:eidgvat:attributes.mainAddress") ?: "N/A",
        imageDataBase64 = "data:image;base64," + getClaimAsString("org.iso.18013.5.1:portrait"),
        timestamp = (issuedAt ?: authenticatedAt ?: updatedAt ?: Instant.now()).toEpochMilli(),
    )
}
