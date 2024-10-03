package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.openid.AuthenticationResponseParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.ClientIdScheme.CertificateSanDns
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import io.github.aakira.napier.Napier
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
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


@Controller
class ApiController(
    @Value("\${app.public-url}")
    private val publicUrl: String,
) {
    private val authenticatedUsers: MutableMap<String, AuthenticatedPrincipal> = HashMap()
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
    private val verifierProtocol: OidcSiopVerifier by lazy { runBlocking { newVerifier() } }

    private suspend fun newVerifier(): OidcSiopVerifier = OidcSiopVerifier(
        verifier = verifier,
        relyingPartyUrl = publicUrl.getDnsName(),
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = CertificateSanDns(listOf(verifierKeyMaterial.getCertificate()!!))
    )

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<ApiItem> = authenticatedUsers.mapNotNull { it.value.toApiItem() }

    @GetMapping("/api/self")
    @ResponseBody
    fun apiSelf(httpServletRequest: HttpServletRequest): ResponseEntity<ApiItem> {
        val attr = httpServletRequest.session.getAttribute(SIOP_2_USER)
        Napier.i("/api/self: $attr")
        return (attr as? Siop2User)?.apiItem?.let {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(it)
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(@RequestBody id: String): ResponseEntity<ApiItem> =
        authenticatedUsers.remove(id)?.toApiItem()?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping("/siopv2/generateQrCode")
    @ResponseBody
    fun generateQrCode(@RequestBody request: QrCodeRequest): ResponseEntity<ByteArray> = runBlocking {
        Napier.i("/siopv2/generateQrCode called with $request")
        val qrCodeUrl = buildQrCodeUrl(request)
        val bytes = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
        Napier.i("/siopv2/generateQrCode returns with qrCodeUrl $qrCodeUrl")
        Napier.i("/siopv2/generateQrCode returns with bytes ${bytes.size}")
        ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
    }

    @PostMapping("/siopv2/generateQrCodeUrl")
    @ResponseBody
    fun qrCodeUrl(@RequestBody request: QrCodeRequest): ResponseEntity<String> = runBlocking {
        Napier.i("/siopv2/generateQrCodeUrl called with $request")
        ResponseEntity.ok().body(buildQrCodeUrl(request))
    }

    private fun buildQrCodeUrl(request: QrCodeRequest) = ServletUriComponentsBuilder.fromUriString(request.urlprefix)
        .queryParam(
            "request_uri", ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
                .pathSegment("siopv2", "request")
                .queryParam(PARAM_CREDENTIALTYPE, request.credentialType)
                .queryParam(PARAM_REPRESENTATION, request.representation)
                .queryParam(PARAM_URLPREFIX, request.urlprefix)
                .queryParam(PARAM_ATTRIBUTES, request.attributes)
                .toUriString()
        )
        .queryParam("client_id", publicUrl.getDnsName())
        .queryParam("client_metadata_uri", metadataUrl)
        .toUriString()



    /**
     * Creates SIOPv2 request object, with response_mode=post
     */
    @ResponseBody
    @GetMapping("/siopv2/request")
    fun siopv2RequestObject(
        @RequestParam(name = PARAM_ATTRIBUTES) attributes: Collection<String>?,
        @RequestParam(name = PARAM_REPRESENTATION) representation: String?,
        @RequestParam(name = PARAM_URLPREFIX) urlprefix: String?,
        @RequestParam(name = PARAM_CREDENTIALTYPE) credentialType: String?,
    ): ResponseEntity<String> = runBlocking {
        Napier.i("/siopv2/request called with $urlprefix, $representation, $credentialType, $attributes")
        val state = createSafeState()
        val credentialScheme = credentialType?.let {
            AttributeIndex.resolveAttributeType(it)
                ?: AttributeIndex.resolveSdJwtAttributeType(it)
                ?: AttributeIndex.resolveIsoDoctype(it)
        } ?: EuPidScheme
        val parsedRep = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT
        val requestObjectJws = verifierProtocol.createAuthnRequestAsSignedRequestObject(
            requestOptions = OidcSiopVerifier.RequestOptions(
                state = state,
                responseMode = OpenIdConstants.ResponseMode.DIRECT_POST,
                responseUrl = postSuccessUrl,
                credentials = setOf(
                    OidcSiopVerifier.RequestOptionsCredential(
                        credentialScheme = credentialScheme,
                        representation = parsedRep,
                        requestedAttributes = attributes?.ifEmpty { null }?.toList(),
                    )
                ),
            ),
        ).getOrElse {
            Napier.w("/siopv2/request error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
        }
        ResponseEntity.ok(requestObjectJws.serialize())
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

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [siopv2RequestObject].
     */
    @PostMapping("/siopv2/postsuccess")
    fun siopv2PostSuccessPage(
        @RequestBody requestBody: String,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<String> = runBlocking {
        Napier.i("/siopv2/postsuccess called with $requestBody")
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        val user = validateSiopResponse(params)
        Napier.i("Storing user at ${user.apiItem.id}: $user")
        authenticatedUsers[user.apiItem.id] = user
        httpServletRequest.getSession(true).setAttribute(SIOP_2_USER, user)
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, customerSuccessUrl).build()
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
                result.document.toApiItemCredential().toSiop2User()

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

    private fun AuthenticatedPrincipal.toApiItem() = if (this is Siop2User) this.apiItem else null

}

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"


private const val SIOP_2_USER = "siop2user"
private const val PARAM_ATTRIBUTES = "attributes"
private const val PARAM_URLPREFIX = "urlprefix"
private const val PARAM_REPRESENTATION = "representation"
private const val PARAM_CREDENTIALTYPE = "credentialType"