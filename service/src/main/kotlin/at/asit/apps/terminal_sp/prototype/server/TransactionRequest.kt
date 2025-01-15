package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequest(
    val credentialType: String? = null,
    val representation: String? = null,
    val simple: Boolean = false,
    val attributes: Collection<String>? = null,
    val credentials: List<TransactionRequestCredential>? = null,
) {
    fun toRequestOptionsCredential() = OidcSiopVerifier.RequestOptionsCredential(
        credentialScheme = credentialType
            ?.let { AttributeIndex.resolveCredential(it)?.first }
            ?: EuPidScheme,
        representation = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT,
        requestedOptionalAttributes = attributes?.ifEmpty { null }?.toList(),
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
    fun toRequestOptionsCredential() = OidcSiopVerifier.RequestOptionsCredential(
        credentialScheme = credentialType
            ?.let { AttributeIndex.resolveCredential(it)?.first }
            ?: EuPidScheme,
        representation = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT,
        requestedOptionalAttributes = attributes?.ifEmpty { null }?.toList(),
    )
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
    val remoteWalletUrl: String
) {
    override fun toString(): String {
        return "TransactionResponseQrCode(id='$id', name='$name', label='$label', prefix='$prefix', png='${png.take(16)}...', url='$url', remoteWalletUrl='$remoteWalletUrl')"
    }
}
