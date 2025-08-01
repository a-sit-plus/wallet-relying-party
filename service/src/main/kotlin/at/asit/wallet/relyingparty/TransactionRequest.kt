package at.asit.wallet.relyingparty

import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequest(
    val credentialType: String? = null,
    val representation: String? = null,
    val simple: Boolean = false,
    @SerialName("presentationMechanismIdentifier")
    @Serializable(with = WebUiPresentationMechanismEnumSelectionSerializer::class)
    val presentationMechanism: PresentationMechanismEnum = PresentationMechanismEnum.PresentationExchange,
    val attributes: Collection<String>? = null,
    val credentials: List<TransactionRequestCredential>? = null,
) {
    fun toRequestOptionsCredential() = RequestOptionsCredential(
        credentialScheme = credentialType
            ?.let { AttributeIndex.resolveCredential(it)?.first }
            ?: EuPidScheme,
        representation = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT,
        requestedOptionalAttributes = attributes?.ifEmpty { null }?.toSet(),
    )

    fun toRequestOptionsCredentials() = credentials?.let { credentials.map { it.toRequestOptionsCredential() }.toSet() }
        ?: setOf(toRequestOptionsCredential())
}

@Serializable
data class TransactionRequestCredential(
    val credentialType: String? = null,
    val representation: String? = null,
    val attributes: List<String>? = null,
) {
    fun toRequestOptionsCredential() =
        (credentialType?.let { AttributeIndex.resolveCredential(it)?.first } ?: EuPidScheme).let { scheme ->
            (CredentialRepresentation.entries.firstOrNull { it.name == representation }
                ?: CredentialRepresentation.SD_JWT).let { representation ->
                RequestOptionsCredential(
                    credentialScheme = scheme,
                    representation = representation,
                    requestedOptionalAttributes = scheme.optionalAttributes(representation),
                    requestedAttributes = scheme.requestedAttributes(representation),
                )
            }
        }

    // if the credential is not selectively disclosable, do not request any attributes
    private fun CredentialScheme.optionalAttributes(
        representation: CredentialRepresentation,
    ): Set<String>? =
        if (!isSd() && representation == CredentialRepresentation.SD_JWT) null
        else attributes?.ifEmpty { null }?.toSet()

    // if the credential is not selectively disclosable, request all attributes
    private fun CredentialScheme.requestedAttributes(
        representation: CredentialRepresentation,
    ): Set<String>? =
        if (!isSd() && representation == CredentialRepresentation.SD_JWT) mandatoryAttributes()
        else null

    private fun CredentialScheme.mandatoryAttributes(): Set<String>? {
        return when (this) {
            is HealthIdScheme -> with(HealthIdScheme.Attributes) {
                setOf(
                    EXPIRY_DATE,
                    ISSUE_DATE,
                    ISSUING_AUTHORITY,
                    ISSUING_COUNTRY,
                )
            }

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
    }

    private fun CredentialScheme.isSd(): Boolean = when (this) {
        is HealthIdScheme -> false
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
    val remoteWalletUrl: String,
) {
    override fun toString(): String {
        return "TransactionResponseQrCode(id='$id'," +
                " name='$name'," +
                " label='$label'," +
                " description='$description'," +
                " prefix='$prefix'," +
                " png='${png.take(16)}...'," +
                " url='$url'," +
                " remoteWalletUrl='$remoteWalletUrl')"
    }
}
