package at.asit.wallet.relyingparty

import at.asitplus.etsi.ListOfTrustedEntities
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.TrustedCertificates
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.validation.CredentialTrustAnchors
import at.asitplus.wallet.lib.validation.CredentialTrustScope
import at.asitplus.wallet.lib.validation.CredentialTrustScopes
import at.asitplus.wallet.lib.validation.CredentialValidator
import at.asitplus.wallet.lib.validation.SignatureCheck
import at.asitplus.wallet.lib.validation.StatusValidator
import at.asitplus.wallet.lib.validation.ValidationPolicy
import at.asitplus.wallet.lib.validation.credentialTrustScope
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Validation of presentations of wallets with VC-K's [CredentialValidator]: issuers are trusted per credential type,
 * status list tokens are fetched with [StatusListTokenFetcher], under the [ValidationPolicy] configured in
 * [AppConfigurationProperties.validation].
 */
@Configuration
class CredentialValidationConfiguration {

    @Bean
    fun presentationPolicy(configuration: AppConfigurationProperties): ValidationPolicy =
        configuration.validation.toValidationPolicy()

    /** Trusted for every credential type, next to the lists of the European Commission. */
    @Bean
    fun asitRootCertificate(): X509Certificate = X509Certificate.decodeFromPem(
        checkNotNull(javaClass.getResource("/asit-root.pem")) { "Missing ASIT root certificate" }.readText()
    ).getOrThrow()

    @Bean
    fun credentialTrustAnchors(
        trustListCache: TrustListCache,
        configuration: AppConfigurationProperties,
        asitRootCertificate: X509Certificate,
    ): CredentialTrustAnchors = CredentialTrustScopes(
        credentialTrustScopes(
            identifiers = CredentialCatalog.entries.map { it.identifier },
            stages = configuration.trustListStages,
            asitRootCertificate = asitRootCertificate,
            lists = { url -> listOfNotNull(trustListCache.getPayload(url)?.loTe) },
        )
    )

    /**
     * Status lists are fetched while the wallet waits for its response to be acknowledged, so a slow server must not
     * hold it for long: its status is then reported as not determined.
     */
    @Bean
    fun statusListTokenResolver(): StatusListTokenResolver = StatusListTokenFetcher(
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 3_000
                requestTimeoutMillis = 5_000
            }
            install(ContentNegotiation) {
                json(joseCompliantSerializer)
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Napier.i(message = message, tag = "at.asitplus.http.status")
                    }
                }
                level = LogLevel.INFO
            }
        }
    )

    @Bean
    fun credentialValidator(
        credentialTrustAnchors: CredentialTrustAnchors,
        statusListTokenResolver: StatusListTokenResolver,
    ) = CredentialValidator(
        trustAnchors = credentialTrustAnchors,
        statusValidator = StatusValidator(
            statusListTokenResolver = statusListTokenResolver,
            // TODO Remove once Signum verifies the protected header of COSE_Sign1 as received
            signatureCheck = SignatureCheck(DeterministicCoseHeaderVerifySignature()),
        ),
    )
}

/**
 * One trust scope per attestation type, trusting its issuers through the issuance services, and the signers of its
 * status lists through the revocation services, of its list as published on [stages] (the [lists] at a URL, verified
 * by [TrustListService]), and through [asitRootCertificate] for both. Types with a list of the European Commission
 * (see [LoteProfile.fromSchemeIdentifier]) share one scope per list, every other type has a scope of its own on the
 * Pub-EAA providers list ([LoteProfile.EAA]).
 */
fun credentialTrustScopes(
    identifiers: Collection<String>,
    stages: List<LoTEStage>,
    asitRootCertificate: X509Certificate,
    lists: (String) -> Collection<ListOfTrustedEntities>,
): List<CredentialTrustScope> {
    val loteFilterService = LoTEFilterService()
    return identifiers.distinct().groupBy { LoteProfile.fromSchemeIdentifier(it) }.flatMap { (profile, ids) ->
        if (profile == null) ids.map { setOf(it) to LoteProfile.EAA }
        else listOf(ids.toSet() to profile)
    }.map { (ids, profile) ->
        val urls = stages.map { profile.fetchUrl(it) }
        val lote = loteFilterService.credentialTrustScope(
            credentialIdentifiers = ids,
            profile = profile,
            source = urls.joinToString(),
            lists = { urls.flatMap(lists) },
        )
        lote.copy(
            source = "$ASIT_ROOT_SOURCE, $profile list ${lote.source}",
            issuance = TrustedCertificates { lote.issuance() + asitRootCertificate },
            status = TrustedCertificates { lote.status() + asitRootCertificate },
        )
    }
}

private const val ASIT_ROOT_SOURCE = "A-SIT root"
