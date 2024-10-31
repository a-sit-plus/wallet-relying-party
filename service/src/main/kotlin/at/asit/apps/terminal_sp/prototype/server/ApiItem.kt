package at.asit.apps.terminal_sp.prototype.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiItem(
    val id: String,
    val firstname: String,
    val lastname: String,
    val imageDataBase64: String?,
    val timestamp: Long,
    val credentials: List<ApiItemCredential>
)

@Serializable
data class ApiItemCredential(
    val jwtCredential: JsonElement? = null,
    // TODO Make this a JSONObject?
    val allFields: Map<String, String> = mapOf(),
    val credentialType: String? = null,
)

@Serializable
data class OpenId4VpSuccess(
    @SerialName("redirect_uri")
    val redirectUri: String,
)