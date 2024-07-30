package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.SelectiveDisclosureItem
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

class Siop2User(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {
    override fun toString(): String {
        return apiItem.toString()
    }

    companion object {
        fun fromVerifiablePresentation(presentation: VerifiablePresentationParsed): Siop2User? {
            val credentialSubjects = presentation.verifiableCredentials
                .map { it.vc.credentialSubject }
            val idAustriaCredential = credentialSubjects
                .filterIsInstance<IdAustriaCredential>()
                .firstOrNull()
            if (idAustriaCredential != null) {
                return idAustriaCredential.toSiop2User()
            }
            val euPidCredential = credentialSubjects
                .filterIsInstance<EuPidCredential>()
                .firstOrNull()
            if (euPidCredential != null) {
                return euPidCredential.toSiop2User()
            }
            return null
        }

        private fun IdAustriaCredential.toSiop2User() = Siop2User(
            ApiItem(
                id = bpk,
                firstname = firstname,
                lastname = lastname,
                address = mainAddress ?: "N/A",
                imageDataBase64 = portrait?.let { "data:image;base64," + it.encodeToString(Base64()) },
                timestamp = Instant.now().toEpochMilli(),
                allFields = mapOf(
                    "bpk" to this.bpk,
                    "firstname" to this.firstname,
                    "lastname" to this.lastname,
                    "dateOfBirth" to this.dateOfBirth.toString(),
                    "mainAddress" to this.mainAddress.toString(),
                    "ageOver14" to this.ageOver14.toString(),
                    "ageOver16" to this.ageOver16.toString(),
                    "ageOver18" to this.ageOver18.toString(),
                    "ageOver21" to this.ageOver21.toString(),
                    "vehicleRegistration" to this.vehicleRegistration.toString(),
                    "gender" to this.gender.toString(),
                )
            )
        )

        private fun EuPidCredential.toSiop2User() = Siop2User(
            ApiItem(
                id = id,
                firstname = givenName,
                lastname = familyName,
                address = residentAddress ?: "N/A",
                imageDataBase64 = null,
                timestamp = Instant.now().toEpochMilli(),
                allFields = mapOf(
                    "givenName" to this.givenName,
                    "familyName" to this.familyName,
                    "birthDate" to this.birthDate.toString(),
                    "issuanceDate" to this.issuanceDate.toString(),
                    "expiryDate" to this.expiryDate.toString(),
                    "issuingAuthority" to this.issuingAuthority,
                    "issuingCountry" to this.issuingCountry,
                )
            )
        )

        fun fromDisclosures(disclosures: List<SelectiveDisclosureItem>) = Siop2User(
            ApiItem(
                id = disclosures.getClaimValue(IdAustriaScheme.Attributes.BPK)
                    ?: Json.encodeToString<List<SelectiveDisclosureItem>>(disclosures).sha256(),
                firstname = disclosures.getClaimValue(IdAustriaScheme.Attributes.FIRSTNAME)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.GIVEN_NAME)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.GIVEN_NAME)
                    ?: disclosures.getClaimValue(PowerOfRepresentationDataElements.LEGAL_NAME)
                    ?: "N/A",
                lastname = disclosures.getClaimValue(IdAustriaScheme.Attributes.LASTNAME)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.FAMILY_NAME)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.FAMILY_NAME)
                    ?: "N/A",
                address = disclosures.getClaimValue(IdAustriaScheme.Attributes.MAIN_ADDRESS)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.RESIDENT_ADDRESS)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.RESIDENCE_ADDRESS)
                    ?: "N/A",
                imageDataBase64 = disclosures.getClaimValueBytesEncodedBase64(IdAustriaScheme.Attributes.PORTRAIT),
                timestamp = Instant.now().toEpochMilli(),
                allFields = disclosures
                    .filterNot { it.claimName == IdAustriaScheme.Attributes.PORTRAIT }
                    .associate { it.claimName to it.claimValue.toString() }
            )
        )

        fun fromMdoc(document: IsoDocumentParsed) = Siop2User(
            ApiItem(
                id = document.elementValue(IdAustriaScheme.Attributes.BPK)?.toString()
                    ?: Json.encodeToString<List<IssuerSignedItem>>(document.validItems).sha256(),
                firstname = document.elementValue(IdAustriaScheme.Attributes.FIRSTNAME)?.toString()
                    ?: document.elementValue(EuPidScheme.Attributes.GIVEN_NAME)?.toString()
                    ?: document.elementValue(MobileDrivingLicenceDataElements.GIVEN_NAME)?.toString()
                    ?: "N/A",
                lastname = document.elementValue(IdAustriaScheme.Attributes.LASTNAME)?.toString()
                    ?: document.elementValue(EuPidScheme.Attributes.FAMILY_NAME)?.toString()
                    ?: document.elementValue(MobileDrivingLicenceDataElements.FAMILY_NAME)?.toString()
                    ?: "N/A",
                address = document.elementValue(IdAustriaScheme.Attributes.MAIN_ADDRESS)?.toString()
                    ?: document.elementValue(EuPidScheme.Attributes.RESIDENT_ADDRESS)?.toString()
                    ?: "N/A",
                imageDataBase64 = document.getByteArray(MobileDrivingLicenceDataElements.PORTRAIT)
                    ?.let { "data:image;base64," + it.encodeToString(Base64()) },
                timestamp = Instant.now().toEpochMilli(),
                allFields = document.validItems
                    .filterNot { it.elementIdentifier == MobileDrivingLicenceDataElements.PORTRAIT }
                    .associate { it.elementIdentifier to it.elementValueToString() }
            )
        )

    }

    override fun getName(): String {
        return "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"
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
    if (claimValue is ByteArray)
        return claimValue
    val toString = claimValue.toString()
    if (toString.isEmpty())
        return null
    return runCatching { toString.decodeToByteArray(Base64()) }.getOrNull()
}

private fun IsoDocumentParsed.elementValue(elementIdentifier: String) =
    validItems.firstOrNull { it.elementIdentifier == elementIdentifier }?.elementValue
