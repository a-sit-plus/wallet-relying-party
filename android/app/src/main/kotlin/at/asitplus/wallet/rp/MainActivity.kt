package at.asitplus.wallet.rp

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val authLauncher = AuthTabIntent.registerActivityResultLauncher(this, ::handleAuthResult)
    private val executor = Executors.newSingleThreadExecutor()
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun login() {
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

        executor.execute {
            val result = runCatching {
                val connection = URL(resultUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "Result request failed (${connection.responseCode})."
                    }
                    AuthenticationResult.parse(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }
            runOnUiThread {
                result.fold(::showResult) { showLogin("Could not load the authentication result.") }
            }
        }
    }

    private fun showLogin(error: String? = null) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        error?.let { addText(it, 16f).setTextColor(0xffb00020.toInt()) }
        content.addView(Button(this).apply {
            text = "Login"
            setOnClickListener { login() }
        })
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(ProgressBar(this))
        addText("Loading user data…", 16f)
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
            text = "Login again"
            setOnClickListener { login() }
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
    }
}
