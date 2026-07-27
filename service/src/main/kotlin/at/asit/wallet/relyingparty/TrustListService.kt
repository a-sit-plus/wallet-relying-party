package at.asit.wallet.relyingparty

import at.asitplus.etsi.TrustListPayload
import at.asitplus.iso.DeviceRequest
import at.asitplus.iso.IssuerSigned
import at.asitplus.openid.dcql.DCQLIsoMdocCredentialMetadataAndValidityConstraints
import at.asitplus.openid.dcql.DCQLJwtVcCredentialMetadataAndValidityConstraints
import at.asitplus.openid.dcql.DCQLSdJwtCredentialMetadataAndValidityConstraints
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.leaf
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.etsi.LoTEFilterCriteria
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.LoTEServiceType
import at.asitplus.wallet.lib.etsi.isTrustedBy
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifiedPresentationResult
import at.asitplus.wallet.lib.jws.VerifyJwsObjectJades
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.springframework.stereotype.Service
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.*
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.springframework.scheduling.annotation.Scheduled

val asitRootPem = "-----BEGIN CERTIFICATE-----\n" +
        "MIICNzCCAd6gAwIBAgIUVKbs5o5e1jnILQPrKrsBnZbJj5EwCgYIKoZIzj0EAwIw\n" +
        "MTELMAkGA1UEBhMCQVQxDjAMBgNVBAoMBUEtU0lUMRIwEAYDVQQDDAlJQUNBIDIw\n" +
        "MjYwHhcNMjYwNDE2MTQ1NDQ1WhcNMjcwNDE2MTQ1NDQ1WjAxMQswCQYDVQQGEwJB\n" +
        "VDEOMAwGA1UECgwFQS1TSVQxEjAQBgNVBAMMCUlBQ0EgMjAyNjBZMBMGByqGSM49\n" +
        "AgEGCCqGSM49AwEHA0IABA7215fpBuEqE0AmnwgUoKMGCIZjnXMPZohMJKKrO0f/\n" +
        "84eg4bFLVUAM25Clukqbjr/Ol3Pa16LLhxQoSIupJx+jgdMwgdAwEgYDVR0TAQH/\n" +
        "BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwMQYDVR0fBCowKDAmoCSgIoYgaHR0\n" +
        "cDovL3dhbGxldC5hLXNpdC5hdC9jcmwvMS5jcmwwIgYDVR0SBBswGYYXaHR0cHM6\n" +
        "Ly93YWxsZXQuYS1zaXQuYXQwEwYDVR0gBAwwCjAIBgYEAI96AQEwHwYDVR0jBBgw\n" +
        "FoAUTXNbbT6FjuThGuNsHM5KMNSead4wHQYDVR0OBBYEFE1zW20+hY7k4RrjbBzO\n" +
        "SjDUnmneMAoGCCqGSM49BAMCA0cAMEQCIDMQ328z1NWGUK6wcLC8JmgTkKxt3Ycw\n" +
        "BapSKA9Qxhd6AiANUlRcM5BT5JKZL3yNSvUlERYXqcEYs50sxwE60SVkEw==\n" +
        "-----END CERTIFICATE-----\n"
