package com.oz.android

import android.app.Activity
import com.oz.android.ads_core.admobs.AdMobManager
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.config.OzAdsConfig
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdsResult
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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
            Log.e("OzAdsManager", "Failed to load AdMob App ID from manifest", e)
            null
        }
    }

    suspend fun init(
        activity: Activity,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): OzAdsResult<Unit> = suspendCancellableCoroutine { continuation ->
        if (config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
            val appId = getAdMobAppId(activity) ?: "ca-app-pub-3940256099942544~3347511713"
            Log.d("OzAdsManager", "Initializing Next-Gen Mobile Ads SDK with App ID: $appId")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    MobileAds.initialize(
                        activity,
                        InitializationConfig.Builder(appId).build()
                    ) {
                        initialized = true
                        continuation.resume(OzAdsResult.Success(Unit))
                        onSuccess?.invoke()
                    }
                } catch (e: Throwable) {
                    initialized = false
                    continuation.resume(OzAdsResult.Failure(e))
                    onError?.invoke(e)
                }
            }
        } else {
            // Use testDeviceIds from the stored config
            adMobManager.initializeMobileAdsSdk(config.testDeviceIds, activity) {
                initialized = true
                continuation.resume(OzAdsResult.Success(Unit))
                onSuccess?.invoke()
            }
        }
    }

    fun isAdInitialized(): Boolean = initialized

    fun openAdInspector(context: android.content.Context) {
        if (config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
            try {
                MobileAds.openAdInspector { error ->
                    if (error != null) {
                        Log.e("OzAdsManager", "Next-Gen Ad Inspector closed with error: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("OzAdsManager", "Failed to open Next-Gen Ad Inspector", e)
            }
        } else {
            try {
                val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
                val listenerClass = Class.forName("com.google.android.gms.ads.OnAdInspectorClosedListener")
                val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onAdInspectorClosed") {
                        val error = args[0]
                        if (error != null) {
                            try {
                                val getMessageMethod = error.javaClass.getMethod("getMessage")
                                val message = getMessageMethod.invoke(error) as? String
                                Log.e("OzAdsManager", "Standard Ad Inspector error: $message")
                            } catch (ex: Exception) {
                                Log.e("OzAdsManager", "Standard Ad Inspector closed with error")
                            }
                        }
                    }
                    null
                }
                val openAdInspectorMethod = mobileAdsClass.getMethod(
                    "openAdInspector",
                    android.content.Context::class.java,
                    listenerClass
                )
                openAdInspectorMethod.invoke(null, context, proxyListener)
            } catch (e: Exception) {
                Log.e("OzAdsManager", "Failed to open standard AdMob Ad Inspector reflectively", e)
            }
        }
    }

    companion object {
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