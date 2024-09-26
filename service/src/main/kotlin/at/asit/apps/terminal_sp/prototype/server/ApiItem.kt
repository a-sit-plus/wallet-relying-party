package at.asit.apps.terminal_sp.prototype.server

import kotlinx.serialization.Serializable

@Serializable
data class ApiItem(
    val id: String,
    val firstname: String,
    val lastname: String,
    val address: String,
    val imageDataBase64: String?,
    val timestamp: Long,
    val allFields: Map<String, String>,
)