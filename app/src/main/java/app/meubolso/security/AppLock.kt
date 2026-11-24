package app.meubolso.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Bloqueio do app com a digital/rosto ou a senha/PIN do próprio celular. */
object AppLock {
    /** Depois de quanto tempo em segundo plano o app volta a pedir desbloqueio. */
    const val RELOCK_AFTER_MS = 60_000L

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** O celular tem bloqueio de tela (digital ou senha) configurado? */
    fun isAvailable(ctx: Context): Boolean {
        val bm = BiometricManager.from(ctx)
        if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) return true
        // Android < 11: sem digital, a senha do aparelho ainda serve.
        val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
        return Build.VERSION.SDK_INT < 30 && km.isDeviceSecure
    }

    @Suppress("DEPRECATION")
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: (String?) -> Unit = {}) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use a digital ou a senha do celular")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(authenticators)
                else setDeviceCredentialAllowed(true)
            }
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onFail(it.message) }
    }
}
