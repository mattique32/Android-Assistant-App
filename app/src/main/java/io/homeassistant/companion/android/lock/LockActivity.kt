package io.homeassistant.companion.android.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class LockActivity : AppCompatActivity(), LockView {

    companion object {

        fun newInstance(context: Context): Intent {
            return Intent(context, LockActivity::class.java)
        }

        fun biometric(
            set: Boolean = false,
            fragment: FragmentActivity,
            lockViewPresenter: LockPresenter? = null,
            switchLock: SwitchPreference? = null
        ) {

            val executor = ContextCompat.getMainExecutor(fragment)
            val biometricPrompt = BiometricPrompt(fragment, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        if (set)
                            switchLock?.isChecked = false
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (set)
                            switchLock?.isChecked = false
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        if (!set)
                            lockViewPresenter?.onViewReady()
                    }
                })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(fragment.resources.getString(R.string.biometric_title))
                .setSubtitle(fragment.resources.getString(R.string.biometric_message))
                .setDeviceCredentialAllowed(true)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    @Inject
    lateinit var presenter: LockPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        if (presenter.isLockEnabled()) {
            val button = findViewById<ImageView>(R.id.unlockButton)
            button.setOnClickListener {
                if (BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                    biometric(
                        fragment = this,
                        lockViewPresenter = presenter
                    )
                }
            }
        }
    }

    override fun displayWebview() {
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }
}
