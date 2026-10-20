package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.validation.CheckOutcome.Blocked
import at.asitplus.wallet.lib.validation.CheckOutcome.Failed
import at.asitplus.wallet.lib.validation.CheckOutcome.NotApplicable
import at.asitplus.wallet.lib.validation.CheckOutcome.Passed
import at.asitplus.wallet.lib.validation.CredentialChecks
import at.asitplus.wallet.lib.validation.DisclosedItemReference
import at.asitplus.wallet.lib.validation.DisclosedItemValidation
import at.asitplus.wallet.lib.validation.HolderBindingPolicy
import at.asitplus.wallet.lib.validation.PresentationChecks
import at.asitplus.wallet.lib.validation.ProtocolResponseChecks
import at.asitplus.wallet.lib.validation.RequiredClaimsValidation
import at.asitplus.wallet.lib.validation.Requirement
import at.asitplus.wallet.lib.validation.StatusListTokenChecks
import at.asitplus.wallet.lib.validation.StatusMechanismValidation
import at.asitplus.wallet.lib.validation.StatusValidation
import at.asitplus.wallet.lib.validation.TimelinessException
import at.asitplus.wallet.lib.validation.TimelinessValidation
import at.asitplus.wallet.lib.validation.TokenStatusException
import at.asitplus.wallet.lib.validation.TrustPolicy
import at.asitplus.wallet.lib.validation.TrustValidation
import at.asitplus.wallet.lib.validation.ValidationDecision
import at.asitplus.wallet.lib.validation.ValidationReport
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ValidationReportViewTest {

    private val evaluatedAt = Instant.fromEpochSeconds(1_800_000_000)
    private val strict = ValidationConfigurationProperties().toValidationPolicy()
    private val relaxed = ValidationConfigurationProperties(requireTimeliness = false).toValidationPolicy()
    private val statusList = StatusListInfo(index = 7U, uri = UniformResourceIdentifier("https://example.com/status/1"))

    private val token = ValidationReport.statusListToken(
        StatusListTokenChecks(
            retrieval = Passed,
            parsing = Passed,
            signature = Passed,
            signerTrust = TrustValidation(Passed, credentialIdentifier = "urn:eudi:pid:1", source = "PID list"),
            subject = Passed,
            timeliness = Passed,
        ),
        TrustPolicy.RequireAuthorizedSigner,
        evaluatedAt,
    )

    private val expired = TimelinessException(
        "expired",
        evaluatedAt = evaluatedAt,
        notBefore = null,
        notAfter = evaluatedAt - 1.days,
    )

    private fun credentialChecks(
        timeliness: TimelinessValidation = TimelinessValidation(Passed, details = null),
        mechanism: StatusMechanismValidation = StatusMechanismValidation(statusList, token, Passed, TokenStatus.Valid),
    ) = CredentialChecks(
        parsing = Passed,
        issuerSignature = Passed,
        issuerTrust = TrustValidation(Passed, credentialIdentifier = "urn:eudi:pid:1", source = "PID list"),
        semantics = Passed,
        disclosedItems = listOf(
            DisclosedItemValidation(DisclosedItemReference.SdJwt(0, "given_name"), Passed, Passed, Passed),
        ),
        requiredClaims = RequiredClaimsValidation(NotApplicable),
        holderBinding = NotApplicable,
        timeliness = timeliness,
        status = StatusValidation(Passed, listOf(mechanism), agreement = Passed),
    )

    private fun response(credential: CredentialChecks, policy: at.asitplus.wallet.lib.validation.ValidationPolicy) =
        ValidationReport.protocolResponse(
            ProtocolResponseChecks(
                parsing = Passed,
                encryption = Passed,
                expectedOrigin = NotApplicable,
                requestState = Passed,
                submissionRequirements = Passed,
            ),
            listOf(
                ValidationReport.presentation(
                    PresentationChecks(Passed, Passed, Passed, Passed, Passed, Passed),
                    listOf(ValidationReport.credential(credential, policy, evaluatedAt)),
                    evaluatedAt,
                )
            ),
            evaluatedAt,
        )

    @Test
    fun `the tree shows response, presentation, credential, and status list token`() {
        val view = response(credentialChecks(), strict).toView(childTitles = listOf("Presentation for query 'pid'"))

        assertEquals("protocolResponse", view.kind)
        assertEquals("Wallet response", view.title)
        assertEquals(ValidationDecision.ACCEPTED, view.decision)
        val presentation = view.children.single()
        assertEquals("Presentation for query 'pid'", presentation.title)
        assertEquals("Presentation binding (sd_hash, transaction data)", presentation.checks.last().label)
        val credential = presentation.children.single()
        assertEquals("credential", credential.kind)
        assertEquals("Credential urn:eudi:pid:1", credential.title)
        assertEquals("urn:eudi:pid:1, trusted via PID list", credential.check("issuerTrust").subject)
        assertEquals("Disclosed item 1: Digest", credential.check("disclosedItems[0].digest").label)
        assertEquals("Disclosure 0, given_name", credential.check("disclosedItems[0].digest").subject)
        assertEquals(
            "https://example.com/status/1, index 7, observed VALID",
            credential.check("status.mechanisms[0]").subject,
        )
        val statusListToken = credential.children.single()
        assertEquals("statusListToken", statusListToken.kind)
        assertEquals("Status list https://example.com/status/1, index 7", statusListToken.title)
        assertNull(view.rejectionSummary())
    }

    @Test
    fun `an expired credential is rejected by the strict policy, and tolerated by a relaxed one`() {
        val checks = credentialChecks(timeliness = TimelinessValidation(Failed(expired), details = null))

        val rejected = response(checks, strict).toView().children.single().children.single()
        assertEquals(ValidationDecision.REJECTED, rejected.decision)
        rejected.check("timeliness").let {
            assertEquals(OutcomeView.FAILED, it.outcome)
            assertEquals(Requirement.REQUIRED, it.requirement)
            assertFalse(it.satisfied)
            assertTrue(it.detail!!.startsWith("Expired at "), it.detail)
        }

        val tolerated = response(checks, relaxed).toView().children.single().children.single()
        assertEquals(ValidationDecision.ACCEPTED, tolerated.decision)
        tolerated.check("timeliness").let {
            assertEquals(OutcomeView.FAILED, it.outcome)
            assertEquals(Requirement.OPTIONAL, it.requirement)
            assertTrue(it.satisfied)
        }
    }

    @Test
    fun `a revoked credential names its status`() {
        val revoked = StatusMechanismValidation(
            statusList, token, Failed(TokenStatusException(TokenStatus.Invalid)), TokenStatus.Invalid,
        )
        val report = ValidationReport.credential(credentialChecks(mechanism = revoked), strict, evaluatedAt)

        val check = report.toView().check("status.mechanisms[0]")
        assertEquals("Status mechanism 1", check.label)
        assertEquals("Status is INVALID", check.detail)
        assertEquals("Status mechanism 1: Status is INVALID", report.rejectionSummary())
    }

    @Test
    fun `a blocked check without cause says it was not evaluated`() {
        val report = ValidationReport.credential(
            credentialChecks().copy(holderBinding = Blocked()),
            strict.copy(holderBinding = HolderBindingPolicy.RequireExpectedKey),
            evaluatedAt,
        )

        assertEquals("Not evaluated: a prerequisite did not pass", report.toView().check("holderBinding").detail)
    }

    @Test
    fun `the report is serialized with the result`() {
        val validation = response(credentialChecks(), strict).toView()
        val json = joseCompliantSerializer.encodeToJsonElement(
            ApiItem.serializer(),
            User(credentials = emptyList(), presentationError = null, validation = validation).apiItem,
        ).jsonObject

        assertEquals(
            validation,
            joseCompliantSerializer.decodeFromJsonElement(ValidationNodeView.serializer(), json.getValue("validation")),
        )
    }

    private fun ValidationNodeView.check(name: String) = checks.single { it.name == name }

    private fun ValidationNodeView.rejectionSummary() = takeIf { decision == ValidationDecision.REJECTED }
}
