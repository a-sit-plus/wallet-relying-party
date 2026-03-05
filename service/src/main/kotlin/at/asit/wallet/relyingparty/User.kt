package at.asit.wallet.relyingparty

import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.validation.CredentialFreshnessSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.Mdoc
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.SdJwt
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.VcJws
import at.asitplus.wallet.lib.agent.validation.common.EntityExpiredError
import at.asitplus.wallet.lib.agent.validation.common.EntityNotYetValidError
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.VcJwsVerificationResultWrapper
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.iso.Iso180137AnnexCResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.Error
import at.asitplus.wallet.lib.openid.AuthnResponseResult.IdToken
import at.asitplus.wallet.lib.openid.AuthnResponseResult.Success
import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessIso
import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessSdJwt
import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessUnsigned
import at.asitplus.wallet.lib.openid.AuthnResponseResult.ValidationError
import at.asitplus.wallet.lib.openid.AuthnResponseResult.VerifiableDCQLPresentationValidationResults
import at.asitplus.wallet.lib.openid.AuthnResponseResult.VerifiablePresentationValidationResults
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
class User(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    override fun getName(): String = "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"

    override fun toString(): String = "User(apiItem=$apiItem)"

}

fun Collection<ApiItemCredential>.toUser() = User(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = firstNotNullOfOrNull { it.getGivenName() } ?: "N/A",
        lastname = firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A",
        imageDataBase64 = firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = this
    )
)

private fun String?.toImage() = this?.let { "data:image;base64,${it.replace("-", "+").replace("_", "/")}" }

private fun ApiItemCredential.getPortrait() =
    getClaim(MobileDrivingLicenceDataElements.PORTRAIT)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.PORTRAIT)
        ?: getClaim(EuPidScheme.Attributes.PORTRAIT)

private fun ApiItemCredential.getFamilyName() =
    getClaim(EuPidScheme.Attributes.FAMILY_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.FAMILY_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ApiItemCredential.getGivenName() =
    getClaim(EuPidScheme.Attributes.GIVEN_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.GIVEN_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ApiItemCredential.getClaim(claim: String) = allFields?.entries
    ?.firstOrNull { it.key == claim }?.value
    ?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

fun VerifiablePresentationValidationResults.toUser() = toApiItemCredentials().toUser()

fun VerifiablePresentationValidationResults.toApiItemCredentials() =
    validationResults.flatMap { it.toApiItemCredentials() }

fun AuthnResponseResult.toApiItemCredentials(): Collection<ApiItemCredential> = when (this) {
    is Error -> toApiItemCredentials()
    is IdToken -> toApiItemCredentials()
    is Success -> toApiItemCredentials()
    is SuccessIso -> toApiItemCredentials()
    is SuccessSdJwt -> toApiItemCredentials()
    is ValidationError -> toApiItemCredentials()
    is VerifiablePresentationValidationResults -> toApiItemCredentials()
    is VerifiableDCQLPresentationValidationResults -> this.allValidationResults.toApiItemCredentials()
    is SuccessUnsigned -> vc.toApiItemCredentials()
}

fun Error.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(error = reason + cause.let { ": " + it.message })
)

fun IdToken.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(error = "Got IdToken: ${this.idToken}")
)

fun ValidationError.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(error = field + cause.let { ": " + it.message })
)

fun VerifiableDCQLPresentationValidationResults.toUser(): User =
    this.allValidationResults.toApiItemCredentials().toUser()

fun Map<DCQLCredentialQueryIdentifier, List<AuthnResponseResult>>.toApiItemCredentials(): Collection<ApiItemCredential> =
    values.flatten().flatMap { it.toApiItemCredentials() }

fun Success.toApiItemCredentials(): Collection<ApiItemCredential> = vp.toApiItemCredentials()

fun Success.toUser() = vp.toApiItemCredentials().toUser()
fun Iso180137AnnexCResponseResult.Success.toUser() = vp.toApiItemCredentials().toUser()

