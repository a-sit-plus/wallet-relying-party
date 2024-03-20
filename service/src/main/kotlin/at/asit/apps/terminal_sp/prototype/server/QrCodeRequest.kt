package at.asit.apps.terminal_sp.prototype.server

data class QrCodeRequest(
    val credentialScheme: String,
    val attributes: List<String>,
)