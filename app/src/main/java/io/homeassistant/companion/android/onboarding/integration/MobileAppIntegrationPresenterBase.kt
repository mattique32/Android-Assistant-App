package io.homeassistant.companion.android.onboarding.integration

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class MobileAppIntegrationPresenterBase constructor(
    private val view: MobileAppIntegrationView,
    private val integrationUseCase: IntegrationRepository
) : MobileAppIntegrationPresenter {

    companion object {
        internal const val TAG = "IntegrationPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    internal open suspend fun createRegistration(simple: Boolean, deviceName: String): DeviceRegistration {
        return DeviceRegistration(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            deviceName
        )
    }

    override fun onRegistrationAttempt(simple: Boolean, deviceName: String) {
        view.showLoading()
        mainScope.launch {
            val deviceRegistration: DeviceRegistration
            try {
                deviceRegistration = createRegistration(simple, deviceName)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create registration.", e)
                view.showWarning()
                return@launch
            }
            try {
                integrationUseCase.registerDevice(deviceRegistration)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register with Home Assistant", e)
                view.showError()
                return@launch
            }
            view.deviceRegistered()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
