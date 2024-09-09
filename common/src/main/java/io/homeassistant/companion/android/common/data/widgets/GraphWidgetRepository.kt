package io.homeassistant.companion.android.common.data.widgets

import io.homeassistant.companion.android.common.data.BaseDaoWidgetRepository
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories

interface GraphWidgetRepository : BaseDaoWidgetRepository<GraphWidgetEntity> {

    suspend fun getGraphWidgetWithHistories(id: Int): GraphWidgetWithHistories?

    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: Long)

    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTimeInHours: Int)

    suspend fun insertGraphWidgetHistory(historyEntity: GraphWidgetHistoryEntity)

    fun updateWidgetLastLabel(appWidgetId: Int, labelText: String)

    fun updateWidgetTimeRange(appWidgetId: Int, timeRange: Int)
}
