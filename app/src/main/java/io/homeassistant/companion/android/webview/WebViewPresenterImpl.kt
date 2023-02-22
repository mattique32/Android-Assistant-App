package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import android.graphics.Color
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.matter.MatterFrontendCommissioningStatus
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.util.UrlHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import io.homeassistant.companion.android.common.R as commonR

class WebViewPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    private val matterUseCase: MatterManager
) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private val view = context as WebView

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var serverId: Int = ServerManager.SERVER_ID_ACTIVE

    private var url: URL? = null
    private var urlForServer: Int? = null

    private val _matterCommissioningStatus = MutableStateFlow(MatterFrontendCommissioningStatus.NOT_STARTED)

    private var matterCommissioningIntentSender: IntentSender? = null

    init {
        updateActiveServer()
    }

    override fun onViewReady(path: String?) {
        mainScope.launch {
            val oldUrl = url
            val oldUrlForServer = urlForServer

            var server = serverManager.getServer(serverId)
            if (server == null) {
                setActiveServer(ServerManager.SERVER_ID_ACTIVE)
                server = serverManager.getServer(serverId)
            }

            try {
                if (serverManager.authenticationRepository(serverId).getSessionState() == SessionState.ANONYMOUS) return@launch
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unable to get server session state, not continuing")
                return@launch
            }

            val serverConnectionInfo = server?.connection
            url = serverConnectionInfo?.getUrl(
                serverConnectionInfo.isInternal() || (serverConnectionInfo.prioritizeInternal && !DisabledLocationHandler.isLocationEnabled(view as Context))
            )
            urlForServer = server?.id

            if (path != null && !path.startsWith("entityId:")) {
                url = UrlHandler.handle(url, path)
            }

            /*
            We only want to cause the UI to reload if the server or URL that we need to load has
            changed. An example of this would be opening the app on wifi with a local url then
            loosing wifi signal and reopening app. Without this we would still be trying to use the
            internal url externally.
             */
            if (oldUrlForServer != urlForServer || oldUrl?.host != url?.host) {
                view.loadUrl(
                    Uri.parse(url.toString())
                        .buildUpon()
                        .appendQueryParameter("external_auth", "1")
                        .build()
                        .toString(),
                    oldUrlForServer == urlForServer
                )
            }
        }
    }

    override fun getActiveServer(): Int = serverId

    override fun updateActiveServer() {
        if (serverManager.isRegistered()) {
            serverManager.getServer()?.let {
                serverId = it.id
            }
        }
    }

    override fun setActiveServer(id: Int) {
        serverManager.getServer(id)?.let {
            if (serverManager.authenticationRepository(id).getSessionState() == SessionState.CONNECTED) {
                serverManager.activateServer(id)
                serverId = id
            }
        }
    }

    override fun checkSecurityVersion() {
        mainScope.launch {
            try {
                if (!serverManager.integrationRepository(serverId).isHomeAssistantVersionAtLeast(2021, 1, 5)) {
                    if (serverManager.integrationRepository(serverId).shouldNotifySecurityWarning()) {
                        view.showError(WebView.ErrorType.SECURITY_WARNING)
                    } else {
                        Log.w(TAG, "Still not updated but have already notified.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting version/notifying of security issue.", e)
            }
        }
    }

    override fun onGetExternalAuth(context: Context, callback: String, force: Boolean) {
        mainScope.launch {
            try {
                view.setExternalAuth("$callback(true, ${serverManager.authenticationRepository(serverId).retrieveExternalAuthentication(force)})")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to retrieve external auth", e)
                val anonymousSession = serverManager.getServer(serverId) == null || serverManager.authenticationRepository(serverId).getSessionState() == SessionState.ANONYMOUS
                view.setExternalAuth("$callback(false)")
                view.showError(
                    errorType = when {
                        anonymousSession -> WebView.ErrorType.AUTHENTICATION
                        e is SSLException || (e is SocketTimeoutException && e.suppressed.any { it is SSLException }) -> WebView.ErrorType.SSL
                        else -> WebView.ErrorType.TIMEOUT
                    },
                    description = when {
                        anonymousSession -> null
                        e is SSLHandshakeException || (e is SocketTimeoutException && e.suppressed.any { it is SSLHandshakeException }) -> context.getString(commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
                        e is SSLException || (e is SocketTimeoutException && e.suppressed.any { it is SSLException }) -> context.getString(commonR.string.webview_error_SSL_INVALID)
                        else -> null
                    }
                )
            }
        }
    }

    override fun onRevokeExternalAuth(callback: String) {
        mainScope.launch {
            try {
                serverManager.getServer(serverId)?.let {
                    serverManager.authenticationRepository(it.id).revokeSession()
                    serverManager.removeServer(it.id)
                }
                view.setExternalAuth("$callback(true)")
                view.relaunchApp()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to revoke session", e)
                view.setExternalAuth("$callback(false)")
            }
        }
    }

    override fun isFullScreen(): Boolean = runBlocking {
        prefsRepository.isFullScreenEnabled()
    }

    override fun getScreenOrientation(): String? = runBlocking {
        prefsRepository.getScreenOrientation()
    }

    override fun isKeepScreenOnEnabled(): Boolean = runBlocking {
        prefsRepository.isKeepScreenOnEnabled()
    }

    override fun isPinchToZoomEnabled(): Boolean = runBlocking {
        prefsRepository.isPinchToZoomEnabled()
    }

    override fun isWebViewDebugEnabled(): Boolean = runBlocking {
        prefsRepository.isWebViewDebugEnabled()
    }

    override fun isAppLocked(): Boolean = runBlocking {
        if (serverManager.isRegistered()) {
            try {
                serverManager.integrationRepository(serverId).isAppLocked()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Cannot determine app locked state")
                false
            }
        } else false
    }

    override fun setAppActive(active: Boolean) = runBlocking {
        serverManager.getServer(serverId)?.let {
            serverManager.integrationRepository(serverId).setAppActive(active)
        } ?: Unit
    }

    override fun isLockEnabled(): Boolean = runBlocking {
        serverManager.getServer(serverId)?.let {
            serverManager.authenticationRepository(serverId).isLockEnabled()
        } ?: false
    }

    override fun isAutoPlayVideoEnabled(): Boolean = runBlocking {
        prefsRepository.isAutoPlayVideoEnabled()
    }

    override fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean = runBlocking {
        prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()
    }

    override fun sessionTimeOut(): Int = runBlocking {
        serverManager.getServer(serverId)?.let {
            serverManager.integrationRepository(serverId).getSessionTimeOut()
        } ?: 0
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun isSsidUsed(): Boolean =
        serverManager.getServer(serverId)?.connection?.internalSsids?.isNotEmpty() == true

    override fun getAuthorizationHeader(): String = runBlocking {
        serverManager.getServer(serverId)?.let {
            serverManager.authenticationRepository(serverId).buildBearerToken()
        } ?: ""
    }

    override suspend fun parseWebViewColor(webViewColor: String): Int = withContext(Dispatchers.IO) {
        var color = 0

        Log.d(TAG, "Try getting color from webview color \"$webViewColor\".")
        if (webViewColor.isNotEmpty() && webViewColor != "null") {
            try {
                color = parseColorWithRgb(webViewColor)
                Log.i(TAG, "Found color $color.")
            } catch (e: Exception) {
                Log.w(TAG, "Could not get color from webview.", e)
            }
        } else {
            Log.w(TAG, "Could not get color from webview. Color \"$webViewColor\" is not a valid color.")
        }

        if (color == 0) {
            Log.w(TAG, "Couldn't get color.")
        }

        return@withContext color
    }

    private fun parseColorWithRgb(colorString: String): Int {
        val c: Pattern = Pattern.compile("rgb *\\( *([0-9]+), *([0-9]+), *([0-9]+) *\\)")
        val m: Matcher = c.matcher(colorString)
        return if (m.matches()) {
            Color.rgb(
                m.group(1).toInt(),
                m.group(2).toInt(),
                m.group(3).toInt()
            )
        } else Color.parseColor(colorString)
    }

    override fun appCanCommissionMatterDevice(): Boolean = matterUseCase.appSupportsCommissioning()

    override fun startCommissioningMatterDevice(context: Context) {
        if (_matterCommissioningStatus.value != MatterFrontendCommissioningStatus.REQUESTED) {
            _matterCommissioningStatus.tryEmit(MatterFrontendCommissioningStatus.REQUESTED)

            matterUseCase.startNewCommissioningFlow(
                context,
                { intentSender ->
                    Log.d(TAG, "Matter commissioning is ready")
                    matterCommissioningIntentSender = intentSender
                    _matterCommissioningStatus.tryEmit(MatterFrontendCommissioningStatus.IN_PROGRESS)
                },
                { e ->
                    Log.e(TAG, "Matter commissioning couldn't be prepared", e)
                    _matterCommissioningStatus.tryEmit(MatterFrontendCommissioningStatus.ERROR)
                }
            )
        } // else already waiting for a result, don't send another request
    }

    override fun getMatterCommissioningStatusFlow(): Flow<MatterFrontendCommissioningStatus> =
        _matterCommissioningStatus.asStateFlow()

    override fun getMatterCommissioningIntent(): IntentSender? {
        val intent = matterCommissioningIntentSender
        matterCommissioningIntentSender = null
        return intent
    }

    override fun confirmMatterCommissioningError() {
        _matterCommissioningStatus.tryEmit(MatterFrontendCommissioningStatus.NOT_STARTED)
    }
}
