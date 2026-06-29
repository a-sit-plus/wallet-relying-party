package at.asit.wallet.relyingparty

import at.asitplus.KmmResult
import at.asitplus.openid.IdToken
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.validation.CredentialFreshnessSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.*
import at.asitplus.wallet.lib.agent.validation.common.EntityExpiredError
import at.asitplus.wallet.lib.agent.validation.common.EntityNotYetValidError
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.VcJwsVerificationResultWrapper
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifiedPresentationResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResultDCQL
import at.asitplus.wallet.lib.openid.VpTokenValidationResultPresentationExchange
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
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
data class User(
    val idToken: IdToken?,
    val idTokenError: String?,
    val credentials: Collection<ApiItemCredential>?,
    val presentationError: String?,
) : AuthenticatedPrincipal {
    @Transient
    val apiItem = ApiItem(
        // TODO: replace with more robust id as Json does not mandate an ordering of keys
        id = Json.encodeToString(this).sha256(),
        firstname = credentials?.firstNotNullOfOrNull { it.getGivenName() },
        lastname = credentials?.firstNotNullOfOrNull { it.getFamilyName() },
        imageDataBase64 = credentials?.firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        idToken = idToken,
        idTokenError = idTokenError,
        presentationError = presentationError,
        credentials = credentials ?: listOf(),
    )

    override fun getName(): String =
        listOfNotNull(apiItem.firstname, apiItem.lastname).joinToString(" ").let { "$it (${apiItem.id})" }

    override fun toString(): String = "User(apiItem=$apiItem)"
}

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

fun AuthnResponseResult.toUser() = User(
    idToken = idTokenValidationResult?.getOrNull(),
    idTokenError = idTokenValidationResult?.exceptionOrNull()?.message,
    credentials = vpTokenValidationResult?.getOrNull()?.presentations()?.flatMap {
        it.toApiItemCredentials()
    },
    presentationError = vpTokenValidationResult?.exceptionOrNull()?.message ?: when(val presentation = vpTokenValidationResult?.getOrNull()) {
        is VpTokenValidationResultDCQL -> presentation.submissionRequirementsValidationResult.exceptionOrNull()?.message
        is VpTokenValidationResultPresentationExchange -> null
        null -> null
    },
)

fun VpTokenValidationResult.presentations() = when (this) {
    is VpTokenValidationResultDCQL -> credentialQueryResponseValidations.flatMap {
        it.value
    }

    is VpTokenValidationResultPresentationExchange -> inputDescriptorResponseValidations.values
}

fun KmmResult<Verifier.VerifyPresentationResult>.toApiItemCredentials() = exceptionOrNull()?.let {
    listOf(ApiItemCredential(error = it.message))
} ?: when (val it = getOrThrow()) {
    is Verifier.VerifyPresentationResult.Success -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessIso -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessSdJwt -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessUnsigned -> it.toApiItemCredentials()
}

fun Verifier.VerifyPresentationResult.Success.toApiItemCredentials(): Collection<ApiItemCredential> =
    vp.toApiItemCredentials()

fun Iso180137AnnexCVerifiedPresentationResult.toUser() = User(
    idToken = null,
    idTokenError = null,
    presentationError = null,
    credentials = documents.map { it.toApiItemCredential() }
)

fun KmmResult<AuthnResponseResult>.convertToUser(): User = exceptionOrNull()?.let {
    throw RuntimeException("Failed: input", it)
} ?: getOrThrow().toUser()

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

fun Verifier.VerifyPresentationResult.SuccessUnsigned.toApiItemCredentials(): List<ApiItemCredential> =
    vc.toApiItemCredentials()

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

fun Verifier.VerifyPresentationResult.SuccessSdJwt.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(
        allFields = reconstructedJsonObject,
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

fun Verifier.VerifyPresentationResult.SuccessIso.toApiItemCredentials(): Collection<ApiItemCredential> =
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

