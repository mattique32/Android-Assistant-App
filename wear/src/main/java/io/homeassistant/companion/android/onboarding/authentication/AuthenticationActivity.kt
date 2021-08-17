package io.homeassistant.companion.android.onboarding.authentication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import kotlinx.android.synthetic.main.activity_authentication.*
import kotlinx.android.synthetic.main.activity_authentication.loading_view
import kotlinx.android.synthetic.main.activity_integration.*
import javax.inject.Inject

class AuthenticationActivity : AppCompatActivity(), AuthenticationView {
    companion object {
        private const val TAG = "AuthenticationActivity"

        fun newInstance(context: Context, flowId: String): Intent {
            var intent = Intent(context, AuthenticationActivity::class.java)
            intent.putExtra("flowId", flowId)
            return intent
        }
    }

    @Inject
    lateinit var presenter: AuthenticationPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null || !intent.hasExtra("flowId")) {
            Log.e(TAG, "Flow id not specified, canceling authentication")
            finish()
        }

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        setContentView(R.layout.activity_authentication)

        button_next.setOnClickListener {
            presenter.onNextClicked(
                intent.getStringExtra("flowId")!!,
                username.text.toString(),
                password.text.toString())
        }
    }

    override fun startIntegration() {
        startActivity(MobileAppIntegrationActivity.newInstance(this))

        loading_view.visibility = View.GONE
    }

    override fun showLoading() {
        loading_view.visibility = View.VISIBLE
    }

    override fun showError() {
        // Hide loading view again
        loading_view.visibility = View.GONE

        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.failed_authentication))
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}