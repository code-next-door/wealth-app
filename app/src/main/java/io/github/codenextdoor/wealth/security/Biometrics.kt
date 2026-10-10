package io.github.codenextdoor.wealth.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Fingerprint / face unlock through the system's biometric prompt. Strong biometrics only:
 * Android ties a key to the prompt ([BiometricKey]) only for those.
 */
object Biometrics {

    private const val ALLOWED = BIOMETRIC_STRONG

    /** True when the device has biometrics set up that the app can use. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the prompt for [cipher]; [onSuccess] gets the cipher back, unlocked by the prompt. */
    fun prompt(activity: FragmentActivity, title: String, cancelLabel: String, cipher: Cipher, onSuccess: (Cipher?) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onSuccess(result.cryptoObject?.cipher)
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(cancelLabel) // Falls back to the app's PIN pad.
                .setAllowedAuthenticators(ALLOWED)
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }
}
