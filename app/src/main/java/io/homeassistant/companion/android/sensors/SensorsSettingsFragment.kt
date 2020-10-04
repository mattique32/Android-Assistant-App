package io.homeassistant.companion.android.sensors

import android.os.Bundle
import android.os.Handler
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import javax.inject.Inject

class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val handler = Handler()
    private val refresh = object : Runnable {
        override fun run() {
            SensorWorker.start(requireContext())
            totalDisabledSensors = 0
            totalEnabledSensors = 0
            val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
            SensorReceiver.MANAGERS.sortedBy { it.name }.filter { it.hasSensor(requireContext()) }.forEach { managers ->
                managers.availableSensors.sortedBy { it.name }.forEach { basicSensor ->
                    findPreference<Preference>(basicSensor.id)?.let {
                        val sensorEntity = sensorDao.get(basicSensor.id)
                        val sensorSettings = sensorDao.getSettings(basicSensor.id)
                        if (!sensorSettings.isNullOrEmpty()) {
                            sensorSettings.forEach { setting ->
                                if (!setting.name.isNullOrBlank())
                                    if (getString(basicSensor.name) !in sensorsWithSettings) {
                                        sensorsWithSettings += getString(basicSensor.name)
                                        sensorsWithSettings.sort()
                                    }
                            }
                        }
                        if (sensorEntity?.enabled == true) {
                            totalEnabledSensors += 1
                            if (basicSensor.unitOfMeasurement.isNullOrBlank())
                                it.summary = sensorEntity.state
                            else
                                it.summary = sensorEntity.state + " " + basicSensor.unitOfMeasurement
                            // TODO: Add the icon from mdi:icon?
                        } else {
                            totalDisabledSensors += 1
                            it.summary = "Disabled"
                        }
                    }
                }
            }

            findPreference<PreferenceCategory>("enable_disable_category")?.let {
                it.summary = getString(R.string.manage_all_sensors_summary, (totalDisabledSensors + totalEnabledSensors))
            }

            findPreference<SwitchPreference>("enable_disable_sensors")?.let {
                if (totalDisabledSensors == 0) {
                    it.title = getString(R.string.disable_all_sensors, totalEnabledSensors)
                    it.summary = ""
                    it.isChecked = true
                } else {
                    if ((totalDisabledSensors + totalEnabledSensors) == totalDisabledSensors)
                        it.title = getString(R.string.enable_all_sensors)
                    else
                        it.title = getString(R.string.enable_remaining_sensors, totalDisabledSensors)
                    it.summary = getString(R.string.enable_all_sensors_summary)
                    it.isChecked = false
                }
            }

            findPreference<Preference>("sensors_with_settings")?.let {
                it.summary = getString(
                    R.string.sensors_with_settings,
                    sensorsWithSettings.asList().toString().replace("[", "").replace("]", "")
                )
                it.isVisible = !sensorsWithSettings.isNullOrEmpty()
            }

            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        private var totalEnabledSensors = 0
        private var totalDisabledSensors = 0
        private var sensorsWithSettings: Array<String> = arrayOf()
        fun newInstance(): SensorsSettingsFragment {
            return SensorsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        setPreferencesFromResource(R.xml.sensors, rootKey)

        findPreference<SwitchPreference>("enable_disable_sensors")?.let {

            var permArray: Array<String> = arrayOf()
            it.setOnPreferenceChangeListener { _, newState ->
                val enabledAll = newState as Boolean
                val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

                SensorReceiver.MANAGERS.forEach { managers ->
                    managers.availableSensors.forEach { basicSensor ->
                        var sensorEntity = sensorDao.get(basicSensor.id)

                        if (!managers.checkPermission(requireContext(), basicSensor.id))
                            permArray += managers.requiredPermissions(basicSensor.id).asList()

                        if (sensorEntity != null) {
                            sensorEntity.enabled = enabledAll
                            sensorEntity.lastSentState = ""
                            sensorDao.update(sensorEntity)
                        } else {
                            sensorEntity = Sensor(basicSensor.id, enabledAll, false, "")
                            sensorDao.add(sensorEntity)
                        }
                    }
                }
                if (!permArray.isNullOrEmpty())
                    requestPermissions(permArray, 0)
                handler.postDelayed(refresh, 0)
                return@setOnPreferenceChangeListener true
            }
        }

        SensorReceiver.MANAGERS.sortedBy { it.name }.filter { it.hasSensor(requireContext()) }.forEach { manager ->
            val prefCategory = PreferenceCategory(preferenceScreen.context)
            prefCategory.title = getString(manager.name)

            preferenceScreen.addPreference(prefCategory)
            manager.availableSensors.sortedBy { it.name }.forEach { basicSensor ->

                val pref = Preference(preferenceScreen.context)
                pref.key = basicSensor.id
                pref.title = getString(basicSensor.name)

                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            SensorDetailFragment.newInstance(
                                manager,
                                basicSensor
                            )
                        )
                        .addToBackStack("Sensor Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory.addPreference(pref)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refresh, 0)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }
}
