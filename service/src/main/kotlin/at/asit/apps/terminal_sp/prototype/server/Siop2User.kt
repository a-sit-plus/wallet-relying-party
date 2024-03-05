package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.SelectiveDisclosureItem
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.springframework.security.core.AuthenticatedPrincipal
import java.time.Instant

class Siop2User(
    private val name: String,
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    companion object {
        fun fromVerifiablePresentation(presentation: VerifiablePresentationParsed): Siop2User? {
            val idAustriaCredential = presentation.verifiableCredentials
                .map { it.vc.credentialSubject }
                .filterIsInstance<IdAustriaCredential>()
                .firstOrNull() ?: return null
            val authnInstant: Instant = Instant.now()
            val identifier = idAustriaCredential.bpk
            val name = idAustriaCredential.firstname + " " + idAustriaCredential.lastname
            val apiItem = ApiItem(
                id = identifier,
                firstname = idAustriaCredential.firstname,
                lastname = idAustriaCredential.lastname,
                address = idAustriaCredential.mainAddress ?: "N/A",
                imageDataBase64 = "data:image;base64," + idAustriaCredential.portrait?.encodeToString(Base64()),
                timestamp = authnInstant.toEpochMilli(),
            )
            return Siop2User(name, apiItem)
        }

        fun fromDisclosures(disclosures: List<SelectiveDisclosureItem>): Siop2User? {
            val authnInstant: Instant = Instant.now()
            val identifier = disclosures.getClaimValue(IdAustriaScheme.Attributes.BPK) ?: return null
            val apiItem = ApiItem(
                id = identifier,
                firstname = disclosures.getClaimValue(IdAustriaScheme.Attributes.FIRSTNAME) ?: "N/A",
                lastname = disclosures.getClaimValue(IdAustriaScheme.Attributes.LASTNAME) ?: "N/A",
                address = disclosures.getClaimValue(IdAustriaScheme.Attributes.MAIN_ADDRESS) ?: "N/A",
                imageDataBase64 = "data:image;base64," + disclosures.getClaimValueBytes(IdAustriaScheme.Attributes.PORTRAIT)
                    ?.encodeToString(Base64()),
                timestamp = authnInstant.toEpochMilli(),
            )
            val name = apiItem.firstname + " " + apiItem.lastname
            return Siop2User(name, apiItem)
        }

        fun fromMdoc(document: IsoDocumentParsed): Siop2User? {
            val authnInstant: Instant = Instant.now()
            val identifier = document.elementValue(IdAustriaScheme.Attributes.BPK)?.string ?: return null
            val apiItem = ApiItem(
                id = identifier,
                firstname = document.elementValue(IdAustriaScheme.Attributes.FIRSTNAME)?.string ?: "N/A",
                lastname = document.elementValue(IdAustriaScheme.Attributes.LASTNAME)?.string ?: "N/A",
                address = document.elementValue(IdAustriaScheme.Attributes.MAIN_ADDRESS)?.string ?: "N/A",
                imageDataBase64 = "data:image;base64," + document.elementValue(IdAustriaScheme.Attributes.PORTRAIT)?.bytes
                    ?.encodeToString(Base64()),
                timestamp = authnInstant.toEpochMilli(),
            )
            val name = apiItem.firstname + " " + apiItem.lastname
            return Siop2User(name, apiItem)
        }

        private fun List<SelectiveDisclosureItem>.getClaimValue(claimName: String) =
            firstOrNull { it.claimName == claimName }?.claimValue?.toString()

        private fun List<SelectiveDisclosureItem>.getClaimValueBytes(claimName: String): ByteArray? =
            firstOrNull { it.claimName == claimName }?.claimValue as ByteArray?

        private fun IsoDocumentParsed.elementValue(elementIdentifier: String) =
            validItems.firstOrNull { it.elementIdentifier == elementIdentifier }?.elementValue

    }

    override fun getName(): String {
        return name
    }

}