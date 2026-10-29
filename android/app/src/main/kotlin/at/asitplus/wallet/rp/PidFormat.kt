package at.asitplus.wallet.rp

import org.json.JSONArray
import org.json.JSONObject

/**
 * The format the EU PID is requested in over ISO/IEC 18013-7 Annex C: as ISO mdoc, or as SD-JWT VC in the ISO device
 * request and response as proposed by WG10 N2933, which the wallet has to support.
 */
internal enum class PidFormat(
    val label: String,
    private val credentialType: String,
    private val representation: String,
    private val attributes: List<String>,
    /** The remote presentation case of the relying party requesting the PID in this format over Annex C. */
    val remoteCaseId: String,
) {
    MDOC(
        label = "mdoc",
        credentialType = "eu.europa.ec.eudi.pid.1",
        representation = "ISO_MDOC",
        attributes = listOf(
            "family_name",
            "given_name",
            "birth_date",
            "nationality",
            "expiry_date",
            "issuing_authority",
            "issuing_country",
        ),
        remoteCaseId = "6",
    ),
    SD_JWT(
        label = "SD-JWT",
        credentialType = "urn:eudi:pid:1",
        representation = "SD_JWT",
        attributes = listOf(
            "family_name",
            "given_name",
            "birthdate",
            "nationalities",
            "date_of_expiry",
            "issuing_authority",
            "issuing_country",
        ),
        remoteCaseId = "5",
    );

    /** The credential to request, as `utilities/buildCredentialQueries` of the relying party expects it. */
    fun credentialJson(): JSONObject = JSONObject()
        .put("credentialType", credentialType)
        .put("representation", representation)
        .put("attributes", JSONArray(attributes))
}
