package io.homeassistant.companion.android.launch

import android.util.Log
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class LaunchPresenterBase(
    private val view: LaunchView,
    private val authenticationUseCase: AuthenticationRepository,
    internal val integrationUseCase: IntegrationRepository
) : LaunchPresenter {

    companion object {
        const val TAG = "LaunchPresenter"
    }

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            Log.d(TAG, "Authentication state: ${authenticationUseCase.getSessionState()}")
            Log.d(TAG, "Integration is registered: ${integrationUseCase.isRegistered()}")
            val sessionValid = authenticationUseCase.getSessionState() == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                resyncRegistration()
                view.displayHome()
            } else if (sessionValid) {
                view.displayMobileAppIntegration()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override fun setSessionExpireMillis(value: Long) {
        mainScope.launch {
            integrationUseCase.setSessionExpireMillis(value)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    // TODO: This should probably go in settings?
    internal abstract fun resyncRegistration()
}