@Service
class TrustListService(
    private val trustListCache: TrustListCache
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(joseCompliantSerializer)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.i(message = message, tag = "at.asitplus.http.trust")
                }
            }
            level = LogLevel.INFO
        }
    }

    private val asitIssuerCert = X509Certificate.decodeFromPem(asitRootPem).getOrThrow()
    private val loTeFilterService = LoTEFilterService()

    @PostConstruct
    fun initCacheBootstrap() = runBlocking {
        try {
            Napier.i("Initializing baseline Trust List sync...")
            refreshAll()
        } catch (e: Exception) {
            Napier.e("Failed to initialize baseline Trust List sync during startup. Continuing with empty/stale cache.", e)
        }
    }

    /**
     * Background routine executing once every hour.
     */
    @Scheduled(fixedRate = 3600000)
    fun scheduledRefresh() = runBlocking {
        refreshAll()
    }

    suspend fun refreshAll() {
        LoTEServiceType.defaultUrls.forEach { url ->
            syncSingleUrl(url)
        }
    }

    private suspend fun syncSingleUrl(url: String) {
        Napier.i("Fetching Trust List from: $url")
        val jws = httpClient.get(url) {
            accept(ContentType.Application.Json)
        }.body<String>().let {
            JwsCompact.parse<TrustListPayload>(it).getOrThrow()
        }
        VerifyJwsObjectJades().invoke(jws.first).getOrThrow()
        trustListCache.updatePayload(url, jws.second)
        Napier.i("Successfully verified and cached Trust List for URL: $url")
    }

    /**
     * Evaluates if a given issuer is trusted based on the internal root cert and LoTEs.
     */
    fun evaluateIssuer(
        issuer: X509Certificate,
        serviceType: LoTEServiceType
    ): TrustState = try {
        if (issuer.isTrustedBy(listOf(asitIssuerCert)).isSuccess) {
            return TrustState.TRUSTED
        }

        val criteria = LoTEFilterCriteria(expectedServiceType = serviceType)
        val allLoTes = trustListCache.getAll()

        val certificateList: List<X509Certificate> = allLoTes
            .flatMap { lote -> loTeFilterService.extractTrustedCertificates(lote.key, lote.value.loTe, criteria) }
            .mapNotNull { it.certificate }

        if (certificateList.isEmpty()) {
            return TrustState.UNTRUSTED
        }

        val validationResult = issuer.isTrustedBy(certificateList)
        if (validationResult.isSuccess) TrustState.TRUSTED else TrustState.UNTRUSTED
    } catch (e: Exception) {
        Napier.e("Failed to evaluate issuer trust status due to unexpected error", e)
        TrustState.UNKNOWN
    }

    fun evaluateCredentialIssuerTrust(
        leafCertificate: X509Certificate?,
        transaction: ApiController.Transaction
    ): TrustState {
        if (leafCertificate == null) return TrustState.UNKNOWN
        return evaluateIssuer(leafCertificate, extractLoTEServiceType(transaction))
    }

    private fun extractLoTEServiceType(transaction: ApiController.Transaction): LoTEServiceType =
        LoTEServiceType.fromSchemeIdentifier(
            when (transaction.presentationMechanism) {
                PresentationMechanismEnum.DCQL -> transaction.dcqlRequest?.extractSchemeIdentifier()
                PresentationMechanismEnum.DeviceRequest -> transaction.deviceRequest?.extractSchemeIdentifier()
                PresentationMechanismEnum.PresentationExchange -> transaction.presentationExchangeRequest?.extractSchemeIdentifier()
            }
        )
}

@Serializable
enum class TrustState {
    TRUSTED, UNTRUSTED, UNKNOWN
}

fun IssuerSigned.extractIssuerCertificate(): X509Certificate? =
    issuerAuth.let { auth ->
        (auth.unprotectedHeader?.certificateChain?.firstOrNull()
            ?: auth.protectedHeader.certificateChain?.firstOrNull())
            ?.let { X509Certificate.decodeFromDer(it) }
    }

fun AuthnResponseResult.extractIssuerCertificate(): X509Certificate? {
    val vpResult = this.vpTokenValidationResult?.getOrNull() ?: return null

    return vpResult.presentationResults.firstNotNullOfOrNull { presentationResultKmm ->
        val successResult = presentationResultKmm.getOrNull() ?: return@firstNotNullOfOrNull null
        when (successResult) {
            is Verifier.VerifyPresentationResult.SuccessSdJwt -> {
                successResult.sdJwtSigned.jws.jwsHeader.certificateChain?.leaf
            }
            is Verifier.VerifyPresentationResult.Success -> {
                successResult.vp.freshVerifiableCredentials.firstOrNull()?.vcJws?.issuer?.let {
                    X509Certificate.decodeFromPem(it)
                        .getOrNull()
                }
            }
            is Verifier.VerifyPresentationResult.SuccessUnsigned -> {
                X509Certificate.decodeFromPem(successResult.vc.vcJws.issuer).getOrNull()
            }
            is Verifier.VerifyPresentationResult.SuccessIso -> {
                successResult.documents.firstOrNull()?.document?.issuerSigned?.extractIssuerCertificate()
            }
        }
    }
}

fun Iso180137AnnexCVerifiedPresentationResult.extractIssuerCertificate(): X509Certificate? =
    documents.firstOrNull()?.document?.issuerSigned?.extractIssuerCertificate()

fun CredentialPresentationRequest.DCQLRequest.extractSchemeIdentifier() =
    when (val meta = this.dcqlQuery.credentials.firstOrNull()?.meta) {
        is DCQLIsoMdocCredentialMetadataAndValidityConstraints -> meta.doctypeValue
        is DCQLSdJwtCredentialMetadataAndValidityConstraints -> meta.vctValues.firstOrNull()
        is DCQLJwtVcCredentialMetadataAndValidityConstraints -> meta.typeValues.firstOrNull()?.firstOrNull()
        else -> null
    }

fun CredentialPresentationRequest.PresentationExchangeRequest.extractSchemeIdentifier() =
    presentationDefinition.inputDescriptors.firstOrNull()?.constraints?.fields?.firstNotNullOf { it.filter?.const.toString() }
        ?: presentationDefinition.inputDescriptors.firstOrNull()?.id

fun DeviceRequest.extractSchemeIdentifier() = this.docRequests.firstOrNull()?.itemsRequest?.value?.docType
