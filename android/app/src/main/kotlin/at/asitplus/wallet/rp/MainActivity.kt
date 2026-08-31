package at.asitplus.wallet.rp

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.credentials.CredentialManager
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val authLauncher = AuthTabIntent.registerActivityResultLauncher(this, ::handleAuthResult)
    private val credentialManager by lazy { CredentialManager.create(this) }
    private var expectedState: UUID? = null
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expectedState = savedInstanceState?.getString(STATE_KEY)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        })
        showLogin()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        expectedState?.let { outState.putString(STATE_KEY, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun browserLogin() {
        val state = UUID.randomUUID()
        expectedState = state
        val loginUrl = Uri.parse("$SERVICE_URL/pidmdoc.html").buildUpon()
            .appendQueryParameter("client", "android")
            .appendQueryParameter("state", state.toString())
            .build()

        try {
            AuthTabIntent.Builder().build().launch(authLauncher, loginUrl, CALLBACK_SCHEME)
        } catch (_: ActivityNotFoundException) {
            expectedState = null
            showError("No compatible browser is installed.")
        }
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    private fun directLogin() {
        lifecycleScope.launch {
            showLoading("Preparing credential request...")
            val authentication = runCatching {
                withContext(Dispatchers.IO) { createDirectAuthentication() }
            }.getOrElse {
                showLogin("Could not create the credential request.")
                return@launch
            }

            val credential = try {
                credentialManager.getCredential(
                    context = this@MainActivity,
                    request = GetCredentialRequest(
                        listOf(GetDigitalCredentialOption(authentication.requestJson))
                    ),
                ).credential as? DigitalCredential
                    ?: error("Credential provider returned an unsupported credential")
            } catch (_: GetCredentialCancellationException) {
                showLogin()
                return@launch
            } catch (_: Exception) {
                showLogin("Credential authentication failed.")
                return@launch
            }

            showLoading("Validating credential...")
            val submitted = runCatching {
                withContext(Dispatchers.IO) {
                    request(
                        "$SERVICE_URL/transaction/result/${authentication.transactionId}",
                        method = "POST",
                        body = credential.credentialJson,
                    )
                }
            }
            if (submitted.isFailure) {
                showLogin("The credential response was rejected.")
                return@launch
            }
            loadResult(authentication.transactionId)
        }
    }

    private fun createDirectAuthentication(): DirectAuthentication {
        val credential = JSONObject()
            .put("credentialType", "eu.europa.ec.eudi.pid.1")
            .put("representation", "ISO_MDOC")
            .put("attributes", JSONArray(PID_ATTRIBUTES))
        val queries = JSONObject(
            request(
                "$SERVICE_URL/utilities/buildCredentialQueries",
                method = "POST",
                body = JSONArray().put(credential).toString(),
            )
        )
        val transaction = JSONObject(
            request(
                "$SERVICE_URL/transaction/create",
                method = "POST",
                body = JSONObject()
                    .put("dcqlQuery", queries.getJSONObject("dcqlQuery"))
                    .put("dcApiOrigin", androidAppOrigin())
                    .toString(),
            )
        )
        val profiles = transaction.getJSONArray("profiles")
        val profile = profiles.getJSONObject(0)
        val requestUrl = Uri.parse(profile.getString("dcApiUrl")).buildUpon()
            .appendQueryParameter("oid4vpMode", "NONE")
            .appendQueryParameter("isoMdoc", "true")
            .appendQueryParameter("encrypt", "true")
            .build()
            .toString()
        return DirectAuthentication(
            transactionId = profile.getString("id"),
            requestJson = digitalCredentialRequestJson(request(requestUrl)),
        )
    }

    private fun androidAppOrigin(): String {
        val signingInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ).signingInfo ?: error("Missing app signing information")
        val certificate = signingInfo.signingCertificateHistory.first().toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(certificate)
        return "android:apk-key-hash:" + Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun request(url: String, method: String = "GET", body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { writer -> writer.write(it) }
            }
            val responseBody = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(connection.responseCode in 200..299) {
                "Request failed (${connection.responseCode}): $responseBody"
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun handleAuthResult(result: AuthTabIntent.AuthResult) {
        val state = expectedState
        expectedState = null
        if (result.resultCode == AuthTabIntent.RESULT_CANCELED) return

        val callback = runCatching {
            check(result.resultCode == AuthTabIntent.RESULT_OK && state != null)
            AuthenticationCallback.parse(result.resultUri.toString(), state)
        }.getOrElse {
            showError("The authentication response was invalid.")
            return
        }

        loadResult(callback.transactionId)
    }

    private fun loadResult(transactionId: String) {
        showLoading()
        val resultUrl = Uri.parse("$SERVICE_URL/api/single").buildUpon()
            .appendPath(transactionId)
            .build()
            .toString()

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AuthenticationResult.parse(request(resultUrl))
                }
            }
            result.fold(::showResult) { showLogin("Could not load the authentication result.") }
        }
    }

    private fun showLogin(error: String? = null) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        error?.let { addText(it, 16f).setTextColor(0xffb00020.toInt()) }
        content.addView(Button(this).apply {
            text = "Login with Android"
            setOnClickListener { directLogin() }
        })
        content.addView(Button(this).apply {
            text = "Login with browser"
            setOnClickListener { browserLogin() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
    }

    private fun showLoading(message: String = "Loading user data...") {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(ProgressBar(this))
        addText(message, 16f)
    }

    private fun showResult(result: AuthenticationResult) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        addText("Authenticated", 28f).setTypeface(null, Typeface.BOLD)

        result.portrait?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                content.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Credential portrait"
                }, LinearLayout.LayoutParams(dp(120), dp(120)).apply { topMargin = dp(16) })
            }
        }
        result.error?.let { addText(it, 16f).setTextColor(0xffb00020.toInt()) }

        result.credentials.forEach { credential ->
            addText(credential.type, 20f).apply {
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(20), 0, dp(4))
            }
            credential.error?.let { addText(it, 16f).setTextColor(0xffb00020.toInt()) }
            credential.claims.forEach { claim ->
                addText(claim.name, 13f).setTextColor(0xff666666.toInt())
                addText(claim.value, 16f).setTextIsSelectable(true)
            }
        }

        content.addView(Button(this).apply {
            text = "Authenticate again"
            setOnClickListener { showLogin() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(24) })
    }

    private fun addText(value: String, size: Float) = TextView(this).also {
        it.text = value
        it.textSize = size
        content.addView(it, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SERVICE_URL = "https://wallet-rp.a-sit.plus"
        const val CALLBACK_SCHEME = "wallet-rp"
        const val STATE_KEY = "authentication-state"
        val PID_ATTRIBUTES = listOf(
            "family_name",
            "given_name",
            "birth_date",
            "nationality",
            "expiry_date",
            "issuing_authority",
            "issuing_country",
        )
    }
}

private data class DirectAuthentication(val transactionId: String, val requestJson: String)
