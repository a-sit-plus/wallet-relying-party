package at.asit.wallet.relyingparty

import at.asitplus.KmmResult
import at.asitplus.openid.IdToken
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.leaf
import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.validation.CredentialFreshnessSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.*
import at.asitplus.wallet.lib.agent.validation.common.EntityExpiredError
import at.asitplus.wallet.lib.agent.validation.common.EntityNotYetValidError
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.VcDataModelConstants.VERIFIABLE_CREDENTIAL
import at.asitplus.wallet.lib.data.VcJwsVerificationResultWrapper
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.DcApiResponseResult
import at.asitplus.wallet.lib.openid.Iso180137AnnexCWrapper
import at.asitplus.wallet.lib.openid.VpTokenValidationResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResultDCQL
import at.asitplus.wallet.lib.openid.VpTokenValidationResultPresentationExchange
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.security.core.AuthenticatedPrincipal
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
        imageDataBase64 = credentials?.firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        idToken = idToken,
        idTokenError = idTokenError,
        presentationError = presentationError,
        credentials = credentials ?: listOf(),
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

fun DcApiResponseResult.convertToUser(evaluateIssuerTrust: (ApiItemCredential) -> TrustState) =
    when (this) {
        is AuthnResponseResult -> toUser()
        is Iso180137AnnexCWrapper -> toUser()
    }.let { user ->
        user.copy(credentials = user.credentials?.map { credential ->
            credential.copy(trustState = evaluateIssuerTrust(credential))
        })
    }

fun AuthnResponseResult.toUser() = User(
    idToken = idTokenValidationResult?.getOrNull(),
    idTokenError = idTokenValidationResult?.exceptionOrNull()?.message,
    credentials = vpTokenValidationResult?.getOrNull()?.presentations()?.flatMap {
        it.toApiItemCredentials()
    },
    presentationError = vpTokenValidationResult?.exceptionOrNull()?.message ?: when (val presentation =
        vpTokenValidationResult?.getOrNull()) {
        is VpTokenValidationResultDCQL -> presentation.submissionRequirementsValidationResult.exceptionOrNull()?.message
        is VpTokenValidationResultPresentationExchange -> null
        null -> null
    },
)

fun VpTokenValidationResult.presentations() = when (this) {
    is VpTokenValidationResultDCQL -> credentialQueryResponseValidations.flatMap { it.value }
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

fun Iso180137AnnexCWrapper.toUser() = User(
    idToken = null,
    idTokenError = null,
    presentationError = null,
    credentials = documents.map {
        it.toApiItemCredential(it.document.issuerSigned.extractIssuerCertificate())
    }
)

fun VerifiablePresentationParsed.toApiItemCredentials(): List<ApiItemCredential> {
    val issuerCertificates = jws.payload.vp.verifiableCredential.mapNotNull { serializedCredential ->
        runCatching { JwsCompactTyped<VerifiableCredentialJws>(serializedCredential) }.getOrNull()?.let {
            it.payload.jwtId to it.jws.jwsHeader.certificateChain?.leaf
        }
    }.toMap()
    return buildList {
        addAll(freshVerifiableCredentials.map {
            ApiItemCredential(
                jwtCredential = it.vcJws.vc.credentialSubject,
                credentialType = it.vcJws.vc.type.filterNot { it == VERIFIABLE_CREDENTIAL }.firstOrNull(),
                issuerCertificate = issuerCertificates[it.vcJws.jwtId],
            )
        })
        addAll(notVerifiablyFreshVerifiableCredentials.map {
            ApiItemCredential(
                jwtCredential = it.vcJws.vc.credentialSubject,
                credentialType = it.vcJws.vc.type.filterNot { type -> type == VERIFIABLE_CREDENTIAL }.firstOrNull(),
                error = it.freshnessSummary.errorMessage(),
                issuerCertificate = issuerCertificates[it.vcJws.jwtId],
            )
        })
        addAll(invalidVerifiableCredentials.map { ApiItemCredential(error = "Structure invalid: $it") })
        if (isEmpty()) add(ApiItemCredential(error = "No result"))
    }
}

fun Verifier.VerifyPresentationResult.SuccessUnsigned.toApiItemCredentials(): List<ApiItemCredential> =
    vc.toApiItemCredentials()

fun VcJwsVerificationResultWrapper.toApiItemCredentials(): List<ApiItemCredential> = if (freshnessSummary.isFresh) {
    listOf(
        ApiItemCredential(
            jwtCredential = vcJws.vc.credentialSubject,
            credentialType = vcJws.vc.type.first(),
        )
    )
} else {
    listOf(
        ApiItemCredential(
            jwtCredential = vcJws.vc.credentialSubject,
            credentialType = vcJws.vc.type.first(),
            error = freshnessSummary.errorMessage(),
        )
    )
}

fun Verifier.VerifyPresentationResult.SuccessSdJwt.toApiItemCredentials(): Collection<ApiItemCredential> = listOf(
    ApiItemCredential(
        allFields = reconstructedJsonObject,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
        error = freshnessSummary.errorMessage(),
        issuerCertificate = sdJwtSigned.jws.jwsHeader.certificateChain?.leaf,
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
    documents.map { it.toApiItemCredential(it.document.issuerSigned.extractIssuerCertificate()) }

private fun IsoDocumentParsed.toApiItemCredential(issuerCertificate: X509Certificate?): ApiItemCredential = ApiItemCredential(
    allFields = buildJsonObject {
        validItems.forEach {
            put(it.elementIdentifier, it.elementValue.toJsonElement())
        }
    },
    credentialType = mso.docType,
    error = freshnessSummary.errorMessage(),
    issuerCertificate = issuerCertificate,
)

private fun at.asitplus.iso.IssuerSigned.extractIssuerCertificate(): X509Certificate? =
    (issuerAuth.unprotectedHeader?.certificateChain?.firstOrNull()
        ?: issuerAuth.protectedHeader.certificateChain?.firstOrNull())
        ?.let { X509Certificate.decodeFromDer(it) }

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
