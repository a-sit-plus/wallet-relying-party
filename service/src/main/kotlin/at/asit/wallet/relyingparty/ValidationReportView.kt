package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.IdentifierListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.validation.CheckOutcome
import at.asitplus.wallet.lib.validation.CredentialChecks
import at.asitplus.wallet.lib.validation.DisclosedItemReference
import at.asitplus.wallet.lib.validation.NamedCheck
import at.asitplus.wallet.lib.validation.PresentationChecks
import at.asitplus.wallet.lib.validation.ProtocolResponseChecks
import at.asitplus.wallet.lib.validation.Requirement
import at.asitplus.wallet.lib.validation.StatusListTokenChecks
import at.asitplus.wallet.lib.validation.TimelinessException
import at.asitplus.wallet.lib.validation.TokenStatusException
import at.asitplus.wallet.lib.validation.TrustValidation
import at.asitplus.wallet.lib.validation.ValidationDecision
import at.asitplus.wallet.lib.validation.ValidationReport
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One node of a [ValidationReport] for display, e.g. a credential with its checks, and its children: the
 * presentations of a response, the credentials of a presentation, the status list tokens of a credential.
 */
@Serializable
data class ValidationNodeView(
    /** Kind of the artifact, e.g. `protocolResponse`, `presentation`, `credential`, `statusListToken`. */
    val kind: String,
    val title: String,
    val decision: ValidationDecision,
    val evaluatedAt: String,
    val checks: List<CheckView>,
    val children: List<ValidationNodeView>,
)

/** A check of a [ValidationNodeView], see [NamedCheck]. */
@Serializable
data class CheckView(
    val name: String,
    val label: String,
    val outcome: OutcomeView,
    /** How the policy counts the check: a failure of an optional check is recorded, but tolerated. */
    val requirement: Requirement,
    /** Whether the check does not reject the artifact. */
    val satisfied: Boolean,
    /** What the check refers to, e.g. the trust source or the status list, never a claim value. */
    val subject: String? = null,
    /** Why the check did not pass. */
    val detail: String? = null,
)

enum class OutcomeView { PASSED, FAILED, BLOCKED, NOT_APPLICABLE }

/**
 * This report as decided under the policy it has been validated with, titled [title], with [childTitles] for its
 * children in order, e.g. the DCQL credential query each presentation answers.
 */
fun ValidationReport.toView(
    title: String? = null,
    childTitles: List<String> = emptyList(),
): ValidationNodeView {
    val named = namedChecks
    val statusTokens = named.mapNotNull { it.statusMechanism }.map {
        it.token.toView("Status list ${it.reference.description()}")
    }
    return ValidationNodeView(
        kind = checks::class.simpleName.orEmpty().removeSuffix("Checks").replaceFirstChar { it.lowercase() },
        title = title ?: defaultTitle(),
        decision = decision,
        evaluatedAt = evaluatedAt.formatted(),
        checks = named.map { it.toView() },
        children = children.mapIndexed { index, child -> child.toView(childTitles.getOrNull(index)) } +
                statusTokens,
    )
}

/** Why this report is rejected, as one line, e.g. for clients that show no report. */
fun ValidationReport.rejectionSummary(): String? =
    if (decision == ValidationDecision.ACCEPTED) null
    else namedChecks.filterNot { it.satisfied }.joinToString("; ") { check ->
        listOfNotNull(check.label(), check.outcome.detail()).joinToString(": ")
    }.ifEmpty { null }

/** Describes why a check did not pass, without claim values. */
fun Throwable.describe(): String = when (this) {
    is TokenStatusException -> "Status is ${status.label()}"
    is TimelinessException -> when {
        notAfter != null && evaluatedAt > notAfter!! -> "Expired at ${notAfter!!.formatted()}"
        notBefore != null && evaluatedAt < notBefore!! -> "Not valid before ${notBefore!!.formatted()}"
        else -> message ?: "Not valid at ${evaluatedAt.formatted()}"
    }

    else -> message ?: this::class.simpleName ?: "Unknown error"
}

private fun ValidationReport.defaultTitle(): String = when (val checks = checks) {
    is ProtocolResponseChecks -> "Wallet response"
    is PresentationChecks -> "Presentation"
    is CredentialChecks -> listOfNotNull("Credential", checks.issuerTrust.credentialIdentifier).joinToString(" ")
    is StatusListTokenChecks -> "Status list token"
    else -> checks::class.simpleName.orEmpty().removeSuffix("Checks")
}

