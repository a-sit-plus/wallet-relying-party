package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.CredentialMetadataLookup
import at.asitplus.wallet.sdjwt.SdJwtVcType

/**
 * The credentials this verifier knows about, each backed by an SD-JWT Type Metadata document hosted in the
 * [credentials-collection](https://github.com/a-sit-plus/credentials-collection) repository. Scheme and claim
 * information is derived from those documents at runtime (registration + UI); this catalog only lists which
 * documents exist and how to address them.
 */
object CredentialCatalog {

    const val BASE_URL = "https://raw.githubusercontent.com/a-sit-plus/credentials-collection/main"

    /**
     * @param vct the document's `vct` value, used as the registry key
     * @param fileName document file name within the collection
     * @param representation how this credential is requested and presented
     * @param isoDocType for ISO mdoc credentials, the `vck.isoDocType` used as the lookup identifier
     */
    data class Entry(
        val vct: String,
        val fileName: String,
        val representation: CredentialRepresentation,
        val isoDocType: String? = null,
    ) {
        val url get() = "$BASE_URL/$fileName"

        /** Identifier the UI sends and the registry resolves: the `vct` for SD-JWT, the docType for ISO mdoc. */
        val identifier get() = isoDocType ?: vct
    }

    val entries = listOf(
        Entry("urn:eudi:pid:1", "eu-pid-sdjwt.json", SD_JWT),
        Entry("urn:eudi:ehic:1", "ehic.json", SD_JWT),
        Entry("eu.europa.ec.eudi.cor.1", "certificate-of-residence.json", SD_JWT),
        Entry("urn:eu.europa.ec.eudi:cr:1", "company-registration.json", SD_JWT),
        Entry("urn:eu.europa.ec.eudi:hiid:1", "healthid.json", SD_JWT),
        Entry("urn:eu.europa.ec.eudi:por:1", "power-of-representation.json", SD_JWT),
        Entry("urn:eu.europa.ec.eudi:tax:1", "tax-id-credential.json", SD_JWT),
        Entry("eu.europa.ec.av.1", "age-verification.json", ISO_MDOC, isoDocType = "eu.europa.ec.av.1"),
        Entry("EuPid2023", "eu-pid.json", ISO_MDOC, isoDocType = "eu.europa.ec.eudi.pid.1"),
        Entry("org.iso.18013.5.1.mDL", "mdl.json", ISO_MDOC, isoDocType = "org.iso.18013.5.1.mDL"),
    )

    /** `vct` -> hosted document URL; the registry owns this map. */
    fun documentUrls(): MutableMap<SdJwtVcType, String> =
        entries.associate { SdJwtVcType(it.vct) to it.url }.toMutableMap()

    /** ISO mdoc docTypes have no direct `vct` fallback, so alias each to its document's `vct`. */
    fun aliases(): Map<CredentialMetadataLookup, SdJwtVcType> =
        entries.filter { it.representation == ISO_MDOC }
            .associate { CredentialMetadataLookup(it.representation, it.identifier) to SdJwtVcType(it.vct) }
}
