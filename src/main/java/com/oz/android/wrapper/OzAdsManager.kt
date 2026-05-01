package com.oz.android.wrapper

import android.app.Activity
import com.oz.android.ads.network.admobs.AdMobManager
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.config.OzAdsConfig
import com.oz.android.utils.listener.OzAdsResult
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
        pendingShows.remove(key)?.invoke()
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
    suspend fun init(
        activity: Activity,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): OzAdsResult<Unit> = suspendCancellableCoroutine { continuation ->
        // Use testDeviceIds from the stored config
        adMobManager.initializeMobileAdsSdk(config.testDeviceIds, activity) {
            initialized = true
            continuation.resume(OzAdsResult.Success(Unit))
            onSuccess?.invoke()
        }
    }

    fun isAdInitialized(): Boolean = initialized

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