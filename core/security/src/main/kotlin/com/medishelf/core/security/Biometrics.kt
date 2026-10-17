package com.medishelf.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Class 3 (strong) biometrics only. The app PIN, not the device credential, is the fallback,
 * so the lock behaves the same on every device.
 */
object Biometrics {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        negativeButton: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            // Cancel, "Use PIN", lockout or hardware errors all fall back to the PIN pad.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFallback()
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButton)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build(),
        )
    }
}

/**
 * Forgot-PIN recovery: proves the user owns the phone via its screen lock. On API 30+ this is a
 * BiometricPrompt; on older versions callers use [confirmCredentialIntent] instead.
 */
object DeviceCredential {
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

    val supportsPrompt: Boolean get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R

    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build(),
        )
    }

    @Suppress("DEPRECATION")
    fun confirmCredentialIntent(context: Context, title: String, description: String): android.content.Intent? =
        context.getSystemService(android.app.KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(title, description)
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