fun VerifiablePresentationParsed.toApiItemCredentials(): List<ApiItemCredential> =
    freshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map {
            ApiItemCredential(
                jwtCredential = it.vcJws.vc.credentialSubject,
                credentialType = EuPidScheme.vcType,
            )
        }
    } ?: notVerifiablyFreshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { it.freshnessSummary }
            .map { it.toApiItemCredential() }
    } ?: invalidVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { ApiItemCredential(error = "Structure invalid: $it") }
    } ?: listOf(ApiItemCredential(error = "No result"))

fun Iso180137AnnexCResponseResult.SuccessUnsigned.toUser() = this.vc.toApiItemCredentials().toUser()
fun SuccessUnsigned.toUser() = toApiItemCredentials().toUser()
fun SuccessUnsigned.toApiItemCredentials(): List<ApiItemCredential> = vc.toApiItemCredentials()
fun VcJwsVerificationResultWrapper.toApiItemCredentials(): List<ApiItemCredential> = if (freshnessSummary.isFresh) {
    listOf(this).map {
        ApiItemCredential(
            jwtCredential = it.vcJws.vc.credentialSubject,
            credentialType = it.vcJws.vc.type.first(),
        )
    }
} else {
    listOf(freshnessSummary.toApiItemCredential())
}

fun CredentialFreshnessSummary.VcJws.toApiItemCredential(): ApiItemCredential =
    ApiItemCredential(error = errorMessage())

private fun EuPidCredential.toApiItemCredential(): ApiItemCredential =
    ApiItemCredential(
        jwtCredential = runCatching { vckJsonSerializer.encodeToJsonElement(this) }.getOrNull(),
        credentialType = EuPidScheme.vcType,
    )

fun SuccessSdJwt.toUser() = toApiItemCredentials().toUser()

fun SuccessSdJwt.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(
        allFields = reconstructed,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
        error = freshnessSummary.errorMessage()
    )
)

private fun CredentialTimelinessValidationSummary.errorMessage(): String? =
    if (isNotYetValid) detailsNotYetValid()
    else if (isExpired) detailsExpired()
    else null

fun CredentialTimelinessValidationSummary.detailsNotYetValid() = when (this) {
    is Mdoc -> details.msoTimelinessValidationSummary?.mdocNotYetValidError?.errorMessage()
    is SdJwt -> details.jwsNotYetValidError?.errorMessage()
    is VcJws -> details.jwsNotYetValidError?.errorMessage()
        ?: details.credentialNotYetValidError?.errorMessage()
}

private fun EntityNotYetValidError.errorMessage(): String =
    "Not yet valid: ${notBeforeTime.formatted()}"

fun CredentialTimelinessValidationSummary.detailsExpired() = when (this) {
    is Mdoc -> details.msoTimelinessValidationSummary?.mdocExpiredError?.errorMessage()
    is SdJwt -> details.jwsExpiredError?.errorMessage()
    is VcJws -> details.jwsExpiredError?.errorMessage()
        ?: details.credentialExpiredError?.errorMessage()
}

private fun EntityExpiredError.errorMessage(): String =
    "Expired at: ${expirationTime.formatted()}"

private fun kotlin.time.Instant.formatted(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
        date(LocalDate.Format { year(); char('-'); monthNumber(); char('-'); day() })
        char(' ')
        time(LocalTime.Format { hour(); char(':'); minute(); char(':'); second() })
    })

fun SuccessIso.toUser() = toApiItemCredentials().toUser()
fun Iso180137AnnexCResponseResult.SuccessIso.toUser() = toApiItemCredentials().toUser()

fun SuccessIso.toApiItemCredentials(): Collection<ApiItemCredential> = documents.map { it.toApiItemCredential() }
fun Iso180137AnnexCResponseResult.SuccessIso.toApiItemCredentials(): List<ApiItemCredential> =
    documents.map { it.toApiItemCredential() }

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

