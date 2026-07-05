package com.oz.android

import android.app.Activity
import com.oz.android.ads_core.admobs.AdMobManager
import com.oz.android.ads_core.admobs.AdmobNextManager
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.config.OzAdsConfig
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdsResult
import com.oz.android.utils.OzLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Business layer manager for handling ads.
 * Orchestrates configuration, initialization, and state management.
 */
class OzAdsManager private constructor(
    private val adMobManager: AdMobManager
) {

    @Volatile
    private var initialized = false

    @Volatile
    private var initStarted = false

    // Signals awaitInitialization() callers when init() completes.
    private val initDeferred = kotlinx.coroutines.CompletableDeferred<OzAdsResult<Unit>>()

    // 1. Centralized Configuration
    // We use a private backing field and expose an immutable getter
    @Volatile
    private var _config: OzAdsConfig = OzAdsConfig()
    val config: OzAdsConfig
        get() = _config

    // 2. Reactive State
    private val _enableAd = MutableStateFlow(_config.isAdEnabled)
    val enableAd = _enableAd.asStateFlow()

    // Fullscreen ad state (overlay ads like interstitial, app open)
    private val _isFullScreenAdShowing = MutableStateFlow(false)
    val isFullScreenAdShowing = _isFullScreenAdShowing.asStateFlow()

    // Ads state management (key -> state)
    private val adStates = ConcurrentHashMap<String, AdState>()

    // Ad store (key -> ad object)
    private val adStore = ConcurrentHashMap<String, Any>()

    // Pending show runnables
    private val pendingShows = ConcurrentHashMap<String, () -> Unit>()

    // Analytics logger hook (Fix 8)
    @Volatile
    var adEventLogger: ((event: String, key: String, reason: String?) -> Unit)? = null

    fun logAdEvent(event: String, key: String, reason: String? = null) {
        adEventLogger?.invoke(event, key, reason)
    }

    // ----------------------------------------------------------------
    // Configuration Methods
    // ----------------------------------------------------------------

    /**
     * Sets the global configuration for the Ads Manager.
     * This updates the local config object and synchronizes reactive flows.
     */
    fun setConfig(newConfig: OzAdsConfig) {
        _config = newConfig
        syncConfigState()
    }

    /**
     * Kotlin DSL style configuration update.
     * Allows updating specific fields without recreating the whole object manually.
     * Example: setConfig { copy(isAdEnabled = true) }
     */
    fun updateConfig(block: OzAdsConfig.() -> OzAdsConfig) {
        _config = _config.block()
        syncConfigState()
    }

    /**
     * Synchronize internal reactive flows with the current config.
     */
    private fun syncConfigState() {
        // Emit new value if config changed
        if (_enableAd.value != _config.isAdEnabled) {
            _enableAd.value = _config.isAdEnabled
        }
    }

    /**
     * Legacy support/Convenience to toggle ads directly
     */
    fun setEnableAd(shouldShow: Boolean) {
        updateConfig { copy(isAdEnabled = shouldShow) }
    }

    // ----------------------------------------------------------------
    // Fullscreen Ad State Methods
    // ----------------------------------------------------------------

    /**
     * Called when a fullscreen ad (overlay) starts showing.
     * Updates the reactive state that can be observed by the app.
     */
    fun onAdsFullScreenShowing() {
        _isFullScreenAdShowing.value = true
    }

    /**
     * Called when a fullscreen ad (overlay) is dismissed.
     * Updates the reactive state that can be observed by the app.
     */
    fun onAdsFullScreenDismissed() {
        _isFullScreenAdShowing.value = false
    }

    /**
     * Check if a fullscreen ad can be shown.
     * @return true if no fullscreen ad is currently showing.
     */
    fun canShowFullScreenAd(): Boolean {
        return !_isFullScreenAdShowing.value
    }

    // ----------------------------------------------------------------
    // Ad State & Storage Methods
    // ----------------------------------------------------------------

    fun getAdState(key: String): AdState {
        return adStates.getOrDefault(key, AdState.IDLE)
    }

    fun setAdState(key: String, state: AdState) {
        adStates[key] = state
    }

    fun putAdStateIfAbsent(key: String, state: AdState) {
        adStates.putIfAbsent(key, state)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getAd(key: String): T? {
        return adStore[key] as? T
    }

    fun setAd(key: String, ad: Any) {
        adStore[key] = ad
    }

    fun removeAd(key: String): Any? {
        return adStore.remove(key)
    }

    fun setPendingShow(key: String, runnable: () -> Unit) {
        pendingShows[key] = runnable
    }

    fun executePendingShow(key: String) {
        pendingShows.remove(key)?.let { runnable ->
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                runnable.invoke()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runnable.invoke()
                }
            }
        }
    }

    fun clearPendingShow(key: String) {
        pendingShows.remove(key)
    }

    // ----------------------------------------------------------------
    // Initialization & Singleton
    // ----------------------------------------------------------------

    /**
     * Initialize all ad networks using the values found in [config].
     *
     * @param activity The activity context for initialization
     * @param onSuccess Optional callback
     * @param onError Optional callback
     */
    private fun getAdMobAppId(context: android.content.Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
        } catch (e: Exception) {
            OzLog.e("OzAdsManager", "Failed to load AdMob App ID from manifest", e)
            null
        }
    }

    /**
     * Initialize the Mobile Ads SDK.
     * This should be called exactly once from MainActivity.
     * Other callers (e.g. SplashFragment) should use [awaitInitialization] instead.
     *
     * @param activity The activity context used for initialization
     * @param onSuccess Callback triggered when initialization completes successfully
     * @param onError Callback triggered if initialization fails
     */
    suspend fun init(
        activity: Activity,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): OzAdsResult<Unit> {
        if (initialized) {
            onSuccess?.invoke()
            return OzAdsResult.Success(Unit)
        }

        // If another initialization is already in progress, just await its completion.
        if (initStarted) {
            OzLog.d("OzAdsManager", "SDK initialization already in progress, awaiting existing init...")
            val result = initDeferred.await()
            if (initialized) {
                onSuccess?.invoke()
            }
            return result
        }

        initStarted = true
        val initStartTime = System.currentTimeMillis()
        OzLog.d("OzAdsManager", "SDK initialization started...")

        val result = suspendCancellableCoroutine<OzAdsResult<Unit>> { continuation ->
            if (config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                // PATH A: GMA Next-Gen SDK — delegates to AdmobNextManager
                val appId = getAdMobAppId(activity) ?: "ca-app-pub-3940256099942544~3347511713"
                AdmobNextManager.getInstance().initializeMobileAdsSdk(appId, activity) {
                    val elapsed = System.currentTimeMillis() - initStartTime
                    OzLog.d("OzAdsManager", "✅ SDK fully initialized in ${elapsed}ms (Next-Gen)")
                    initialized = true
                    initDeferred.complete(OzAdsResult.Success(Unit))
                    onSuccess?.invoke()
                    if (continuation.isActive) continuation.resume(OzAdsResult.Success(Unit))
                }
            } else {
                // PATH B: Standard GMS AdMob SDK — delegates to AdMobManager
                adMobManager.initializeMobileAdsSdk(config.testDeviceIds, activity) {
                    val elapsed = System.currentTimeMillis() - initStartTime
                    OzLog.d("OzAdsManager", "✅ SDK fully initialized in ${elapsed}ms (Standard GMS)")
                    initialized = true
                    initDeferred.complete(OzAdsResult.Success(Unit))
                    onSuccess?.invoke()
                    if (continuation.isActive) continuation.resume(OzAdsResult.Success(Unit))
                }
            }
        }

        return result
    }

    fun isAdInitialized(): Boolean = initialized

    /**
     * Suspend until [init] completes, or until [INIT_TIMEOUT_MS] elapses — whichever comes first.
     *
     * Intended for callers that do not own initialization (e.g. SplashFragment) but need the SDK
     * to be reasonably ready before loading ads. After the timeout, ads proceed anyway — the SDK
     * initialization continues in the background and will be fully ready for subsequent requests.
     *
     * - Returns immediately if already initialized.
     * - Suspends for up to [INIT_TIMEOUT_MS] ms waiting for [init] to signal completion.
     * - Returns [OzAdsResult.Success] regardless (either SDK ready, or timeout elapsed).
     */
    suspend fun awaitInitialization(): OzAdsResult<Unit> {
        if (initialized) return OzAdsResult.Success(Unit)

        val result = withTimeoutOrNull(INIT_TIMEOUT_MS) { initDeferred.await() }
        if (result == null) {
            OzLog.w("OzAdsManager", "SDK init timeout after ${INIT_TIMEOUT_MS}ms — proceeding to load ads. Init continues in background.")
            initialized = true
        }
        return OzAdsResult.Success(Unit)
    }

    fun openAdInspector(context: android.content.Context) {
        if (config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
            AdmobNextManager.getInstance().openAdInspector(context)
        } else {
            adMobManager.openAdInspector(context)
        }
    }

    companion object {
        /** Maximum time to wait for SDK initialization before proceeding to load ads anyway. */
        private const val INIT_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: OzAdsManager? = null

        fun getInstance(adMobManager: AdMobManager = AdMobManager.getInstance()): OzAdsManager {
            return instance ?: synchronized(this) {
                instance ?: OzAdsManager(adMobManager).also { instance = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}