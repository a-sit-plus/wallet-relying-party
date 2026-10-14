package at.asit.wallet.relyingparty

import at.asitplus.wallet.lib.validation.ValidationDecision
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** This is the response to the website, displayed there as "successful authentication", see `result.js`. */
@Serializable
data class ApiItem(
    val imageDataBase64: String?,
    val timestamp: Long,
    /** Why the response of the wallet is not accepted, e.g. an error response, or a rejected validation. */
    val presentationError: String?,
    val credentials: Collection<ApiItemCredential>,
    /** Every check of the validation of the response of the wallet, `null` if it carried no presentation. */
    val validation: ValidationNodeView? = null,
)

/**
 * A credential the wallet presented: [allFields] holds its claims only if it is [ValidationDecision.ACCEPTED],
 * otherwise [error] tells why it is rejected.
 */
@Serializable
data class ApiItemCredential(
    /** The DCQL credential query the credential answers. */
    val queryId: String? = null,
    val allFields: JsonObject? = null,
    val credentialType: String? = null,
    val decision: ValidationDecision,
    val error: String? = null,
)
