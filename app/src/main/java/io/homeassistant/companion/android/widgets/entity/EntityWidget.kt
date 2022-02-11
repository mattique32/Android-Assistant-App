package io.homeassistant.companion.android.widgets.entity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EntityWidget : BaseWidgetProvider() {

    companion object {
        private const val TAG = "StaticWidget"

        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_ATTRIBUTE_IDS = "EXTRA_ATTRIBUTE_IDS"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_STATE_SEPARATOR = "EXTRA_STATE_SEPARATOR"
        internal const val EXTRA_ATTRIBUTE_SEPARATOR = "EXTRA_ATTRIBUTE_SEPARATOR"

        private var lastResolvedTextSuccess = true
    }

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val intent = Intent(context, EntityWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_static).apply {
            val widget = AppDatabase.getInstance(context).staticWidgetDao().get(appWidgetId)
            if (widget != null) {
                val entityId: String = widget.entityId
                val attributeIds: String? = widget.attributeIds
                val label: String? = widget.label
                val textSize: Float = widget.textSize
                val stateSeparator: String = widget.stateSeparator
                val attributeSeparator: String = widget.attributeSeparator
                setTextViewTextSize(
                    R.id.widgetText,
                    TypedValue.COMPLEX_UNIT_SP,
                    textSize
                )
                setTextViewText(
                    R.id.widgetText,
                    resolveTextToShow(
                        context,
                        entityId,
                        suggestedEntity,
                        attributeIds,
                        stateSeparator,
                        attributeSeparator,
                        appWidgetId
                    )
                )
                setTextViewText(
                    R.id.widgetLabel,
                    label ?: entityId
                )
                setViewVisibility(
                    R.id.widgetStaticOffline,
                    if (lastResolvedTextSuccess) View.GONE else View.VISIBLE
                )
                setOnClickPendingIntent(
                    R.id.widgetTextLayout,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        return views
    }

    override fun getAllWidgetIds(context: Context): List<Int> {
        return AppDatabase.getInstance(context).staticWidgetDao().getAll()?.map { it.id }.orEmpty()
    }

    private suspend fun resolveTextToShow(
        context: Context,
        entityId: String?,
        suggestedEntity: Entity<Map<String, Any>>?,
        attributeIds: String?,
        stateSeparator: String,
        attributeSeparator: String,
        appWidgetId: Int
    ): CharSequence? {
        val staticWidgetDao = AppDatabase.getInstance(context).staticWidgetDao()
        var entity: Entity<Map<String, Any>>? = null
        try {
            entity = if (suggestedEntity != null && suggestedEntity.entityId == entityId) {
                suggestedEntity
            } else {
                entityId?.let { integrationUseCase.getEntity(it) }
            }
            lastResolvedTextSuccess = true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            lastResolvedTextSuccess = false
        }
        if (attributeIds == null) {
            staticWidgetDao.updateWidgetLastUpdate(
                appWidgetId,
                entity?.state ?: staticWidgetDao.get(appWidgetId)?.lastUpdate ?: ""
            )
            return staticWidgetDao.get(appWidgetId)?.lastUpdate
        }

        var fetchedAttributes: Map<*, *>
        var attributeValues: List<String?>
        try {
            fetchedAttributes = entity?.attributes as? Map<*, *> ?: mapOf<String, String>()
            attributeValues =
                attributeIds.split(",").map { id -> fetchedAttributes.get(id)?.toString() }
            val lastUpdate =
                entity?.state.plus(if (attributeValues.isNotEmpty()) stateSeparator else "")
                    .plus(attributeValues.joinToString(attributeSeparator))
            staticWidgetDao.updateWidgetLastUpdate(appWidgetId, lastUpdate)
            lastResolvedTextSuccess = true
            return lastUpdate
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity state and attributes", e)
            lastResolvedTextSuccess = false
        }
        return staticWidgetDao.get(appWidgetId)?.lastUpdate
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val attributeSelection: ArrayList<String>? = extras.getStringArrayList(EXTRA_ATTRIBUTE_IDS)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val textSizeSelection: String? = extras.getString(EXTRA_TEXT_SIZE)
        val stateSeparatorSelection: String? = extras.getString(EXTRA_STATE_SEPARATOR)
        val attributeSeparatorSelection: String? = extras.getString(EXTRA_ATTRIBUTE_SEPARATOR)

        if (entitySelection == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            val staticWidgetDao = AppDatabase.getInstance(context).staticWidgetDao()
            Log.d(
                TAG,
                "Saving entity state config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator() +
                    "attribute: " + (attributeSelection ?: "N/A")
            )
            staticWidgetDao.add(
                StaticWidgetEntity(
                    appWidgetId,
                    entitySelection,
                    attributeSelection?.joinToString(","),
                    labelSelection,
                    textSizeSelection?.toFloatOrNull() ?: 30F,
                    stateSeparatorSelection ?: "",
                    attributeSeparatorSelection ?: "",
                    staticWidgetDao.get(appWidgetId)?.lastUpdate ?: ""
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override fun onEntityStateChanged(context: Context, entity: Entity<*>) {
        AppDatabase.getInstance(context).staticWidgetDao().getAll().orEmpty().forEach {
            if (it.entityId == entity.entityId) {
                mainScope.launch {
                    val views = getWidgetRemoteViews(context, it.id, entity as Entity<Map<String, Any>>)
                    AppWidgetManager.getInstance(context).updateAppWidget(it.id, views)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val staticWidgetDao = AppDatabase.getInstance(context).staticWidgetDao()
        appWidgetIds.forEach { appWidgetId ->
            staticWidgetDao.delete(appWidgetId)
        }
    }
}
