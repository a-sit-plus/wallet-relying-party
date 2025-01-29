package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.data.*
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
class Siop2User(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    override fun getName(): String = "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"

    override fun toString(): String = "Siop2User(apiItem=$apiItem)"

}

fun List<ApiItemCredential>.toSiop2User() = Siop2User(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = firstNotNullOfOrNull { it.getGivenName() } ?: "N/A",
        lastname = firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A",
        imageDataBase64 = firstNotNullOfOrNull { it.getPortrait() }?.let { "data:image;base64,$it" },
        timestamp = Instant.now().toEpochMilli(),
        credentials = this
    )
)

fun ApiItemCredential.toSiop2User() = Siop2User(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = getGivenName() ?: "N/A",
        lastname = getFamilyName() ?: "N/A",
        imageDataBase64 = getPortrait()?.let { "data:image;base64,$it" },
        timestamp = Instant.now().toEpochMilli(),
        credentials = listOf(this)
    )
)

private fun ApiItemCredential.getPortrait() = getClaim(MobileDrivingLicenceDataElements.PORTRAIT)
    ?: getClaim(EuPidScheme.Attributes.PORTRAIT)

private fun ApiItemCredential.getFamilyName() = getClaim(EuPidScheme.Attributes.FAMILY_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ApiItemCredential.getGivenName() = getClaim(EuPidScheme.Attributes.GIVEN_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ApiItemCredential.getClaim(claim: String) = this.allFields?.entries?.firstOrNull { it.key == claim }?.value?.let {
    when (it) {
        is JsonPrimitive -> it.content
        else -> it.toString()
    }
}

fun VerifiablePresentationValidationResults.toApiItemCredentials() = validationResults.flatMap {
    when (it) {
        is Error -> listOf()
        is IdToken -> listOf()
        is Success -> listOf(it.vp.toApiItemCredential())
        is SuccessIso -> it.toApiItemCredentials()
        is SuccessSdJwt -> listOf(it.toApiItemCredential())
        is ValidationError -> listOf()
        is VerifiablePresentationValidationResults -> listOf()
    }
}.filterNotNull()

fun VerifiablePresentationParsed.toApiItemCredential() = verifiableCredentials
    .map { it.vc.credentialSubject }
    .filterIsInstance<EuPidCredential>()
    .firstOrNull()?.toApiItemCredential()

fun VerifiablePresentationParsed.toSiop2User() = verifiableCredentials
    .map { it.vc.credentialSubject }
    .filterIsInstance<EuPidCredential>()
    .firstOrNull()?.toApiItemCredential()?.toSiop2User()

private fun EuPidCredential.toApiItemCredential() =
    ApiItemCredential(
        jwtCredential = kotlin.runCatching { vckJsonSerializer.encodeToJsonElement(this) }.getOrNull(),
        credentialType = EuPidScheme.vcType,
    )

fun SuccessSdJwt.toApiItemCredential() =
    ApiItemCredential(
        allFields = reconstructed,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
    )

fun SuccessIso.toApiItemCredentials() = documents.map { doc ->
    ApiItemCredential(
        allFields = buildJsonObject {
            doc.validItems.forEach {
                put(it.elementIdentifier, it.elementValue.toJsonElement())
            }
        },
        credentialType = doc.mso.docType,
    )
}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }

private fun IssuerSignedItem.elementValueToString() = when (elementValue) {
    is ByteArray -> (elementValue as ByteArray).encodeToString(Base64())
    is Array<*> -> (elementValue as Array<*>).contentToString()
    else -> elementValue.toString()
}

