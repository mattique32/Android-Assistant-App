package io.homeassistant.companion.android.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.themes.ThemesManager
import javax.inject.Inject

class OnboardingFragmentFactory @Inject constructor(
    private val themesManager: ThemesManager,
    private val mobileAppIntegrationPresenter: MobileAppIntegrationPresenter
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            AuthenticationFragment::class.java.name -> AuthenticationFragment(themesManager)
            DiscoveryFragment::class.java.name -> DiscoveryFragment()
            MobileAppIntegrationFragment::class.java.name ->
                MobileAppIntegrationFragment(mobileAppIntegrationPresenter)
            ManualSetupFragment::class.java.name -> ManualSetupFragment()
            else -> super.instantiate(classLoader, className)
        }
    }
}
