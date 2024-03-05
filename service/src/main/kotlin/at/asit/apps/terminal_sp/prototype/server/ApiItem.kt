package at.asit.apps.terminal_sp.prototype.server

data class ApiItem(
    val id: String,
    val firstname: String,
    val lastname: String,
    val address: String,
    val imageDataBase64: String,
    val timestamp: Long
)