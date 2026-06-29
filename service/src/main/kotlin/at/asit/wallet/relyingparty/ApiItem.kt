package at.asit.wallet.relyingparty

import at.asitplus.openid.IdToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** This is the response to the website, displayed there as "successful authentication", see `result.js`. */
@Serializable
data class ApiItem(
    val id: String,
    val firstname: String?,
    val lastname: String?,
    val imageDataBase64: String?,
    val timestamp: Long,
    val idToken: IdToken?,
    val idTokenError: String?,
    val presentationError: String?,
    val credentials: Collection<ApiItemCredential>,
)

@Serializable
data class ApiItemCredential(
    val jwtCredential: JsonElement? = null,
    val allFields: JsonObject? = null,
    val credentialType: String? = null,
    val error: String? = null,
)

@Serializable
data class OpenId4VpSuccess(
    @SerialName("redirect_uri")
    val redirectUri: String,
)