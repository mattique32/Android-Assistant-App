package io.homeassistant.companion.android.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.OnConflictStrategy
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.util.databaseChannel
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.EntriesTypeConverter
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingTypeConverter
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.settings.LocalNotificationSettingConverter
import io.homeassistant.companion.android.database.settings.LocalSensorSettingConverter
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import io.homeassistant.companion.android.database.wear.FavoriteCaches
import io.homeassistant.companion.android.database.wear.FavoriteCachesDao
import io.homeassistant.companion.android.database.wear.Favorites
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundTypeConverter
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.collections.ArrayList
import io.homeassistant.companion.android.common.R as commonR

@Database(
    entities = [
        Attribute::class,
        Authentication::class,
        Sensor::class,
        SensorSetting::class,
        ButtonWidgetEntity::class,
        CameraWidgetEntity::class,
        MediaPlayerControlsWidgetEntity::class,
        StaticWidgetEntity::class,
        TemplateWidgetEntity::class,
        NotificationItem::class,
        TileEntity::class,
        Favorites::class,
        FavoriteCaches::class,
        EntityStateComplications::class,
        Server::class,
        Setting::class
    ],
    version = 38,
    autoMigrations = [
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28, spec = AppDatabase.Companion.Migration27to28::class),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
        AutoMigration(from = 33, to = 34),
        AutoMigration(from = 34, to = 35),
        AutoMigration(from = 35, to = 36),
        AutoMigration(from = 36, to = 37, spec = AppDatabase.Companion.Migration36to37::class),
        AutoMigration(from = 37, to = 38, spec = AppDatabase.Companion.Migration37to38::class),
    ]
)
@TypeConverters(
    LocalNotificationSettingConverter::class,
    LocalSensorSettingConverter::class,
    EntriesTypeConverter::class,
    SensorSettingTypeConverter::class,
    WidgetBackgroundTypeConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun cameraWidgetDao(): CameraWidgetDao
    abstract fun mediaPlayCtrlWidgetDao(): MediaPlayerControlsWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao
    abstract fun templateWidgetDao(): TemplateWidgetDao
    abstract fun notificationDao(): NotificationDao
    abstract fun tileDao(): TileDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun favoriteCachesDao(): FavoriteCachesDao
    abstract fun entityStateComplicationsDao(): EntityStateComplicationsDao
    abstract fun serverDao(): ServerDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"
        internal const val TAG = "AppDatabase"
        private const val NOTIFICATION_ID = 45
        private lateinit var appContext: Context
        lateinit var integrationRepository: IntegrationRepository

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            appContext = context
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            appContext = context
            return Room
                .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24
                )
                .fallbackToDestructiveMigration()
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sensors` (`unique_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`unique_id`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `button_widgets` (`id` INTEGER NOT NULL, `icon_id` INTEGER NOT NULL, `domain` TEXT NOT NULL, `service` TEXT NOT NULL, `service_data` TEXT NOT NULL, `label` TEXT, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_id` TEXT, `label` TEXT, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `static_widget` ADD `text_size` FLOAT NOT NULL DEFAULT '30'")
                database.execSQL("ALTER TABLE `static_widget` ADD `separator` TEXT NOT NULL DEFAULT ' '")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `template_widgets` (`id` INTEGER NOT NULL, `template` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    val contentValues: ArrayList<ContentValues> = ArrayList()
                    val widgets = database.query("SELECT * FROM `static_widget`")
                    widgets.use {
                        if (widgets.count > 0) {
                            while (widgets.moveToNext()) {
                                val cv = ContentValues()
                                cv.put("id", widgets.getInt(widgets.getColumnIndex("id")))
                                cv.put("entity_id", widgets.getString(widgets.getColumnIndex("entity_id")))
                                cv.put("attribute_ids", widgets.getString(widgets.getColumnIndex("attribute_id")))
                                cv.put("label", widgets.getString(widgets.getColumnIndex("label")))
                                cv.put("text_size", widgets.getFloat(widgets.getColumnIndex("text_size")))
                                cv.put("state_separator", widgets.getString(widgets.getColumnIndex("separator")))
                                cv.put("attribute_separator", " ")
                                contentValues.add(cv)
                            }
                            database.execSQL("DROP TABLE IF EXISTS `static_widget`")
                            database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_ids` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', `state_separator` TEXT NOT NULL DEFAULT '', `attribute_separator` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
                            for (cv in contentValues) {
                                database.insert("static_widget", 0, cv)
                            }
                        } else {
                            database.execSQL("DROP TABLE IF EXISTS `static_widget`")
                            database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_ids` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', `state_separator` TEXT NOT NULL DEFAULT '', `attribute_separator` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
                        }
                    }
                    widgets.close()
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to migrate database version 5 to version 6", exception)
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT * FROM sensors")
                val sensors = mutableListOf<ContentValues>()
                var migrationSuccessful = false
                var migrationFailed = false
                try {
                    if (cursor.moveToFirst()) {
                        while (cursor.moveToNext()) {
                            sensors.add(
                                ContentValues().also {
                                    it.put("id", cursor.getString(cursor.getColumnIndex("unique_id")))
                                    it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                                    it.put(
                                        "registered",
                                        cursor.getInt(cursor.getColumnIndex("registered"))
                                    )
                                    it.put("state", "")
                                    it.put("state_type", "")
                                    it.put("type", "")
                                    it.put("icon", "")
                                    it.put("name", "")
                                    it.put("device_class", "")
                                }
                            )
                        }
                        migrationSuccessful = true
                    }
                    cursor.close()
                } catch (e: Exception) {
                    migrationFailed = true
                    Log.e(TAG, "Unable to migrate, proceeding with recreating the table", e)
                }
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                if (migrationSuccessful) {
                    sensors.forEach {
                        database.insert("sensors", OnConflictStrategy.REPLACE, it)
                    }
                }
                if (migrationFailed)
                    notifyMigrationFailed()

                database.execSQL("CREATE TABLE IF NOT EXISTS `sensor_attributes` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`sensor_id`, `name`))")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensor_attributes` ADD `value_type` TEXT NOT NULL DEFAULT 'string'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensors` ADD `state_changed` INTEGER NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT * FROM sensors")
                val sensors = mutableListOf<ContentValues>()
                var migrationSuccessful = false
                var migrationFailed = false
                try {
                    if (cursor.moveToFirst()) {
                        while (cursor.moveToNext()) {
                            sensors.add(
                                ContentValues().also {
                                    it.put("id", cursor.getString(cursor.getColumnIndex("id")))
                                    it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                                    it.put(
                                        "registered",
                                        cursor.getInt(cursor.getColumnIndex("registered"))
                                    )
                                    it.put("state", "")
                                    it.put("last_sent_state", "")
                                    it.put("state_type", "")
                                    it.put("type", "")
                                    it.put("icon", "")
                                    it.put("name", "")
                                }
                            )
                        }
                        migrationSuccessful = true
                    }
                    cursor.close()
                } catch (e: Exception) {
                    migrationFailed = true
                    Log.e(TAG, "Unable to migrate, proceeding with recreating the table", e)
                }
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `last_sent_state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                if (migrationSuccessful) {
                    sensors.forEach {
                        database.insert("sensors", OnConflictStrategy.REPLACE, it)
                    }
                }
                if (migrationFailed)
                    notifyMigrationFailed()
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', PRIMARY KEY(`sensor_id`, `name`))")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `mediaplayctrls_widgets` (`id` INTEGER NOT NULL, `entityId` TEXT NOT NULL, `label` TEXT, `showSkip` INTEGER NOT NULL, `showSeek` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `received` INTEGER NOT NULL, `message` TEXT NOT NULL, `data` TEXT NOT NULL)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `static_widget` ADD `last_update` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `template_widgets` ADD `last_update` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensor_settings` ADD `enabled` INTEGER NOT NULL DEFAULT '1'")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `camera_widgets` (`id` INTEGER NOT NULL, `entityId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {

                val cursor = database.query("SELECT * FROM sensor_settings")
                val sensorSettings = mutableListOf<ContentValues>()
                var migrationSuccessful = false
                var migrationFailed = false
                try {
                    if (cursor.moveToFirst()) {
                        while (cursor.moveToNext()) {

                            sensorSettings.add(
                                ContentValues().also {

                                    val currentSensorId = cursor.getString(cursor.getColumnIndex("sensor_id"))
                                    val currentSensorSettingName = cursor.getString(cursor.getColumnIndex("name"))
                                    var entries: String = ""
                                    var newSensorSettingName = currentSensorSettingName
                                    // Alarm
                                    if (currentSensorId == "next_alarm" && currentSensorSettingName == "Allow List") {
                                        newSensorSettingName = "nextalarm_allow_list"
                                    }
                                    // Notification
                                    else if ((currentSensorId == "last_removed_notification" || currentSensorId == "last_notification") && currentSensorSettingName == "Allow List") {
                                        newSensorSettingName = "notification_allow_list"
                                    }
                                    // Geocode
                                    else if (currentSensorId == "geocoded_location" && currentSensorSettingName == "Minimum Accuracy") {
                                        newSensorSettingName = "geocode_minimum_accuracy"
                                    }
                                    // Location
                                    else if ((currentSensorId == "zone_background" || currentSensorId == "accurate_location" || currentSensorId == "location_background") && currentSensorSettingName == "Minimum Accuracy") {
                                        newSensorSettingName = "location_minimum_accuracy"
                                    } else if (currentSensorId == "accurate_location" && currentSensorSettingName == "Minimum time between updates") {
                                        newSensorSettingName = "location_minimum_time_updates"
                                    } else if (currentSensorId == "accurate_location" && currentSensorSettingName == "Include in sensor update") {
                                        newSensorSettingName = "location_include_sensor_update"
                                    } else if (currentSensorId == "location_background" && currentSensorSettingName == "High accuracy mode (May drain battery fast)") {
                                        newSensorSettingName = "location_ham_enabled"
                                    } else if (currentSensorId == "location_background" && currentSensorSettingName == "High accuracy mode update interval (seconds)") {
                                        newSensorSettingName = "location_ham_update_interval"
                                    } else if (currentSensorId == "location_background" && currentSensorSettingName == "High accuracy mode only when connected to BT devices") {
                                        newSensorSettingName = "location_ham_only_bt_dev"
                                    } else if (currentSensorId == "location_background" && currentSensorSettingName == "High accuracy mode only when entering zone") {
                                        newSensorSettingName = "location_ham_only_enter_zone"
                                    } else if (currentSensorId == "location_background" && currentSensorSettingName == "High accuracy mode trigger range for zone (meters)") {
                                        newSensorSettingName = "location_ham_trigger_range"
                                    }
                                    // Bluetooth
                                    else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "UUID") {
                                        newSensorSettingName = "ble_uuid"
                                    } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "Major") {
                                        newSensorSettingName = "ble_major"
                                    } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "Minor") {
                                        newSensorSettingName = "ble_minor"
                                    } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "transmit_power") {
                                        newSensorSettingName = "ble_transmit_power"
                                        entries = "ultraLow|low|medium|high"
                                    } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "Enable Transmitter") {
                                        newSensorSettingName = "ble_transmit_enabled"
                                    } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "Include when enabling all sensors") {
                                        newSensorSettingName = "ble_enable_toggle_all"
                                    }
                                    // Last reboot
                                    else if (currentSensorId == "last_reboot" && currentSensorSettingName == "deadband") {
                                        newSensorSettingName = "lastreboot_deadband"
                                    }
                                    // Last update
                                    else if (currentSensorId == "last_update" && currentSensorSettingName == "Add New Intent") {
                                        newSensorSettingName = "lastupdate_add_new_intent"
                                    } else if (currentSensorId == "last_update" && currentSensorSettingName.startsWith("intent")) {
                                        newSensorSettingName = "lastupdate_intent_var1:" + currentSensorSettingName.substringAfter("intent") + ":"
                                    }
                                    // Network
                                    else if (currentSensorId == "wifi_bssid" && currentSensorSettingName == "get_current_bssid") {
                                        newSensorSettingName = "network_get_current_bssid"
                                    } else if (currentSensorId == "wifi_bssid" && currentSensorSettingName.startsWith("replace_")) {
                                        newSensorSettingName = "network_replace_mac_var1:" + currentSensorSettingName.substringAfter("replace_") + ":"
                                    }
                                    it.put("sensor_id", cursor.getString(cursor.getColumnIndex("sensor_id")))
                                    it.put("name", newSensorSettingName)
                                    it.put("value", cursor.getString(cursor.getColumnIndex("value")))
                                    it.put("value_type", cursor.getString(cursor.getColumnIndex("value_type")))
                                    it.put("entries", entries)
                                    it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                                }
                            )
                        }
                        migrationSuccessful = true
                    }
                    cursor.close()
                } catch (e: Exception) {
                    migrationFailed = true
                    Log.e(TAG, "Unable to migrate, proceeding with recreating the table", e)
                }
                database.execSQL("DROP TABLE IF EXISTS `sensor_settings`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', `entries` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT '1', PRIMARY KEY(`sensor_id`, `name`))")
                if (migrationSuccessful) {
                    sensorSettings.forEach {
                        database.insert("sensor_settings", OnConflictStrategy.REPLACE, it)
                    }
                }
                if (migrationFailed)
                    notifyMigrationFailed()
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `qs_tiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tileId` TEXT NOT NULL, `icon_id` INTEGER, `entityId` TEXT NOT NULL, `label` TEXT NOT NULL, `subtitle` TEXT)")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensors` ADD `state_class` TEXT")
                database.execSQL("ALTER TABLE `sensors` ADD `entity_category` TEXT")
                database.execSQL("ALTER TABLE `sensors` ADD `core_registration` TEXT")
                database.execSQL("ALTER TABLE `sensors` ADD `app_registration` TEXT")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`id` TEXT PRIMARY KEY NOT NULL, `position` INTEGER)")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `websocketSetting` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `notification_history` ADD `source` TEXT NOT NULL DEFAULT 'FCM'")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `mediaplayctrls_widgets` ADD `showVolume` INTEGER NOT NULL DEFAULT '0'")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `settings` ADD `sensorUpdateFrequency` TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        class Migration27to28 : AutoMigrationSpec {
            override fun onPostMigrate(db: SupportSQLiteDatabase) {
                // Update 'registered' in the sensors table to set the value to null instead of the previous default of 0
                // This will force an update to indicate whether a sensor is not registered (null) or registered as disabled (0)
                db.execSQL("UPDATE `sensors` SET `registered` = NULL")
            }
        }

        @RenameColumn.Entries(
            RenameColumn(
                tableName = "Authentication_List",
                fromColumnName = "Username",
                toColumnName = "username"
            ),
            RenameColumn(
                tableName = "Authentication_List",
                fromColumnName = "Password",
                toColumnName = "password"
            ),
            RenameColumn(
                tableName = "qs_tiles",
                fromColumnName = "tileId",
                toColumnName = "tile_id"
            ),
            RenameColumn(
                tableName = "qs_tiles",
                fromColumnName = "entityId",
                toColumnName = "entity_id"
            ),
            RenameColumn(
                tableName = "qs_tiles",
                fromColumnName = "shouldVibrate",
                toColumnName = "should_vibrate"
            ),
            RenameColumn(
                tableName = "qs_tiles",
                fromColumnName = "authRequired",
                toColumnName = "auth_required"
            ),
            RenameColumn(
                tableName = "settings",
                fromColumnName = "websocketSetting",
                toColumnName = "websocket_setting"
            ),
            RenameColumn(
                tableName = "settings",
                fromColumnName = "sensorUpdateFrequency",
                toColumnName = "sensor_update_frequency"
            ),
            RenameColumn(
                tableName = "camera_widgets",
                fromColumnName = "entityId",
                toColumnName = "entity_id"
            ),
            RenameColumn(
                tableName = "entityStateComplications",
                fromColumnName = "entityId",
                toColumnName = "entity_id"
            ),
            RenameColumn(
                tableName = "mediaplayctrls_widgets",
                fromColumnName = "entityId",
                toColumnName = "entity_id"
            ),
            RenameColumn(
                tableName = "mediaplayctrls_widgets",
                fromColumnName = "showSkip",
                toColumnName = "show_skip"
            ),
            RenameColumn(
                tableName = "mediaplayctrls_widgets",
                fromColumnName = "showSeek",
                toColumnName = "show_seek"
            ),
            RenameColumn(
                tableName = "mediaplayctrls_widgets",
                fromColumnName = "showVolume",
                toColumnName = "show_volume"
            ),
            RenameColumn(
                tableName = "mediaplayctrls_widgets",
                fromColumnName = "showSource",
                toColumnName = "show_source"
            )
        )
        @RenameTable.Entries(
            RenameTable(
                fromTableName = "Authentication_List",
                toTableName = "authentication_list"
            ),
            RenameTable(
                fromTableName = "entityStateComplications",
                toTableName = "entity_state_complications"
            ),
            RenameTable(
                fromTableName = "mediaplayctrls_widgets",
                toTableName = "media_player_controls_widgets"
            )
        )
        class Migration36to37 : AutoMigrationSpec

        class Migration37to38 : AutoMigrationSpec {
            override fun onPostMigrate(db: SupportSQLiteDatabase) {
                val urlStorage = appContext.getSharedPreferences("url_0", Context.MODE_PRIVATE)
                val urlExternal = urlStorage.getString("remote_url", null) ?: return
                val urlInternal = urlStorage.getString("local_url", null)
                val urlCloud = urlStorage.getString("remote_ui_url", null)
                val urlWebhook = urlStorage.getString("webhook_id", null)
                val urlCloudhook = urlStorage.getString("cloudhook_url", null)
                val urlUseCloud = urlStorage.getBoolean("use_cloud", false)
                val urlInternalSsids = urlStorage.getStringSet("wifi_ssids", emptySet()).orEmpty().toList()
                val urlPrioritizeInternal = urlStorage.getBoolean("prioritize_internal", false)

                val authStorage = appContext.getSharedPreferences("session_0", Context.MODE_PRIVATE)
                val authAccessToken = authStorage.getString("access_token", null)
                val authRefreshToken = authStorage.getString("refresh_token", null)
                val authTokenExpiration = if (authStorage.contains("expires_date")) authStorage.getLong("expires_date", 0) else null
                val authTokenType = authStorage.getString("token_type", null)
                val authInstallId = if (authStorage.contains("install_id")) {
                    authStorage.getString("install_id", "")
                } else {
                    val uuid = UUID.randomUUID().toString()
                    authStorage.edit { putString("install_id", uuid) }
                    uuid
                }

                val integrationStorage = appContext.getSharedPreferences("integration_0", Context.MODE_PRIVATE)
                val integrationHaVersion = integrationStorage.getString("ha_version", null)

                val serverValues = ContentValues().apply {
                    put("_name", "")
                    putNull("name_override")
                    integrationHaVersion?.let { put("_version", it) } ?: run { putNull("_version") }
                    put("list_order", -1)
                    put("external_url", urlExternal)
                    urlInternal?.let { put("internal_url", it) } ?: run { putNull("internal_url") }
                    urlCloud?.let { put("cloud_url", it) } ?: run { putNull("cloud_url") }
                    urlWebhook?.let { put("webhook_id", it) } ?: run { putNull("webhook_id") }
                    urlCloudhook?.let { put("cloudhook_url", it) } ?: run { putNull("cloudhook_url") }
                    put("use_cloud", urlUseCloud)
                    put("internal_ssids", jacksonObjectMapper().writeValueAsString(urlInternalSsids))
                    put("prioritize_internal", urlPrioritizeInternal)
                    authAccessToken?.let { put("access_token", it) } ?: run { putNull("access_token") }
                    authRefreshToken?.let { put("refresh_token", it) } ?: run { putNull("refresh_token") }
                    authTokenExpiration?.let { put("token_expiration", it) } ?: run { putNull("token_expiration") }
                    authTokenType?.let { put("token_type", it) } ?: run { putNull("token_type") }
                    authAccessToken?.let { put("install_id", authInstallId) } ?: run { putNull("install_id") }
                }
                val serverId = db.insert("servers", SQLiteDatabase.CONFLICT_REPLACE, serverValues)

                urlStorage.edit { clear() }
                authStorage.edit {
                    remove("access_token")
                    remove("refresh_token")
                    remove("expires_date")
                    remove("token_type")
                }
                integrationStorage.edit { remove("ha_version") }
                // TODO migration
                //  - auth/integration prefs prefix with server specific
                //  - settings copy with server ID, 0 = shared
                //  - notifications/tiles/sensors/widgets update with server ID
            }
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = appContext.getSystemService<NotificationManager>()!!

                var notificationChannel =
                    notificationManager.getNotificationChannel(databaseChannel)
                if (notificationChannel == null) {
                    notificationChannel = NotificationChannel(
                        databaseChannel, TAG, NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager.createNotificationChannel(notificationChannel)
                }
            }
        }

        private fun notifyMigrationFailed() {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(appContext, databaseChannel)
                .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
                .setContentTitle(appContext.getString(commonR.string.database_migration_failed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            with(NotificationManagerCompat.from(appContext)) {
                notify(NOTIFICATION_ID, notification)
            }
            runBlocking {
                try {
                    integrationRepository.fireEvent("mobile_app.migration_failed", mapOf())
                    Log.d(TAG, "Event sent to Home Assistant")
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send event to Home Assistant", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            appContext,
                            commonR.string.database_event_failure,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
