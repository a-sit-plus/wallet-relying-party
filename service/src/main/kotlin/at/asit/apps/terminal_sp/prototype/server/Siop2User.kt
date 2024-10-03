package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.*
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
class Siop2User(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    companion object {
        fun VerifiablePresentationParsed.toSiop2User() =
            verifiableCredentials
                .map { it.vc.credentialSubject }
                .filterIsInstance<IdAustriaCredential>()
                .firstOrNull()?.toSiop2User()
                ?: verifiableCredentials
                    .map { it.vc.credentialSubject }
                    .filterIsInstance<EuPidCredential>()
                    .firstOrNull()?.toSiop2User()

        private fun IdAustriaCredential.toApiItemCredential() =
            ApiItemCredential(
                jwtCredential = kotlin.runCatching { vckJsonSerializer.encodeToJsonElement(this) }.getOrNull(),
                credentialType = IdAustriaScheme.vcType,
            )

        private fun IdAustriaCredential.toSiop2User() = Siop2User(
            ApiItem(
                id = bpk,
                firstname = firstname,
                lastname = lastname,
                imageDataBase64 = portrait?.let { "data:image;base64," + it.encodeToString(Base64()) },
                timestamp = Instant.now().toEpochMilli(),
                credentials = listOf(toApiItemCredential())
            )
        )

        private fun EuPidCredential.toApiItemCredential() =
            ApiItemCredential(
                jwtCredential = kotlin.runCatching { vckJsonSerializer.encodeToJsonElement(this) }.getOrNull(),
                credentialType = EuPidScheme.vcType,
            )

        private fun EuPidCredential.toSiop2User() = Siop2User(
            ApiItem(
                id = id,
                firstname = givenName,
                lastname = familyName,
                imageDataBase64 = null,
                timestamp = Instant.now().toEpochMilli(),
                credentials = listOf(toApiItemCredential())
            )
        )

        fun List<SelectiveDisclosureItem>.toApiItemCredential(sdJwt: VerifiableCredentialSdJwt) =
            ApiItemCredential(
                allFields = filterNot { it.claimName == IdAustriaScheme.Attributes.PORTRAIT }
                    .associate { it.claimName to it.claimValue.content },
                credentialType = sdJwt.verifiableCredentialType,
            )

        fun List<SelectiveDisclosureItem>.toSiop2User(sdJwt: VerifiableCredentialSdJwt) = Siop2User(
            ApiItem(
                id = getClaimValue(IdAustriaScheme.Attributes.BPK)
                    ?: Json.encodeToString<List<SelectiveDisclosureItem>>(this).sha256(),
                firstname = getClaimValue(IdAustriaScheme.Attributes.FIRSTNAME)
                    ?: getClaimValue(EuPidScheme.Attributes.GIVEN_NAME)
                    ?: getClaimValue(CertificateOfResidenceDataElements.GIVEN_NAME)
                    ?: getClaimValue(PowerOfRepresentationDataElements.LEGAL_NAME)
                    ?: "N/A",
                lastname = getClaimValue(IdAustriaScheme.Attributes.LASTNAME)
                    ?: getClaimValue(EuPidScheme.Attributes.FAMILY_NAME)
                    ?: getClaimValue(CertificateOfResidenceDataElements.FAMILY_NAME)
                    ?: "N/A",
                imageDataBase64 = getClaimValueBytesEncodedBase64(IdAustriaScheme.Attributes.PORTRAIT),
                timestamp = Instant.now().toEpochMilli(),
                credentials = listOf(toApiItemCredential(sdJwt))
            )
        )

        fun IsoDocumentParsed.toApiItemCredential() =
            ApiItemCredential(
                allFields = validItems
                    .filterNot { it.elementIdentifier == MobileDrivingLicenceDataElements.PORTRAIT }
                    .associate { it.elementIdentifier to it.elementValueToString() },
                credentialType = mso.docType,
            )

        fun IsoDocumentParsed.toSiop2User() = Siop2User(
            ApiItem(
                id = elementValue(IdAustriaScheme.Attributes.BPK)?.toString()
                    ?: Json.encodeToString<List<IssuerSignedItem>>(validItems).sha256(),
                firstname = elementValue(IdAustriaScheme.Attributes.FIRSTNAME)?.toString()
                    ?: elementValue(EuPidScheme.Attributes.GIVEN_NAME)?.toString()
                    ?: elementValue(MobileDrivingLicenceDataElements.GIVEN_NAME)?.toString()
                    ?: "N/A",
                lastname = elementValue(IdAustriaScheme.Attributes.LASTNAME)?.toString()
                    ?: elementValue(EuPidScheme.Attributes.FAMILY_NAME)?.toString()
                    ?: elementValue(MobileDrivingLicenceDataElements.FAMILY_NAME)?.toString()
                    ?: "N/A",
                imageDataBase64 = getByteArray(MobileDrivingLicenceDataElements.PORTRAIT)
                    ?.let { "data:image;base64," + it.encodeToString(Base64()) },
                timestamp = Instant.now().toEpochMilli(),
                credentials = listOf(toApiItemCredential())
            )
        )

    }

    override fun getName(): String {
        return "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"
    }

    override fun toString(): String {
        return "Siop2User(apiItem=$apiItem)"
    }

}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64())
}.getOrElse { this.hashCode().toString() }


private fun IsoDocumentParsed.getByteArray(s: String) =
    (elementValue(s) as? ByteArray)
        ?: runCatching { elementValue(s).toString().decodeToByteArray(Base64()) }.getOrNull()

private fun IssuerSignedItem.elementValueToString() =
    if (elementValue is Array<*>) (elementValue as Array<*>).contentToString() else elementValue.toString()

private fun List<SelectiveDisclosureItem>.getClaimValue(claimName: String) =
    firstOrNull { it.claimName == claimName }?.claimValue?.toString()

private fun List<SelectiveDisclosureItem>.getClaimValueBytesEncodedBase64(claimName: String) =
    getClaimValueBytes(claimName)?.let { "data:image;base64," + it.encodeToString(Base64()) }

private fun List<SelectiveDisclosureItem>.getClaimValueBytes(claimName: String): ByteArray? {
    val claimValue = firstOrNull { it.claimName == claimName }?.claimValue
    Napier.i("getClaimValueBytes for $claimName has $claimValue")
    return runCatching { claimValue?.contentOrNull?.decodeToByteArray(Base64()) }.getOrNull()
}

private fun IsoDocumentParsed.elementValue(elementIdentifier: String) =
    validItems.firstOrNull { it.elementIdentifier == elementIdentifier }?.elementValue
