package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
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
    val presentationMechanism: PresentationMechanismEnum,
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
            RequestOptionsCredential(
                credentialScheme = scheme,
                representation = CredentialRepresentation.entries.firstOrNull { it.name == representation }
                    ?: CredentialRepresentation.SD_JWT,
                // if credential is not selectively disclosable, do not request any attributes
                requestedOptionalAttributes = if (scheme.isSd() == false) null
                    else attributes?.ifEmpty { null }?.toSet(),
            )
        }

    private fun CredentialScheme.isSd(): Boolean = when (this){
        is HealthIdScheme -> false
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
