package at.asit.wallet.relyingparty

import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.agent.validation.CredentialFreshnessSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary
import at.asitplus.wallet.lib.agent.validation.common.EntityExpiredError
import at.asitplus.wallet.lib.agent.validation.common.EntityNotYetValidError
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.*
import kotlinx.datetime.format.char
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
class OpenId4VpUser(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    override fun getName(): String = "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"

    override fun toString(): String = "OpenId4VpUser(apiItem=$apiItem)"

}

fun Collection<ApiItemCredential>.toOpenId4VpUser() = OpenId4VpUser(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = firstNotNullOfOrNull { it.getGivenName() } ?: "N/A",
        lastname = firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A",
        imageDataBase64 = firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = this
    )
)

fun ApiItemCredential.toOpenId4VpUser() = OpenId4VpUser(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = getGivenName() ?: "N/A",
        lastname = getFamilyName() ?: "N/A",
        imageDataBase64 = getPortrait()?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = listOf(this)
    )
)

private fun String?.toImage() = this?.let { "data:image;base64,${it.replace("-", "+").replace("_", "/")}" }

private fun ApiItemCredential.getPortrait() = getClaim(MobileDrivingLicenceDataElements.PORTRAIT)
    ?: getClaim(EuPidScheme.Attributes.PORTRAIT)

private fun ApiItemCredential.getFamilyName() = getClaim(EuPidScheme.Attributes.FAMILY_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ApiItemCredential.getGivenName() = getClaim(EuPidScheme.Attributes.GIVEN_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ApiItemCredential.getClaim(claim: String) = allFields?.entries
    ?.firstOrNull { it.key == claim }?.value
    ?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

fun VerifiablePresentationValidationResults.toApiItemCredentials(): Collection<ApiItemCredential> =
    validationResults.flatMap {
        when (it) {
            is Error -> listOf(it.toApiItemCredential())
            is IdToken -> listOf(it.toApiItemCredential())
            is Success -> it.vp.toApiItemCredential()
            is SuccessIso -> it.toApiItemCredentials()
            is SuccessSdJwt -> listOf(it.toApiItemCredential())
            is ValidationError -> listOf(it.toApiItemCredential())
            is VerifiablePresentationValidationResults -> it.toApiItemCredentials()
            is VerifiableDCQLPresentationValidationResults -> it.validationResults.toApiItemCredentials()
        }
    }

fun Map<DCQLCredentialQueryIdentifier, AuthnResponseResult>.toApiItemCredentials(): Collection<ApiItemCredential> =
    values.flatMap {
        when (it) {
            is Error -> listOf(it.toApiItemCredential())
            is IdToken -> listOf(it.toApiItemCredential())
            is Success -> listOfNotNull(it.vp.toApiItemCredential())
            is SuccessIso -> it.toApiItemCredentials()
            is SuccessSdJwt -> listOfNotNull(it.toApiItemCredential())
            is ValidationError -> listOf(it.toApiItemCredential())
            is VerifiableDCQLPresentationValidationResults -> it.validationResults.toApiItemCredentials()
            is VerifiablePresentationValidationResults -> listOfNotNull(it.toApiItemCredentials())
        }
    }.filterIsInstance<ApiItemCredential>()

fun Error.toApiItemCredential(): ApiItemCredential = ApiItemCredential(
    error = reason + cause?.let { ": " + it.message },
)

fun IdToken.toApiItemCredential(): ApiItemCredential = ApiItemCredential(
    error = "Got IdToken: ${this.idToken}"
)

fun ValidationError.toApiItemCredential(): ApiItemCredential = ApiItemCredential(
    error = field + cause?.let { ": " + it.message }
)

fun Map<DCQLCredentialQueryIdentifier, AuthnResponseResult>.toOpenId4VpUser(): OpenId4VpUser? =
    this.toApiItemCredentials().toOpenId4VpUser()

fun VerifiablePresentationParsed.toApiItemCredential(): List<ApiItemCredential> =
    freshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { it.vcJws.vc.credentialSubject }
            .filterIsInstance<EuPidCredential>()
            .map { it.toApiItemCredential() }
    } ?: notVerifiablyFreshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { it.freshnessSummary }
            .map { it.toApiItemCredential() }
    } ?: invalidVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { ApiItemCredential(error = "Structure invalid: $it") }
    } ?: listOf(ApiItemCredential(error = "No result"))

fun CredentialFreshnessSummary.VcJws.toApiItemCredential(): ApiItemCredential =
    ApiItemCredential(error = errorMessage())

private fun EuPidCredential.toApiItemCredential() =
    ApiItemCredential(
        jwtCredential = runCatching { vckJsonSerializer.encodeToJsonElement(this) }.getOrNull(),
        credentialType = EuPidScheme.vcType,
    )

fun SuccessSdJwt.toApiItemCredential() = ApiItemCredential(
    allFields = reconstructed,
    credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
    error = freshnessSummary.errorMessage()
)

private fun CredentialTimelinessValidationSummary.errorMessage(): String? =
    if (isNotYetValid) detailsNotYetValid()
    else if (isExpired) detailsExpired()
    else null

fun CredentialTimelinessValidationSummary.detailsNotYetValid() = when (this) {
    is CredentialTimelinessValidationSummary.Mdoc -> details.msoTimelinessValidationSummary?.mdocNotYetValidError?.errorMessage()
    is CredentialTimelinessValidationSummary.SdJwt -> details.jwsNotYetValidError?.errorMessage()
    is CredentialTimelinessValidationSummary.VcJws -> details.jwsNotYetValidError?.errorMessage()
        ?: details.credentialNotYetValidError?.errorMessage()
}

private fun EntityNotYetValidError.errorMessage(): String =
    "Not yet valid: ${notBeforeTime.formatted()}"

fun CredentialTimelinessValidationSummary.detailsExpired() = when (this) {
    is CredentialTimelinessValidationSummary.Mdoc -> details.msoTimelinessValidationSummary?.mdocExpiredError?.errorMessage()
    is CredentialTimelinessValidationSummary.SdJwt -> details.jwsExpiredError?.errorMessage()
    is CredentialTimelinessValidationSummary.VcJws -> details.jwsExpiredError?.errorMessage()
        ?: details.credentialExpiredError?.errorMessage()
}

private fun EntityExpiredError.errorMessage(): String =
    "Expired at: ${expirationTime.formatted()}"

private fun kotlinx.datetime.Instant.formatted(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
        date(LocalDate.Format { year();char('-');monthNumber();char('-');dayOfMonth() })
        char(' ')
        time(LocalTime.Format { hour();char(':');minute();char(':');second() })
    })

fun SuccessIso.toApiItemCredentials(): List<ApiItemCredential> = documents.map { it.toApiItemCredential() }

private fun IsoDocumentParsed.toApiItemCredential(): ApiItemCredential = ApiItemCredential(
    allFields = buildJsonObject {
        validItems.forEach {
            put(it.elementIdentifier, it.elementValue.toJsonElement())
        }
    },
    credentialType = mso.docType,
    error = freshnessSummary.errorMessage(),
)

private fun CredentialFreshnessSummary.SdJwt.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun CredentialFreshnessSummary.VcJws.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun CredentialFreshnessSummary.Mdoc.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun TokenStatusValidationResult.errorMessage(): String? = when (this) {
    is TokenStatusValidationResult.Invalid -> "Invalid: Token status is ${this.tokenStatus}"
    is TokenStatusValidationResult.Rejected -> "Rejected: Error is ${this.throwable.toString()}"
    is TokenStatusValidationResult.Valid -> null
}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }

