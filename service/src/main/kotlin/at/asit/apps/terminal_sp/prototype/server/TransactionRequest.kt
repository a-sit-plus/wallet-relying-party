package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
        requestedAttributes = attributes?.ifEmpty { null }?.toList(),
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
        requestedAttributes = attributes?.ifEmpty { null }?.toList(),
    )
}


@Serializable
data class TransactionResponse(
    @Serializable(ByteArrayToBase64Serializer::class)
    val qrCodePng: ByteArray,
    val qrCodeUrl: String,
    val id: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionResponse

        if (!qrCodePng.contentEquals(other.qrCodePng)) return false
        if (qrCodeUrl != other.qrCodeUrl) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qrCodePng.contentHashCode()
        result = 31 * result + qrCodeUrl.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

object ByteArrayToBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArrayToBase64", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray = decoder.decodeString().decodeToByteArray(Base64())

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(value.encodeToString(Base64()))
    }

}

@Serializable
data class Transaction(
    val id: String,
    val request: TransactionRequest
)