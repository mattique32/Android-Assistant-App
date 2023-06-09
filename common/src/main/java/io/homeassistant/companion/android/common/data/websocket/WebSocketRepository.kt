package io.homeassistant.companion.android.common.data.websocket

import dagger.assisted.AssistedFactory
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketRepositoryImpl
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CurrentUserResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetTlvResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getConnectionState(): WebSocketState?
    fun shutdown()
    suspend fun sendPing(): Boolean
    suspend fun getCurrentUser(): CurrentUserResponse?
    suspend fun getConfig(): GetConfigResponse?
    suspend fun getStates(): List<EntityResponse<Any>>?
    suspend fun getAreaRegistry(): List<AreaRegistryResponse>?
    suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>?
    suspend fun getEntityRegistry(): List<EntityRegistryResponse>?
    suspend fun getServices(): List<DomainResponse>?
    suspend fun getStateChanges(): Flow<StateChangedEvent>?
    suspend fun getStateChanges(entityIds: List<String>): Flow<TriggerEvent>?
    suspend fun getCompressedStateAndChanges(): Flow<CompressedStateChangedEvent>?
    suspend fun getCompressedStateAndChanges(entityIds: List<String>): Flow<CompressedStateChangedEvent>?
    suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>?
    suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>?
    suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>?
    suspend fun getTemplateUpdates(template: String): Flow<TemplateUpdatedEvent>?
    suspend fun getNotifications(): Flow<Map<String, Any>>?
    suspend fun ackNotification(confirmId: String): Boolean
    suspend fun commissionMatterDevice(code: String): MatterCommissionResponse?
    suspend fun commissionMatterDeviceOnNetwork(pin: Long): MatterCommissionResponse?
    suspend fun getThreadDatasets(): List<ThreadDatasetResponse>?
    suspend fun getThreadDatasetTlv(datasetId: String): ThreadDatasetTlvResponse?
    suspend fun addThreadDataset(tlv: ByteArray): Boolean

    /**
     * Get an Assist response for the given text input. For core >= 2023.5, use [runAssistPipelineForText]
     * instead.
     */
    suspend fun getConversation(speech: String): ConversationResponse?

    /**
     * Get information about an Assist pipeline.
     * @param pipelineId the ID of the pipeline to get details for, if not specified the preferred
     * pipeline will be returned
     * @return [AssistPipelineResponse] detailing the Assist pipeline, or `null` if not found or no
     * response.
     */
    suspend fun getAssistPipeline(pipelineId: String? = null): AssistPipelineResponse?

    /**
     * @return [AssistPipelineListResponse] listing all Assist pipelines and which one is preferred.
     */
    suspend fun getAssistPipelines(): AssistPipelineListResponse?

    /**
     * Run the Assist pipeline for the given text input
     * @return a Flow that will emit all events for the pipeline
     */
    suspend fun runAssistPipelineForText(
        text: String,
        pipelineId: String? = null,
        conversationId: String? = null
    ): Flow<AssistPipelineEvent>?

    /**
     * Run the Assist pipeline for voice input
     * @return a Flow that will emit all events for the pipeline
     */
    suspend fun runAssistPipelineForVoice(
        sampleRate: Int,
        outputTts: Boolean,
        pipelineId: String? = null,
        conversationId: String? = null
    ): Flow<AssistPipelineEvent>?
}

@AssistedFactory
interface WebSocketRepositoryFactory {
    fun create(serverId: Int): WebSocketRepositoryImpl
}
