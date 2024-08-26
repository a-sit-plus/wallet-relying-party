package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.agent.RandomKeyPairAdapter
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
    private val verifierKeyAdapter = RandomKeyPairAdapter(extensions)
    private val verifier: VerifierAgent = VerifierAgent(verifierKeyAdapter)
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
    private val verifierProtocol: OidcSiopVerifier by lazy { newVerifier() }

    private fun newVerifier(): OidcSiopVerifier = OidcSiopVerifier.newInstance(
        verifier = verifier,
        relyingPartyUrl = publicUrl.getDnsName(),
        responseUrl = postSuccessUrl,
        x5c = listOf(verifierKeyAdapter.certificate!!),
    )

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<ApiItem> = authenticatedUsers.mapNotNull { it.value.toApiItem() }

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
                .queryParam("credentialType", request.credentialType)
                .queryParam("representation", request.representation)
                .queryParam("urlprefix", request.urlprefix)
                .queryParam("attributes", request.attributes)
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
        @RequestParam attributes: Collection<String>?,
        @RequestParam representation: String?,
        @RequestParam urlprefix: String?,
        @RequestParam credentialType: String?,
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
                responseMode = OpenIdConstants.ResponseMode.DIRECT_POST,
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
    fun siopv2PostSuccessPage(@RequestBody requestBody: String): ResponseEntity<String> = runBlocking {
        Napier.i("/siopv2/postsuccess called with $requestBody")
        val params: AuthenticationResponseParameters = requestBody.decodeFromPostBody()
        val user = validateSiopResponse(params)
        Napier.i("Storing user at ${user.apiItem.id}: $user")
        authenticatedUsers[user.apiItem.id] = user
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, customerSuccessUrl).build()
    }

    private suspend fun validateSiopResponse(params: AuthenticationResponseParameters): Siop2User {
        Napier.i("validateSiopResponse with $params")
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

            is OidcSiopVerifier.AuthnResponseResult.VerifiablePresentationValidationResults ->
                throw RuntimeException("Not expected VerifiablePresentationValidationResults: $result")
        }
    }

    private fun createSafeState(): String {
        return Base64.getEncoder().encodeToString(Random.nextBytes(32))
    }

    private fun AuthenticatedPrincipal.toApiItem() = when (this) {
        is Siop2User -> this.apiItem
        else -> null
    }

}

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"
