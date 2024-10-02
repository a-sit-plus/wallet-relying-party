package at.asit.apps.terminal_sp.prototype.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiItem(
    val id: String,
    val firstname: String,
    val lastname: String,
    val imageDataBase64: String?,
    val timestamp: Long,
    val jwtCredential: JsonElement? = null,
    val allFields: Map<String, String> = mapOf(),
    val credentialType: String? = null,
)