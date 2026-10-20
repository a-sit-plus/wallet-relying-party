package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.validation.StatusPolicy
import at.asitplus.wallet.lib.validation.TrustPolicy
import at.asitplus.wallet.lib.validation.ValidationPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URL
import java.time.Duration
import kotlin.time.toKotlinDuration

@ConfigurationProperties(prefix = "app")
data class AppConfigurationProperties(
    /** Public URL of this instance, used for several URLs in messages sent to the Wallet. */
    val publicContext: URL = URL("http://localhost:8080/"),
    /** Key used for signing authn requests */
    val verifierKey: KeyConfiguration = KeyConfiguration(),
    /** How long pending wallet presentation transactions remain valid. */
    val transactionTtl: Duration = Duration.ofMinutes(30),
    /** How long validated demo results remain available through the result API. */
    val resultTtl: Duration = Duration.ofMinutes(30),
    /** Wallet relying party certificate configuration */
    val wrp: WrpConfigurationProperties? = null,
    /**
     * Stages of the European Commission's trust infrastructure to fetch the Lists of Trusted Entities from, combined
     * per credential type: by default development and acceptance, as most status list signers of PID providers are
     * only published on the development stage.
     */
    val trustListStages: List<LoTEStage> = listOf(LoTEStage.DEVELOPMENT, LoTEStage.ACCEPTANCE),
    /** Policy presentations of wallets are validated under, see [ValidationConfigurationProperties]. */
    val validation: ValidationConfigurationProperties = ValidationConfigurationProperties(),
)

fun URL.appendPath(path: String): String = UriComponentsBuilder.fromUri(toURI()).path(path).toUriString()

/**
 * The [ValidationPolicy] for presentations of wallets: strict by default, i.e. issuers and signers of status lists
 * have to be trusted, a status is checked if the credential has one, and credentials have to be valid at the time of
 * the presentation. Relax it for test setups; parsing, signatures and holder binding are never relaxed.
 */
data class ValidationConfigurationProperties(
    /** Whether issuers of credentials have to be authorized by the trust anchors of the credential type. */
    val trust: TrustRequirement = TrustRequirement.REQUIRE_AUTHORIZED_SIGNER,
    /** Whether the status of credentials is checked. */
    val status: StatusRequirement = StatusRequirement.VALIDATE_IF_PRESENT,
    /** Whether signers of status list tokens have to be authorized by the status anchors of the credential type. */
    val statusSignerTrust: TrustRequirement = TrustRequirement.REQUIRE_AUTHORIZED_SIGNER,
    /** Token statuses that are accepted, `0` being valid (draft-ietf-oauth-status-list, 7.1). */
    val acceptedStatus: Set<Int> = setOf(TokenStatus.Specification.VALID.toInt()),
    /** Whether expired or not yet valid credentials are rejected. */
    val requireTimeliness: Boolean = true,
    /** Leeway for every time check, also for the time of holder proofs. */
    val timeLeeway: Duration = Duration.ofMinutes(5),
) {
    fun toValidationPolicy(): ValidationPolicy {
        val accepted = acceptedStatus.map { TokenStatus(it.toUInt()) }.toSet()
        return ValidationPolicy(
            trust = trust.toTrustPolicy(),
            status = when (status) {
                StatusRequirement.SKIP -> StatusPolicy.Skip
                StatusRequirement.VALIDATE_IF_PRESENT ->
                    StatusPolicy.ValidateIfPresent(statusSignerTrust.toTrustPolicy(), accepted)

                StatusRequirement.REQUIRE_CLAIM -> StatusPolicy.RequireClaim(statusSignerTrust.toTrustPolicy(), accepted)
            },
            timeLeeway = timeLeeway.toKotlinDuration(),
            requireTimeliness = requireTimeliness,
        )
    }
}

enum class TrustRequirement {
    /** The signer has to be authorized by trust anchors. */
    REQUIRE_AUTHORIZED_SIGNER,

    /** Only the signature is checked, the signer is reported, but not required to be trusted. */
    INTEGRITY_ONLY,
    ;

    fun toTrustPolicy(): TrustPolicy = when (this) {
        REQUIRE_AUTHORIZED_SIGNER -> TrustPolicy.RequireAuthorizedSigner
        INTEGRITY_ONLY -> TrustPolicy.IntegrityOnly
    }
}

enum class StatusRequirement {
    /** The status is not checked. */
    SKIP,

    /** The status is checked if the credential has one. */
    VALIDATE_IF_PRESENT,

    /** Every credential has to have a status, which is checked. */
    REQUIRE_CLAIM,
}

data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
    val keystore: KeyStoreConfiguration? = null,
)

data class KeyFileConfiguration(
    val privateKey: URI,
    val publicKey: URI?,
    val certificate: URI?,
)

data class KeyStoreConfiguration(
    val path: URI,
    val type: String,
    val provider: String? = null,
    val password: String? = null,
    val alias: String,
    val aliasPassword: String? = null,
)

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
}

data class WrpConfigurationProperties(
    val ac: List<WrpacConfiguration> = listOf(),
    val rc: List<WrprcConfiguration> = listOf(),
)

data class WrpacConfiguration(
    val label: String,
    val keystore: KeyStoreConfiguration,
)

/** One registration of the relying party, as JWT and/or CWT, at least one of [path] and [cwtPath] is required. */
data class WrprcConfiguration(
    val label: String,
    /** The WRPRC as JWT, sent in OpenID4VP `verifier_info`; a CWT found here is used as if set in [cwtPath]. */
    val path: URI? = null,
    /** The WRPRC as CWT (COSE_Sign1, binary or base64-encoded), sent in ISO 18013-7 Annex C `euWrprc`. */
    val cwtPath: URI? = null,
)
