package at.asit.apps.terminal_sp.prototype.server

data class QrCodeRequest(
    val credentialType: String,
    val presentationType: String,
    val attributes: List<String>,
)