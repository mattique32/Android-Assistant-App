package io.homeassistant.companion.android.conversation

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.conversation.views.AssistMessage
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    audioUrlPlayer: AudioUrlPlayer,
    private val wearPrefsRepository: WearPrefsRepository,
    application: Application
) : AssistViewModelBase(serverManager, audioRecorder, audioUrlPlayer, application) {

    companion object {
        private const val TAG = "ConvViewModel"
    }

    private var useAssistPipeline = false
    private var useAssistPipelineStt = false

    var inputMode by mutableStateOf(AssistInputMode.BLOCKED)
        private set

    var isHapticEnabled by mutableStateOf(false)
        private set

    var currentPipeline by mutableStateOf<AssistPipelineResponse?>(null)
        private set

    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

    private val _pipelines = mutableStateListOf<AssistPipelineResponse>()
    val pipelines: List<AssistPipelineResponse> = _pipelines

    private val startMessage = AssistMessage(application.getString(commonR.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    /** @return `true` if the voice input intent should be fired */
    suspend fun onCreate(): Boolean {
        val supported = checkAssistSupport()
        if (!serverManager.isRegistered()) {
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.not_registered), isInput = false)
            )
        } else if (supported == null) { // Couldn't get config
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.assist_connnect), isInput = false)
            )
        } else if (!supported) { // Core too old or missing component
            val usingPipelines = serverManager.getServer()?.version?.isAtLeast(2023, 5) == true
            _conversation.clear()
            _conversation.add(
                AssistMessage(
                    if (usingPipelines) {
                        app.getString(commonR.string.no_assist_support, "2023.5", app.getString(commonR.string.no_assist_support_assist_pipeline))
                    } else {
                        app.getString(commonR.string.no_assist_support, "2023.1", app.getString(commonR.string.no_assist_support_conversation))
                    },
                    isInput = false
                )
            )
        } else {
            if (serverManager.getServer()?.version?.isAtLeast(2023, 5) == true) {
                viewModelScope.launch {
                    loadPipelines()
                }
            }

            return setPipeline(null)
        }

        return false
    }

    override fun getInput(): AssistInputMode = inputMode

    override fun setInput(inputMode: AssistInputMode) {
        this.inputMode = inputMode
    }

    private suspend fun checkAssistSupport(): Boolean? {
        isHapticEnabled = wearPrefsRepository.getWearHapticFeedback()
        if (!serverManager.isRegistered()) return false

        val config = serverManager.webSocketRepository().getConfig()
        val onConversationVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 1, 0)
        val onPipelineVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 5, 0)

        useAssistPipeline = onPipelineVersion
        return if ((onConversationVersion && !onPipelineVersion && config == null) || (onPipelineVersion && config == null)) {
            null // Version OK but couldn't get config (offline)
        } else {
            (onConversationVersion && !onPipelineVersion && config?.components?.contains("conversation") == true) ||
                (onPipelineVersion && config?.components?.contains("assist_pipeline") == true)
        }
    }

    private suspend fun loadPipelines() {
        val pipelines = serverManager.webSocketRepository().getAssistPipelines()
        pipelines?.let { _pipelines.addAll(it.pipelines) }
    }

    fun usePipelineStt(): Boolean = useAssistPipelineStt

    fun changePipeline(id: String) = viewModelScope.launch {
        if (id == currentPipeline?.id) return@launch

        stopRecording()
        stopPlayback()

        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?): Boolean {
        val pipeline = if (useAssistPipeline) {
            _pipelines.firstOrNull { it.id == id } ?: serverManager.webSocketRepository().getAssistPipeline(id)
        } else {
            null
        }

        useAssistPipelineStt = false
        if (pipeline != null || !useAssistPipeline) {
            currentPipeline = pipeline

            _conversation.clear()
            _conversation.add(startMessage)
            clearPipelineData()
            if (pipeline != null && hasMicrophone && pipeline.sttEngine != null) {
                if (hasPermission || requestSilently) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    useAssistPipelineStt = true
                    onMicrophoneInput()
                } else {
                    inputMode = AssistInputMode.TEXT
                }
            } else {
                inputMode = AssistInputMode.TEXT
            }
        } else {
            inputMode = AssistInputMode.BLOCKED
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.assist_error), isInput = false)
            )
        }

        return inputMode == AssistInputMode.TEXT
    }

    fun updateSpeechResult(commonResult: String) = runAssistPipeline(commonResult)

    fun onMicrophoneInput() {
        if (!hasPermission) {
            requestPermission?.let { it() }
            return
        }

        if (inputMode == AssistInputMode.VOICE_ACTIVE) {
            stopRecording()
            return
        }

        val recording = try {
            audioRecorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting recording", e)
            false
        }

        if (recording) {
            setupRecorderQueue()
            inputMode = AssistInputMode.VOICE_ACTIVE
            runAssistPipeline(null)
        } else {
            _conversation.add(AssistMessage(app.getString(commonR.string.assist_error), isInput = false, isError = true))
        }
    }

    private fun runAssistPipeline(text: String?) {
        val isVoice = text == null

        val userMessage = AssistMessage(text ?: "…", isInput = true)
        _conversation.add(userMessage)
        val haMessage = AssistMessage("…", isInput = false)
        if (!isVoice) _conversation.add(haMessage)
        var message = if (isVoice) userMessage else haMessage

        runAssistPipelineInternal(
            text,
            currentPipeline
        ) { newMessage, isInput, isError ->
            _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                _conversation[index] = message.copy(
                    message = newMessage,
                    isInput = isInput ?: message.isInput,
                    isError = isError
                )
                if (isInput == true) {
                    _conversation.add(haMessage)
                    message = haMessage
                }
            }
        }
    }

    fun setPermissionInfo(hasPermission: Boolean, callback: () -> Unit) {
        this.hasPermission = hasPermission
        requestPermission = callback
    }

    fun onPermissionResult(granted: Boolean, voiceInputIntent: (() -> Unit)) {
        hasPermission = granted
        useAssistPipelineStt = currentPipeline?.sttEngine != null && granted
        if (granted) {
            inputMode = AssistInputMode.VOICE_INACTIVE
            onMicrophoneInput()
        } else if (requestSilently) { // Don't notify the user if they haven't explicitly requested
            inputMode = AssistInputMode.TEXT
            voiceInputIntent()
        }
        requestSilently = false
    }

    fun onConversationScreenHidden() {
        stopRecording()
        stopPlayback()
    }

    fun onPause() {
        requestPermission = null
        stopRecording()
        stopPlayback()
    }
}
