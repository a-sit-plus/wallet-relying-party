package at.asit.wallet.relyingparty

import at.asitplus.dif.PresentationDefinition
import at.asitplus.iso.DeviceRequest
import at.asitplus.iso.DeviceRequestBase64UrlSerializer
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme
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
    val includeWrpac: Boolean = false,
    val selectedWrprcId: String? = null,
) {
}

@Serializable
data class TransactionRequestCredential(
    val credentialType: String? = null,
    val representation: String? = null,
    val attributes: List<String>? = null,
) {
    fun toRequestOptionsCredential() = resolveCredentialType().let { scheme ->
        (CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT).let { representation ->
            RequestOptionsCredential(
                credentialScheme = scheme,
                representation = representation,
                requestedOptionalAttributes = null,
                requestedAttributes = scheme.requestedAttributes(representation),
            )
        }
    }

    private fun resolveCredentialType(): CredentialScheme = credentialType?.let {
        AttributeIndex.resolveAttributeType(it)
            ?: AttributeIndex.resolveSdJwtAttributeType(it)
            ?: AttributeIndex.resolveIsoDoctype(it)
    } ?: EuPidScheme

    // if the credential is not selectively disclosable, request all attributes
    private fun CredentialScheme.requestedAttributes(
        representation: CredentialRepresentation,
    ): Set<String>? =
        if (!isSd() && representation == CredentialRepresentation.SD_JWT) mandatoryAttributes()
        else attributes?.ifEmpty { null }?.toSet()

    private fun CredentialScheme.mandatoryAttributes(): Set<String>? = when (this) {
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

    private fun CredentialScheme.isSd(): Boolean = when (this) {
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
