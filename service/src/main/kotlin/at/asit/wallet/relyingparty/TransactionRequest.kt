package at.asit.wallet.relyingparty

import at.asitplus.dif.PresentationDefinition
import at.asitplus.openid.dcql.DCQLClaimsPathPointer
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequest(
    @SerialName("presentationMechanismIdentifier")
    @Serializable(with = WebUiPresentationMechanismEnumSelectionSerializer::class)
    val presentationMechanism: PresentationMechanismEnum = PresentationMechanismEnum.PresentationExchange,
    val presentationDefinition: PresentationDefinition? = null,
    val dcqlQuery: DCQLQuery? = null,
    val dcApiOrigin: String? = null,
    val includeWrpac: Boolean = false,
    val selectedWrprcId: Int? = null,
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

    suspend fun toRequestOptionsCredential() = RequestOptionsCredential(
        credentialScheme = resolveCredentialType(),
        representation = format,
        optionalAttributePaths = null,
        // All known credentials are selectively disclosable (per their type metadata), so request exactly what the
        // user selected.
        attributePaths = attributes?.ifEmpty { null }?.map { it.toClaimPath(format) }?.toSet(),
    )

    // EU PID (ISO) is the default credential when none is specified; its docType is the lookup identifier.
    private suspend fun resolveCredentialType(): at.asitplus.wallet.lib.data.CredentialScheme =
        AttributeIndex.resolveIdentifier(credentialType ?: "eu.europa.ec.eudi.pid.1", format)

    private fun String.toClaimPath(representation: CredentialRepresentation): DCQLClaimsPathPointer =
        when (representation) {
            // ISO mdoc data element identifiers are literal names;
            // single-segment paths are prefixed with the scheme's isoNamespace by VC-K
            CredentialRepresentation.ISO_MDOC -> DCQLClaimsPathPointer(this)
            // JSON-based credentials use dots as nested-claim shorthand, e.g. "address.formatted"
            else -> split(".").let { DCQLClaimsPathPointer(it.first(), *it.drop(1).toTypedArray()) }
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
