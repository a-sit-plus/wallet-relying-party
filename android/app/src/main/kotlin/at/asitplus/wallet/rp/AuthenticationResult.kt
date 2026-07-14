package at.asitplus.wallet.rp

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class AuthenticationResult(
    val portrait: ByteArray?,
    val error: String?,
    val credentials: List<Credential>,
) {
    data class Credential(val type: String, val error: String?, val claims: List<Claim>)
    data class Claim(val name: String, val value: String)

    companion object {
        fun parse(json: String): AuthenticationResult {
            val root = JSONObject(json)
            return AuthenticationResult(
                portrait = decodeImage(root.stringOrNull("imageDataBase64")),
                error = root.stringOrNull("idTokenError") ?: root.stringOrNull("presentationError"),
                credentials = root.optJSONArray("credentials").objects().map { credential ->
                    val fields = credential.optJSONObject("allFields")
                        ?: credential.optJSONObject("jwtCredential")
                        ?: JSONObject()
                    Credential(
                        type = credential.stringOrNull("credentialType") ?: "Credential",
                        error = credential.stringOrNull("error"),
                        claims = fields.keys().asSequence()
                            .filterNot { it.equals("portrait", ignoreCase = true) }
                            .map { Claim(it, display(fields.get(it))) }
                            .sortedBy(Claim::name)
                            .toList(),
                    )
                }.toList(),
            )
        }

        private fun decodeImage(value: String?): ByteArray? = value
            ?.substringAfter(',', value)
            ?.replace('-', '+')
            ?.replace('_', '/')
            ?.let { it.padEnd((it.length + 3) / 4 * 4, '=') }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

        private fun display(value: Any): String = when (value) {
            JSONObject.NULL -> "null"
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> value.toString()
        }
    }
}

private fun JSONObject.stringOrNull(name: String): String? = opt(name)
    ?.takeUnless { it == JSONObject.NULL }
    ?.toString()
    ?.takeIf(String::isNotEmpty)

private fun JSONArray?.objects(): Sequence<JSONObject> = sequence {
    if (this@objects == null) return@sequence
    for (index in 0 until length()) yield(getJSONObject(index))
}
