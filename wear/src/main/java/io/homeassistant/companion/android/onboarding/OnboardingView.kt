package io.homeassistant.companion.android.onboarding

interface OnboardingView {
    fun startAuthentication(flowId: String)

    fun showLoading()

    fun showError()
}