private fun NamedCheck.toView() = CheckView(
    name = name,
    label = label(),
    outcome = when (outcome) {
        CheckOutcome.Passed -> OutcomeView.PASSED
        is CheckOutcome.Failed -> OutcomeView.FAILED
        is CheckOutcome.Blocked -> OutcomeView.BLOCKED
        CheckOutcome.NotApplicable -> OutcomeView.NOT_APPLICABLE
    },
    requirement = requirement,
    satisfied = satisfied,
    subject = trust?.description()
        ?: statusMechanism?.let { mechanism ->
            listOfNotNull(
                mechanism.reference.description(),
                mechanism.observed?.let { "observed ${it.label()}" },
            ).joinToString(", ")
        }
        ?: disclosedItem?.description(),
    detail = outcome.detail(),
)

private fun CheckOutcome.detail(): String? = when (this) {
    CheckOutcome.Passed, CheckOutcome.NotApplicable -> null
    is CheckOutcome.Failed -> throwable.describe()
    is CheckOutcome.Blocked -> cause?.describe() ?: "Not evaluated: a prerequisite did not pass"
}

private fun TrustValidation.description(): String? = when {
    source != null -> listOfNotNull(credentialIdentifier, "trusted via $source").joinToString(", ")
    credentialIdentifier != null -> "$credentialIdentifier, no trust scope covers it"
    else -> null
}

private fun RevocationListInfo.description() = when (this) {
    is StatusListInfo -> "${uri.string}, index $index"
    is IdentifierListInfo -> uri.string
    else -> uri.string
}

private fun DisclosedItemReference.description() = when (this) {
    is DisclosedItemReference.SdJwt -> listOfNotNull("Disclosure $disclosureIndex", claimPath).joinToString(", ")
    is DisclosedItemReference.Mdoc -> "$namespace / $elementIdentifier, digest ID $digestId"
}

private fun TokenStatus.label() = when (this) {
    TokenStatus.Valid -> "VALID"
    TokenStatus.Invalid -> "INVALID"
    TokenStatus.Suspended -> "SUSPENDED"
    else -> "0x${value.toString(16)}"
}

/** Labels of the checks, see the fields of the [at.asitplus.wallet.lib.validation.ValidationChecks]. */
private val checkLabels = mapOf(
    "parsing" to "Parsing",
    "signature" to "Signature",
    "issuerSignature" to "Issuer signature",
    "issuerTrust" to "Issuer trust",
    "trust" to "Trust",
    "semantics" to "Credential semantics",
    "requiredClaims" to "Required claims",
    "holderBinding" to "Holder binding",
    "timeliness" to "Validity period",
    "status.claim" to "Status claim",
    "status.agreement" to "Agreement of status mechanisms",
    "proofTime" to "Time of holder proof",
    "challenge" to "Nonce",
    "audience" to "Audience",
    "presentationBinding" to "Presentation binding (sd_hash, transaction data)",
    "encryption" to "Response encryption",
    "expectedOrigin" to "Expected origin",
    "requestState" to "Request state",
    "submissionRequirements" to "Requested credentials",
    "retrieval" to "Retrieval",
    "signerTrust" to "Signer trust",
    "subject" to "Subject",
    "clientId" to "Client identifier",
    "nonce" to "Nonce",
    "digest" to "Digest",
    "structure" to "Structure",
)

private val disclosedItemName = Regex("""disclosedItems\[(\d+)]\.(\w+)""")
private val statusMechanismName = Regex("""status\.mechanisms\[(\d+)]""")

private fun NamedCheck.label(): String =
    checkLabels[name]
        ?: disclosedItemName.matchEntire(name)?.destructured?.let { (index, part) ->
            "Disclosed item ${index.toInt() + 1}: ${checkLabels[part] ?: part}"
        }
        ?: statusMechanismName.matchEntire(name)?.destructured?.let { (index) -> "Status mechanism ${index.toInt() + 1}" }
        ?: name

private fun Instant.formatted(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
        date(LocalDate.Format { year(); char('-'); monthNumber(); char('-'); day() })
        char(' ')
        time(LocalTime.Format { hour(); char(':'); minute(); char(':'); second() })
    })
