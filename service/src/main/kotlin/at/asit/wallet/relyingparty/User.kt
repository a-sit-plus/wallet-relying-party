package at.asit.wallet.relyingparty

import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.data.CredentialToJsonConverter
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.DcApiResponseResult
import at.asitplus.wallet.lib.openid.Iso180137AnnexCError
import at.asitplus.wallet.lib.openid.Iso180137AnnexCWrapper
import at.asitplus.wallet.lib.validation.CredentialChecks
import at.asitplus.wallet.lib.validation.PresentationValidationResult
import at.asitplus.wallet.lib.validation.ValidatedCredential
import at.asitplus.wallet.lib.data.VcDataModelConstants
import at.asitplus.wallet.lib.validation.ValidationDecision
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.springframework.security.core.AuthenticatedPrincipal
import java.time.Instant

@Serializable
data class User(
    val credentials: Collection<ApiItemCredential>?,
    val presentationError: String?,
    val validation: ValidationNodeView? = null,
) : AuthenticatedPrincipal {
    @Transient
    val apiItem = ApiItem(
        imageDataBase64 = credentials?.firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        presentationError = presentationError,
        credentials = credentials ?: listOf(),
        validation = validation,
    )

    override fun getName(): String =
        listOfNotNull(
            credentials?.firstNotNullOfOrNull { it.getGivenName() },
            credentials?.firstNotNullOfOrNull { it.getFamilyName() },
        ).joinToString(" ")

    override fun toString(): String = "User(apiItem=$apiItem)"
}

private fun String?.toImage() = this?.let { "data:image;base64,${it.replace("-", "+").replace("_", "/")}" }

private fun ApiItemCredential.getPortrait() = getClaim(EuPidDataElements.PORTRAIT)
    ?: getClaim(EuPidSdJwtDataElements.PORTRAIT)
    ?: getClaim(MobileDrivingLicenceDataElements.PORTRAIT)

private fun ApiItemCredential.getFamilyName() = getClaim(EuPidDataElements.FAMILY_NAME)
    ?: getClaim(EuPidSdJwtDataElements.FAMILY_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ApiItemCredential.getGivenName() = getClaim(EuPidDataElements.GIVEN_NAME)
    ?: getClaim(EuPidSdJwtDataElements.GIVEN_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ApiItemCredential.getClaim(claim: String) = allFields?.entries
    ?.firstOrNull { it.key == claim }?.value
    ?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

/**
 * The result of the wallet's response: the claims of every accepted presentation, the reason of every rejected one,
 * and the report with every check, as decided under the policy of the verifier.
 */
fun DcApiResponseResult.convertToUser(): User = when (this) {
    is AuthnResponseResult.Success -> User(
        credentials = presentationValidations.flatMap { (queryId, validations) ->
            validations.flatMap { it.toApiItemCredentials(queryId.string) }
        },
        presentationError = validationReport.rejectionSummary(),
        validation = validationReport.toView(
            childTitles = presentationValidations.flatMap { (queryId, validations) ->
                validations.map { "Presentation for query '${queryId.string}'" }
            },
        ),
    )

    // Wallet-controlled text, escaped by the frontend when displaying it
    is AuthnResponseResult.Error -> User(
        credentials = null,
        presentationError = listOfNotNull("Wallet error: ${error.error}", error.errorDescription).joinToString(", "),
    )

    is Iso180137AnnexCWrapper -> User(
        credentials = presentationValidations.flatMap { it.toApiItemCredentials(queryId = null) },
        presentationError = validationReport.rejectionSummary(),
        validation = validationReport.toView(
            childTitles = presentationValidations.indices.map { "Document ${it + 1}" },
        ),
    )

    is Iso180137AnnexCError -> User(
        credentials = null,
        presentationError = "Wallet error: device response status $status",
    )
}

/**
 * The credentials of an accepted presentation with their claims, or for a rejected one a single entry telling why,
 * typed by the credential type its first credential report names, if any.
 */
private fun PresentationValidationResult.toApiItemCredentials(
    queryId: String?,
): List<ApiItemCredential> = presentation?.credentials?.map {
    ApiItemCredential(
        queryId = queryId,
        allFields = it.claims(),
        credentialType = it.credentialIdentifier,
        decision = ValidationDecision.ACCEPTED,
    )
} ?: listOf(
    ApiItemCredential(
        queryId = queryId,
        credentialType = report.children.firstNotNullOfOrNull {
            (it.checks as? CredentialChecks)?.issuerTrust?.credentialIdentifier
        },
        decision = ValidationDecision.REJECTED,
        error = rejection()?.describe(),
    )
)

/**
 * The signed type of this credential: the `vct` of an SD-JWT VC, the `docType` of an ISO mdoc, or the only type of a
 * VC-JWT other than `VerifiableCredential`, `null` if it has several.
 */
private val ValidatedCredential.credentialIdentifier: String?
    get() = when (this) {
        is ValidatedCredential.SdJwtVc -> credential.verifiableCredentialType
        is ValidatedCredential.IsoMdoc -> issuerSigned.issuerAuth.payload?.docType
        is ValidatedCredential.VcJwt -> credential.vc.type
            .filter { it != VcDataModelConstants.VERIFIABLE_CREDENTIAL }
            .singleOrNull()
    }

/** Claims to display: the elements of all namespaces of an mdoc in one object, as type metadata lists them. */
private fun ValidatedCredential.claims(): JsonObject = CredentialToJsonConverter.toJsonElement(this).let { claims ->
    if (this is ValidatedCredential.IsoMdoc) buildJsonObject {
        claims.values.forEach { namespace -> namespace.jsonObject.forEach { put(it.key, it.value) } }
    } else claims
}
