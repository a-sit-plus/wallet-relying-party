package at.asitplus.wallet.rp

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val authLauncher = AuthTabIntent.registerActivityResultLauncher(this, ::handleAuthResult)
    private var expectedState: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginButton = Button(this).apply {
            text = "Login"
            setOnClickListener { login() }
        }
        setContentView(
            loginButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
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

        Toast.makeText(this, "Authenticated: ${callback.transactionId}", Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val SERVICE_URL = "https://wallet-rp.a-sit.plus"
        const val CALLBACK_SCHEME = "wallet-rp"
    }
}
