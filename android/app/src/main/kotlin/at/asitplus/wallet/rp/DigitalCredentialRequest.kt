package at.asitplus.wallet.rp

import org.json.JSONObject

internal fun digitalCredentialRequestJson(serverResponse: String): String =
    JSONObject(serverResponse).getJSONObject("digital").toString()
