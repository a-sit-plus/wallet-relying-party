package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxId2025Scheme
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
                    // if credential is not selectively disclosable, do not request any attributes
                    requestedOptionalAttributes = scheme.requestedOptionalAttributes(representation),
                )
            }
        }

    private fun CredentialScheme.requestedOptionalAttributes(
        representation: CredentialRepresentation,
    ): Set<String>? =
        if (!isSd() && representation == CredentialRepresentation.SD_JWT) null
        else attributes?.ifEmpty { null }?.toSet()

    @Suppress("DEPRECATION")
    private fun CredentialScheme.isSd(): Boolean = when (this) {
        is HealthIdScheme -> false
        is EhicScheme -> false
        is at.asitplus.wallet.taxid.TaxIdScheme -> false
        is TaxId2025Scheme -> false
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
    val label: String,
    val prefix: String,
    val png: String,
    val url: String,
    val remoteWalletUrl: String,
) {
    override fun toString(): String {
        return "TransactionResponseQrCode(id='$id', name='$name', label='$label', prefix='$prefix', png='${png.take(16)}...', url='$url', remoteWalletUrl='$remoteWalletUrl')"
    }
}
