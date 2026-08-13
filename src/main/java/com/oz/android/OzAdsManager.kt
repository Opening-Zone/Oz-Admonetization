package com.oz.android

import android.app.Activity
import com.oz.android.ads_core.BuildConfig
import com.oz.android.ads_core.admobs.AdMobManager
import com.oz.android.ads_core.admobs.AdmobNextManager
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.config.OzAdsConfig
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.event.OzEventLogger
import com.oz.android.utils.listener.OzAdsResult
import com.oz.android.utils.OzLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * Network-agnostic consent check.
     *
     * Populated by the network init flow (e.g. [AdMobManager] / [AdmobNextManager]) after
     * consent gathering completes. Defaults to `true` (permissive) so that non-GDPR
     * regions or test builds without a consent flow are not silently blocked.
     *
     * Layer boundary: OzAdsManager (L1) must NOT import concrete network classes (L3/L4).
     * The AdMob init layer calls [setConsentChecker] once, passing a lambda that wraps
     * [GoogleMobileAdsConsentManager.canRequestAds] — keeping L1 fully network-agnostic.
     */
    @Volatile
    private var consentChecker: () -> Boolean = { true }

    /**
     * Called by the network-specific init layer (L3) to inject the consent check logic.
     * Must be called before any ad is loaded; typically invoked at the end of the consent flow.
     */
    fun setConsentChecker(checker: () -> Boolean) {
        consentChecker = checker
    }

    /**
     * Returns whether the current consent state allows ad requests.
     * Delegates to the injected [consentChecker]; never calls L3 classes directly.
     */
    fun canRequestAds(): Boolean = consentChecker()

    // Ads state management (key -> state)
    private val adStates = ConcurrentHashMap<String, AdState>()

    // Ad store (key -> ad object)
    private val adStore = ConcurrentHashMap<String, Any>()

    // Pending show runnables
    private val pendingShows = ConcurrentHashMap<String, () -> Unit>()

    // Analytics logger hook (Fix 8)
    /**
     * Callback for custom ad event logging.
     * WARNING: This callback may be invoked from GMA background threads.
     * Implementations MUST be thread-safe.
     */
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
     * @param consentChecker Optional lambda that returns whether ads can be requested based on
     *   user consent. Provide this from the network-init layer (L3) — e.g.
     *   `{ GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds }`. When null,
     *   the existing [consentChecker] is unchanged (defaults to permissive `{ true }`).
     */
    suspend fun init(
        activity: Activity,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        consentChecker: (() -> Boolean)? = null
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
        OzLog.d("OzAdsManager", "SDK initialization started (v${BuildConfig.LIB_VERSION})...")

        // Register the consent checker supplied by the caller/network init layer.
        if (consentChecker != null) {
            setConsentChecker(consentChecker)
        } else {
            OzLog.w("OzAdsManager", "No consentChecker provided to init() — ads will load using default permissive check.")
        }

        val timeoutMs = config.initTimeoutMs
        val initResult = withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine<OzAdsResult<Unit>> { continuation ->
                if (config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                    // PATH A: GMA Next-Gen SDK — delegates to AdmobNextManager
                    val appId = getAdMobAppId(activity)
                    if (appId.isNullOrEmpty()) {
                        OzLog.e("OzAdsManager", "AdMob APPLICATION_ID missing in manifest!")
                        OzEventLogger.logAdsSdkInitException(activity, "app_id_missing")
                        if (BuildConfig.DEBUG) {
                            error("AdMob APPLICATION_ID missing in manifest!")
                        } else {
                            if (continuation.isActive) continuation.resume(OzAdsResult.Failure(IllegalStateException("AdMob APPLICATION_ID missing")))
                            return@suspendCancellableCoroutine
                        }
                    }
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
        }

        if (initResult == null) {
            OzLog.w("OzAdsManager", "SDK init timed out in init() after ${timeoutMs}ms — proceeding.")
            OzEventLogger.logAdsSdkInitTimeoutInit(activity, timeoutMs)
            initialized = true
            initDeferred.complete(OzAdsResult.Success(Unit))
            return OzAdsResult.Success(Unit)
        }

        return initResult
    }

    fun isAdInitialized(): Boolean = initialized

    fun isInitStarted(): Boolean = initStarted

    /**
     * Suspend until [init] completes, or until [INIT_TIMEOUT_MS] elapses — whichever comes first.
     *
     * Intended for callers that do not own initialization (e.g. SplashFragment) but need all
     * mediation adapters to be ready before requesting their first ad. Waiting here is necessary
     * because mediation adapters perform full initialization inside the SDK callback; requesting
     * an ad before that callback fires means the adapter inventory is not yet available.
     *
     * The timeout guard: if the callback has not fired within [INIT_TIMEOUT_MS] (5 s vs the
     * typical ~500 ms), the SDK or a mediation adapter is stuck in an abnormal state.
     * In that case we mark [initialized] = true and proceed anyway — the app must not be blocked
     * indefinitely, and any adapter that recovers later will still serve ads on subsequent loads.
     *
     * - Returns immediately if already initialized.
     * - Suspends for up to [INIT_TIMEOUT_MS] ms waiting for [init] to signal completion.
     * - Returns [OzAdsResult.Success] regardless (either SDK ready, or timeout elapsed).
     */
    suspend fun awaitInitialization(context: android.content.Context? = null): OzAdsResult<Unit> {
        if (initialized) return OzAdsResult.Success(Unit)

        val timeoutMs = config.initTimeoutMs
        val result = withTimeoutOrNull(timeoutMs.milliseconds) { initDeferred.await() }
        if (result == null) {
            // Timeout elapsed — the SDK/mediation adapter is taking abnormally long.
            // Mark as initialized so subsequent init() calls are not blocked, then proceed.
            // Adapters that finish later will still serve ads on their next fill opportunity.
            OzLog.w("OzAdsManager", "SDK init timeout after ${timeoutMs}ms — proceeding to load ads. Init continues in background.")
            context?.let { OzEventLogger.logAdsSdkInitTimeoutAwait(it, timeoutMs) }
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
        /**
         * Maximum time to wait for the mediation adapters to fully initialize before proceeding.
         *
         * Background: AdMob itself is fire-and-forget — calling MobileAds.initialize() is
         * enough for AdMob-only requests. However, mediation adapters (Meta, AppLovin, etc.)
         * perform their own full initialization inside the same callback and must complete
         * before their inventory is available. We therefore wait for the initialization
         * callback to fire rather than proceeding immediately after calling initialize().
         *
         * The timeout exists because in rare race conditions (low-end device + slow network
         * + heavy mediation stack) the callback can be delayed beyond acceptable UX limits.
         * Average initialization time in production is ~500 ms; 5 s is deliberately generous
         * to cover the long tail while still protecting against an indefinite hang.
         */
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