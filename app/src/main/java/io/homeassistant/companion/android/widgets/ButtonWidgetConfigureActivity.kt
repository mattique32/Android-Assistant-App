package io.homeassistant.companion.android.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Service
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_button_configure.*
import kotlinx.android.synthetic.main.widget_button_configure_dynamic_field.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ButtonWidgetConfigureActivity : Activity() {
    private val TAG: String = "ButtonWidgetConfigAct"

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private var services = HashMap<String, Service>()
    private var entities = HashMap<String, Entity<Any>>()
    private var dynamicFields = ArrayList<Pair<String, String>>()
    private lateinit var dynamicFieldAdapter: WidgetDynamicFieldAdapter

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var onClickListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity

        // Set up a broadcast intent and pass the service call data as extras
        val intent = Intent()
        intent.action = ButtonWidget.RECEIVE_DATA
        intent.component = ComponentName(context, ButtonWidget::class.java)

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        // Analyze and send service and domain
        val serviceText = context.widget_text_config_service.text.toString()
        val domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
        val service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]
        intent.putExtra(
            ButtonWidget.EXTRA_DOMAIN,
            domain
        )
        intent.putExtra(
            ButtonWidget.EXTRA_SERVICE,
            service
        )

        // Fetch and send label and icon
        intent.putExtra(
            ButtonWidget.EXTRA_LABEL,
            context.label.text.toString()
        )
        intent.putExtra(
            ButtonWidget.EXTRA_ICON,
            context.widget_config_spinner.selectedItemId.toInt()
        )

        // Analyze and send service data
        val serviceDataMap = HashMap<String, Any>()
        for (i in 0 until dynamicFields.size) {
            val fieldText = dynamicFieldAdapter.getText(i)

            // Don't store data that's empty (or just whitespace)
            if (!fieldText.isBlank()) {
                serviceDataMap[dynamicFields[i].second] = fieldText
            }
        }

        intent.putExtra(
            ButtonWidget.EXTRA_SERVICE_DATA,
            Gson().toJson(serviceDataMap)
        )

        context.sendBroadcast(intent)

        // Make sure we pass back the original appWidgetId
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val serviceTextWatcher: TextWatcher = (object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            val serviceText: String = p0.toString()

            if (services.keys.contains(serviceText)) {
                Log.d(TAG, "Valid domain and service--processing dynamic fields")

                // Make sure there are not already any dynamic fields created
                // This can happen if selecting the drop-down twice or pasting
                widget_config_fields_layout.removeAllViews()

                // We only call this if servicesAvailable was fetched and is not null,
                // so we can safely assume that it is not null here
                val fields = services[serviceText]!!.serviceData.fields
                val fieldKeys = fields.keys
                Log.d(TAG, "Fields applicable to this service: $fields")

                fieldKeys.sorted().forEach { fieldKey ->
                    Log.d(TAG, "Creating a text input box for $fieldKey")

                    // Insert a dynamic layout
                    // IDs get priority and go at the top, since the other fields
                    // are usually optional but the ID is required
                    if (fieldKey.contains("_id"))
                        dynamicFields.add(0, Pair(serviceText, fieldKey))
                    else
                        dynamicFields.add(Pair(serviceText, fieldKey))

                    dynamicFieldAdapter.notifyDataSetChanged()
                }
            } else {
                if (dynamicFields.size > 0) {
                    dynamicFields.clear()
                    dynamicFieldAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    })

    private fun getServiceString(service: Service): String {
        return "${service.domain}.${service.service}"
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_button_configure)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val serviceAdapter = SingleItemArrayAdapter<Service>(this) {
            if (it != null) getServiceString(it) else ""
        }
        widget_text_config_service.setAdapter(serviceAdapter)
        widget_text_config_service.onFocusChangeListener = dropDownOnFocus

        mainScope.launch {
            // Fetch services
            integrationUseCase.getServices().forEach {
                services[getServiceString(it)] = it
            }
            serviceAdapter.addAll(services.values)
            serviceAdapter.sort()

            // Fetch entities
            integrationUseCase.getEntities().forEach {
                entities[it.entityId] = it
            }

            // Update service adapter
            runOnUiThread {
                serviceAdapter.notifyDataSetChanged()
            }
        }

        widget_text_config_service.addTextChangedListener(serviceTextWatcher)

        add_button.setOnClickListener(onClickListener)

        dynamicFieldAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFields)
        widget_config_fields_layout.adapter = dynamicFieldAdapter
        widget_config_fields_layout.layoutManager = LinearLayoutManager(this)

        // Set up icon spinner
        val icons = intArrayOf(
            R.drawable.ic_flash_on_black_24dp,
            R.drawable.ic_lightbulb_outline_black_24dp,
            R.drawable.ic_home_black_24dp,
            R.drawable.ic_power_settings_new_black_24dp
        )

        widget_config_spinner.adapter = ButtonWidgetConfigSpinnerAdaptor(this, icons)
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}
