package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter
import io.homeassistant.companion.android.bluetooth.ble.TransmitterManager
import io.homeassistant.companion.android.common.bluetooth.BluetoothDevice
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils.supportsTransmitter
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.util.UUID
import kotlin.collections.ArrayList
import io.homeassistant.companion.android.common.R as commonR

class BluetoothSensorManager : SensorManager {
    companion object {

        const val SETTING_BLE_ID1 = "ble_uuid"
        const val SETTING_BLE_ID2 = "ble_major"
        const val SETTING_BLE_ID3 = "ble_minor"
        const val SETTING_BLE_TRANSMIT_POWER = "ble_transmit_power"
        const val SETTING_BLE_ADVERTISE_MODE = "ble_advertise_mode"
        private const val SETTING_BLE_TRANSMIT_ENABLED = "ble_transmit_enabled"
        const val SETTING_BLE_MEASURED_POWER = "ble_measured_power_at_1m"
        const val BLE_ADVERTISE_LOW_LATENCY = "lowLatency"
        const val BLE_ADVERTISE_BALANCED = "balanced"
        const val BLE_ADVERTISE_LOW_POWER = "lowPower"
        const val BLE_TRANSMIT_HIGH = "high"
        const val BLE_TRANSMIT_MEDIUM = "medium"
        const val BLE_TRANSMIT_LOW = "low"
        const val BLE_TRANSMIT_ULTRA_LOW = "ultraLow"

        private const val DEFAULT_BLE_TRANSMIT_POWER = "ultraLow"
        private const val DEFAULT_BLE_ADVERTISE_MODE = "lowPower"
        const val DEFAULT_BLE_MAJOR = "100"
        const val DEFAULT_BLE_MINOR = "1"
        private const val DEFAULT_MEASURED_POWER_AT_1M = -59
        private var priorBluetoothStateEnabled = false

        private var bleTransmitterDevice = IBeaconTransmitter("", "", "", transmitPowerSetting = "", measuredPowerSetting = 0, advertiseModeSetting = "", transmitting = false, state = "", restartRequired = false)
        val bluetoothConnection = SensorManager.BasicSensor(
            "bluetooth_connection",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth,
            commonR.string.sensor_description_bluetooth_connection,
            "mdi:bluetooth",
            unitOfMeasurement = "connection(s)",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val bluetoothState = SensorManager.BasicSensor(
            "bluetooth_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_bluetooth_state,
            commonR.string.sensor_description_bluetooth_state,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val bleTransmitter = SensorManager.BasicSensor(
            "ble_emitter",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth_ble_emitter,
            commonR.string.sensor_description_bluetooth_ble_emitter,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        fun enableDisableBLETransmitter(context: Context, transmitEnabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorEntity = sensorDao.get(bleTransmitter.id)
            val sensorEnabled = (sensorEntity != null && sensorEntity.enabled)
            if (!sensorEnabled)
                return

            TransmitterManager.stopTransmitting(bleTransmitterDevice) // stop in all instances, clean up state if start required
            if (transmitEnabled) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
            sensorDao.add(SensorSetting(bleTransmitter.id, SETTING_BLE_TRANSMIT_ENABLED, transmitEnabled.toString(), SensorSettingType.TOGGLE))
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#bluetooth-sensors"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_bluetooth
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(bluetoothConnection, bluetoothState, bleTransmitter)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == bleTransmitter.id && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            else -> arrayOf(Manifest.permission.BLUETOOTH)
        }
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
        updateBluetoothState(context)
        updateBLESensor(context)
    }

    private fun updateBluetoothConnectionSensor(context: Context) {
        if (!isEnabled(context, bluetoothConnection.id))
            return

        var totalConnectedDevices = 0
        var connectedPairedDevices: List<String> = ArrayList()
        var connectedNotPairedDevices: List<String> = ArrayList()
        var pairedDevices: List<String> = ArrayList()

        if (checkPermission(context, bluetoothConnection.id)) {

            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(context, true)
            pairedDevices = bluetoothDevices.filter { b -> b.paired }.map { checkNameAddress(it) }
            connectedPairedDevices = bluetoothDevices.filter { b -> b.paired && b.connected }.map { checkNameAddress(it) }
            connectedNotPairedDevices = bluetoothDevices.filter { b -> !b.paired && b.connected }.map { checkNameAddress(it) }
            totalConnectedDevices = bluetoothDevices.count { b -> b.connected }
        }
        onSensorUpdated(
            context,
            bluetoothConnection,
            totalConnectedDevices,
            bluetoothConnection.statelessIcon,
            mapOf(
                "connected_paired_devices" to connectedPairedDevices,
                "connected_not_paired_devices" to connectedNotPairedDevices,
                "paired_devices" to pairedDevices
            )
        )
    }

    private fun isBtOn(context: Context): Boolean {
        var btOn = false
        if (checkPermission(context, bluetoothState.id)) {
            btOn = BluetoothUtils.isOn(context)
        }
        return btOn
    }

    private fun updateBluetoothState(context: Context) {
        if (!isEnabled(context, bluetoothState.id))
            return
        val icon = if (isBtOn(context)) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            context,
            bluetoothState,
            isBtOn(context),
            icon,
            mapOf()
        )
    }

    private fun updateBLEDevice(context: Context) {
        val transmitActive = getToggleSetting(context, bleTransmitter, SETTING_BLE_TRANSMIT_ENABLED, default = true)
        val uuid = getSetting(context, bleTransmitter, SETTING_BLE_ID1, SensorSettingType.STRING, default = UUID.randomUUID().toString())
        val major = getSetting(context, bleTransmitter, SETTING_BLE_ID2, SensorSettingType.STRING, default = DEFAULT_BLE_MAJOR)
        val minor = getSetting(context, bleTransmitter, SETTING_BLE_ID3, SensorSettingType.STRING, default = DEFAULT_BLE_MINOR)
        val measuredPower = getNumberSetting(context, bleTransmitter, SETTING_BLE_MEASURED_POWER, default = DEFAULT_MEASURED_POWER_AT_1M)
        val transmitPower = getSetting(
            context = context,
            sensor = bleTransmitter,
            settingName = SETTING_BLE_TRANSMIT_POWER,
            settingType = SensorSettingType.LIST,
            entries = listOf(
                BLE_TRANSMIT_ULTRA_LOW, BLE_TRANSMIT_LOW, BLE_TRANSMIT_MEDIUM, BLE_TRANSMIT_HIGH
            ),
            default = DEFAULT_BLE_TRANSMIT_POWER
        )
        val advertiseMode = getSetting(
            context = context,
            sensor = bleTransmitter,
            settingName = SETTING_BLE_ADVERTISE_MODE,
            settingType = SensorSettingType.LIST,
            entries = listOf(
                BLE_ADVERTISE_LOW_POWER, BLE_ADVERTISE_BALANCED, BLE_ADVERTISE_LOW_LATENCY
            ),
            default = DEFAULT_BLE_ADVERTISE_MODE
        )

        bleTransmitterDevice.restartRequired = false
        if (bleTransmitterDevice.uuid != uuid || bleTransmitterDevice.major != major ||
            bleTransmitterDevice.minor != minor || bleTransmitterDevice.transmitPowerSetting != transmitPower ||
            bleTransmitterDevice.advertiseModeSetting != advertiseMode || bleTransmitterDevice.transmitRequested != transmitActive ||
            bleTransmitterDevice.measuredPowerSetting != measuredPower || priorBluetoothStateEnabled != isBtOn(context)
        ) {
            bleTransmitterDevice.restartRequired = true
        }
        // stash the current BT state to help us know if we need to restart if BT state turns from off to on
        priorBluetoothStateEnabled = isBtOn(context)

        bleTransmitterDevice.uuid = uuid
        bleTransmitterDevice.major = major
        bleTransmitterDevice.minor = minor
        bleTransmitterDevice.transmitPowerSetting = transmitPower
        bleTransmitterDevice.measuredPowerSetting = measuredPower
        bleTransmitterDevice.advertiseModeSetting = advertiseMode
        bleTransmitterDevice.transmitRequested = transmitActive
    }

    private fun updateBLESensor(context: Context) {
        // get device details from settings
        updateBLEDevice(context)

        // sensor disabled, stop transmitting if we have been
        if (!isEnabled(context, bleTransmitter.id)) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
            return
        }
        // transmit when BT is on, if we are not already transmitting, or details have changed
        if (isBtOn(context)) {
            if (bleTransmitterDevice.transmitRequested && (!bleTransmitterDevice.transmitting || bleTransmitterDevice.restartRequired)) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
        }

        // BT off, or TransmitToggled off, stop transmitting if we have been
        if (!isBtOn(context) || !bleTransmitterDevice.transmitRequested) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
        }

        val lastState = AppDatabase.getInstance(context).sensorDao().get(bleTransmitter.id)?.state ?: "unknown"
        val state = if (isBtOn(context)) bleTransmitterDevice.state else "Bluetooth is turned off"
        val icon = if (bleTransmitterDevice.transmitting) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            context,
            bleTransmitter,
            if (state != "") state else lastState,
            icon,
            mapOf(
                "id" to bleTransmitterDevice.uuid + "-" + bleTransmitterDevice.major + "-" + bleTransmitterDevice.minor,
                "Transmitting power" to bleTransmitterDevice.transmitPowerSetting,
                "Advertise mode" to bleTransmitterDevice.advertiseModeSetting,
                "Measured power" to bleTransmitterDevice.measuredPowerSetting,
                "Supports transmitter" to supportsTransmitter(context)
            )
        )
    }

    private fun checkNameAddress(bt: BluetoothDevice): String {
        return if (bt.address != bt.name) "${bt.address} (${bt.name})" else bt.address
    }
}
