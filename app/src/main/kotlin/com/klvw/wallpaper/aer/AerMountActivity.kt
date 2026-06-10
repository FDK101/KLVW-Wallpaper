package com.klvw.wallpaper.aer

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AerMountActivity : FragmentActivity() {

    companion object {
        const val OPEN_FILES_AFTER_MOUNT = "open_files_after_mount"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openFilesAfterMount = intent.getBooleanExtra(OPEN_FILES_AFTER_MOUNT, false)

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AerLockStore.mount(this@AerMountActivity)
                    if (openFilesAfterMount) {
                        AerShell.openFilesApp(this@AerMountActivity)
                    }
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }

                override fun onAuthenticationFailed() {
                    // Biometric not recognised — keep prompt visible, user can retry
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("KLVW Aer")
            .setSubtitle("Unlock to access private media storage")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
