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
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

class Siop2User(
    private val name: String,
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

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

        private fun IdAustriaCredential.toSiop2User(): Siop2User {
            val apiItem = ApiItem(
                id = bpk,
                firstname = firstname,
                lastname = lastname,
                address = mainAddress ?: "N/A",
                imageDataBase64 = "data:image;base64," + portrait?.encodeToString(Base64()),
                timestamp = Instant.now().toEpochMilli(),
            )
            return Siop2User("$firstname $lastname", apiItem)
        }

        private fun EuPidCredential.toSiop2User(): Siop2User {
            val apiItem = ApiItem(
                id = id,
                firstname = givenName,
                lastname = familyName,
                address = this.residentAddress ?: "N/A",
                imageDataBase64 = "",
                timestamp = Instant.now().toEpochMilli(),
            )
            return Siop2User("$givenName $familyName", apiItem)
        }

        fun fromDisclosures(disclosures: List<SelectiveDisclosureItem>): Siop2User {
            val apiItem = ApiItem(
                id = disclosures.getClaimValue(IdAustriaScheme.Attributes.BPK)
                    ?: Json.encodeToString<List<SelectiveDisclosureItem>>(disclosures).sha256(),
                firstname = disclosures.getClaimValue(IdAustriaScheme.Attributes.FIRSTNAME)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.GIVEN_NAME)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.GIVEN_NAME)
                    ?: disclosures.getClaimValue(PowerOfRepresentationDataElements.LEGAL_NAME)
                    ?: "N/A",
                lastname = disclosures.getClaimValue(IdAustriaScheme.Attributes.LASTNAME)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.FAMILY_NAME)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.GIVEN_NAME)
                    ?: "N/A",
                address = disclosures.getClaimValue(IdAustriaScheme.Attributes.MAIN_ADDRESS)
                    ?: disclosures.getClaimValue(EuPidScheme.Attributes.RESIDENT_ADDRESS)
                    ?: disclosures.getClaimValue(CertificateOfResidenceDataElements.RESIDENCE_ADDRESS)
                    ?: "N/A",
                imageDataBase64 = disclosures.getClaimValueBytes(IdAustriaScheme.Attributes.PORTRAIT)
                    ?.let { "data:image;base64," + it.encodeToString(Base64()) }
                    ?: "",
                timestamp = Instant.now().toEpochMilli(),
            )
            return Siop2User("${apiItem.firstname} ${apiItem.lastname}", apiItem)
        }

        fun fromMdoc(document: IsoDocumentParsed): Siop2User {
            val apiItem = ApiItem(
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
                imageDataBase64 = (document.elementValue(IdAustriaScheme.Attributes.PORTRAIT) as? ByteArray)
                    ?.let { "data:image;base64," + it.encodeToString(Base64()) }
                    ?: "",
                timestamp = Instant.now().toEpochMilli(),
            )
            return Siop2User("${apiItem.firstname} ${apiItem.lastname}", apiItem)
        }

        private fun List<SelectiveDisclosureItem>.getClaimValue(claimName: String) =
            firstOrNull { it.claimName == claimName }?.claimValue?.toString()

        private fun List<SelectiveDisclosureItem>.getClaimValueBytes(claimName: String): ByteArray? {
            val claimValue = firstOrNull { it.claimName == claimName }?.claimValue
            if (claimValue is ByteArray)
                return claimValue
            return runCatching { claimValue.toString().decodeToByteArray(Base64()) }.getOrNull()
        }

        private fun IsoDocumentParsed.elementValue(elementIdentifier: String) =
            validItems.firstOrNull { it.elementIdentifier == elementIdentifier }?.elementValue

    }

    override fun getName(): String {
        return name
    }

}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64())
}.getOrElse { this.hashCode().toString() }
