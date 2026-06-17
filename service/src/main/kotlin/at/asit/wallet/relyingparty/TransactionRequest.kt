package at.asit.wallet.relyingparty

import at.asitplus.dif.PresentationDefinition
import at.asitplus.iso.DeviceRequest
import at.asitplus.iso.DeviceRequestBase64UrlSerializer
import at.asitplus.openid.dcql.DCQLClaimsPathPointer
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EU_PID_DOCTYPE
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequest(
    @SerialName("presentationMechanismIdentifier")
    @Serializable(with = WebUiPresentationMechanismEnumSelectionSerializer::class)
    val presentationMechanism: PresentationMechanismEnum = PresentationMechanismEnum.PresentationExchange,
    val presentationDefinition: PresentationDefinition? = null,
    val dcqlQuery: DCQLQuery? = null,
    @Serializable(with = DeviceRequestBase64UrlSerializer::class)
    val deviceRequest: DeviceRequest? = null,
)

@Serializable
data class TransactionRequestCredential(
    val credentialType: String? = null,
    val representation: String? = null,
    val attributes: List<String>? = null,
) {
    val format
        get() = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT

    suspend fun toRequestOptionsCredential() = resolveCredentialType().let { scheme ->
        RequestOptionsCredential(
            credentialScheme = scheme,
            representation = format,
            optionalAttributePaths = null,
            attributePaths = scheme.requestedAttributePaths(format),
        )
    }

    private suspend fun resolveCredentialType(): at.asitplus.wallet.lib.data.CredentialScheme =
        AttributeIndex.resolveIdentifier(credentialType ?: EU_PID_DOCTYPE, format)

    // if the credential is not selectively disclosable, request all attributes
    private fun at.asitplus.wallet.lib.data.CredentialScheme.requestedAttributePaths(
        representation: CredentialRepresentation,
    ): Set<DCQLClaimsPathPointer>? =
        (if (!isSd() && representation == CredentialRepresentation.SD_JWT) mandatoryAttributes()
        else attributes?.ifEmpty { null }?.toSet())
            ?.map { it.toClaimPath(representation) }?.toSet()

    private fun String.toClaimPath(representation: CredentialRepresentation): DCQLClaimsPathPointer =
        when (representation) {
            // ISO mdoc data element identifiers are literal names;
            // single-segment paths are prefixed with the scheme's isoNamespace by VC-K
            CredentialRepresentation.ISO_MDOC -> DCQLClaimsPathPointer(this)
            // JSON-based credentials use dots as nested-claim shorthand, e.g. "address.formatted"
            else -> split(".").let { DCQLClaimsPathPointer(it.first(), *it.drop(1).toTypedArray()) }
        }

    private fun at.asitplus.wallet.lib.data.CredentialScheme.mandatoryAttributes(): Set<String>? = when (this) {
        is EhicScheme -> with(EhicScheme.Attributes) {
            setOf(
                ISSUING_COUNTRY,
                PERSONAL_ADMINISTRATIVE_NUMBER,
                PREFIX_ISSUING_AUTHORITY,
                PREFIX_AUTHENTIC_SOURCE,
                DOCUMENT_NUMBER,
                DATE_OF_ISSUANCE,
                DATE_OF_EXPIRY,
                STARTING_DATE,
                ENDING_DATE,
            )
        }

        is TaxIdScheme -> TaxIdScheme.requiredClaims.toSet()
        is PowerOfRepresentationScheme -> PowerOfRepresentationDataElements.MANDATORY_ELEMENTS.toSet()
        else -> setOf()
    }

    private fun at.asitplus.wallet.lib.data.CredentialScheme.isSd(): Boolean = when (this) {
        is EhicScheme -> false
        is TaxIdScheme -> false
        is PowerOfRepresentationScheme -> false
        else -> true
    }
}

@Serializable
data class TransactionResponse(
    val profiles: Collection<TransactionProfile>,
)

@Serializable
data class TransactionProfile(
    val id: String,
    val name: String,
    val description: String,
    val label: String,
    val prefix: String,
    val png: String,
    val url: String,
    val dcApiUrl: String? = null,
    val supportedOptions: Set<SupportedOptions>,
) {
    override fun toString(): String {
        return "TransactionProfile(id='$id'," +
                " name='$name'," +
                " label='$label'," +
                " description='$description'," +
                " prefix='$prefix'," +
                " png='${png.take(16)}...'," +
                " url='$url'," +
                " dcApiUrl='$dcApiUrl'," +
                " supportedOptions='$supportedOptions')"
    }
}
