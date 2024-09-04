package io.homeassistant.companion.android.database.widget.graph

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.homeassistant.companion.android.database.widget.WidgetDao
import kotlinx.coroutines.flow.Flow

@Dao
interface GraphWidgetDao : WidgetDao {

    @Query("SELECT * FROM graph_widget WHERE id = :id")
    fun get(id: Int): GraphWidgetEntity?

    @Transaction
    @Query("SELECT * FROM graph_widget WHERE id = :id")
    suspend fun getWithHistories(id: Int): GraphWidgetWithHistories?

    @Insert(onConflict = OnConflictStrategy.NONE)
    suspend fun add(graphWidgetEntity: GraphWidgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(graphWidgetHistoryEntity: GraphWidgetHistoryEntity)

    @Query("DELETE FROM graph_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM graph_widget WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM graph_widget")
    suspend fun getAll(): List<GraphWidgetEntity>

    @Query("SELECT * FROM graph_widget")
    fun getAllFlow(): Flow<List<GraphWidgetEntity>>

    @Query("UPDATE graph_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)

    @Query("DELETE FROM graph_widget_history WHERE graph_widget_id = :appWidgetId AND sent_state < :cutoffTime")
    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTime: Long)

    @Query("""
        SELECT * FROM graph_widget_history 
        WHERE graph_widget_id = :appWidgetId 
        ORDER BY sent_state DESC 
        LIMIT 2
    """)
    suspend fun getLastTwoEntries(appWidgetId: Int): List<GraphWidgetHistoryEntity>

    @Query("""
        SELECT * FROM graph_widget_history 
        WHERE graph_widget_id = :appWidgetId 
        ORDER BY sent_state DESC 
        LIMIT 1
    """)
    suspend fun getLastGraphWidgetHistory(appWidgetId: Int): GraphWidgetHistoryEntity?
}
