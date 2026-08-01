package com.oz.android.oz_ads

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.OzAdsManager
import com.oz.android.utils.event.OzEventLogger
import com.oz.android.utils.OzLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Abstract base class for all OzAds types
 * Implements common ad features and properties
 *
 * Manages preloaded ads by key (key represents placement, not the ad ID)
 * Ads have 4 states: IDLE, LOADING, LOADED, SHOWING
 *
 * Concrete implementations (such as AdmobInlineAds, AdmobOverlayAds) will extend this class
 */
abstract class OzAds<AdType> : ViewGroup {
    @JvmOverloads
    constructor(
        context: Context?,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr)

    companion object {
        private const val TAG = "OzAds"
    }

    // The single key managed by this view instance
    protected var adKey: String? = null

    // Coroutine job for the SDK-init retry queue — cancelled on destroy to prevent stale loads
    private var initRetryJob: Job? = null

    //listener
    var listener: OzAdListener<AdType>? = null

    // Abstract/open properties to be provided by concrete format implementations
    open val adFormat: String? = null
    open fun getAdUnitId(key: String): String? = null

    /**
     * Implementation of isAdEnable() from the interface
     * @return true if ad should be shown or load, false otherwise
     */
    fun isAdEnable(): Boolean {
        return OzAdsManager.getInstance().enableAd.value
    }

    /**
     * Implementation of setPreloadKey() from the interface.
     * Sets the single ad key for this view instance and pre-registers its state.
     * @param key The ad key to be managed by this instance.
     */
    fun setPreloadKey(key: String) {
        this.adKey = key
        OzLog.d(TAG, "Ad key set to: $key")
        // Set state to IDLE if not already present
        OzAdsManager.getInstance().putAdStateIfAbsent(key, AdState.IDLE)
    }

    /**
     * Get the state of the ad with the given key
     * @param key Key to identify the ad
     * @return Current AdState, or IDLE if not present
     */
    fun getAdState(key: String): AdState {
        return OzAdsManager.getInstance().getAdState(key)
    }

    /**
     * Implementation of loadAd() from the interface.
     * Loads the ad for the key managed by this instance.
     */
    open fun loadAd() {
        val key = adKey
        if (key == null) {
            OzLog.w(TAG, "Ad key not set. Call setPreloadKey() first.")
            return
        }

        OzEventLogger.logAdLoadAttempt(context, adUnitId = getAdUnitId(key), adFormat = adFormat, key = key)

        if (!isAdEnable()) {
            OzLog.d(TAG, "Should not show ad, skipping showAds() for key: $key")
            OzEventLogger.logAdSkip(context, adUnitId = getAdUnitId(key), adFormat = adFormat, reason = "ad_disabled", key = key)
            return
        }

        if (!OzAdsManager.getInstance().canRequestAds()) {
            OzLog.w(TAG, "Consent not granted. Cannot load ad for key: $key")
            OzEventLogger.logAdSkip(context, adUnitId = getAdUnitId(key), adFormat = adFormat, reason = "consent_not_granted", key = key)
            return
        }

        if (!OzAdsManager.getInstance().isAdInitialized()) {
            OzLog.w(TAG, "OzAdsManager is not initialized — queueing load for key: $key")
            OzEventLogger.logAdSkip(context, adUnitId = getAdUnitId(key), adFormat = adFormat, reason = "sdk_not_initialized", key = key)

            val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: ProcessLifecycleOwner.get().lifecycleScope
            initRetryJob?.cancel()
            initRetryJob = scope.launch {
                OzAdsManager.getInstance().awaitInitialization()
                if (OzAdsManager.getInstance().isAdInitialized() && adKey == key) {
                    val state = getAdState(key)
                    if (state == AdState.IDLE) {
                        OzLog.d(TAG, "SDK initialized, retrying queued load for key: $key")
                        loadAd()
                    }
                }
                initRetryJob = null
            }
            return
        }

        val currentState = getAdState(key)

        when (currentState) {
            AdState.IDLE, AdState.SHOWING -> {
                if (currentState == AdState.IDLE) OzLog.d(
                    TAG,
                    "Loading ad for key: $key (state: IDLE)"
                )
                else OzLog.d(TAG, "Ad is showing for key: $key, loading a new one")

                setAdState(key, AdState.LOADING)
                val ad = createAd(key)
                if (ad == null) {
                    onAdLoadFailed(key, "Failed to create ad object.")
                    return
                }
                OzEventLogger.logAdRequest(context, getAdUnitId(key), adFormat, key)
                onLoadAd(key, ad)
            }

            AdState.LOADING -> {
                OzLog.d(TAG, "Ad already loading for key: $key")
                return
            }

            AdState.LOADED -> {
                val existing: AdType? = OzAdsManager.getInstance().getAd(key)
                when {
                    existing == null -> {
                        // State is LOADED but nothing is in the store — stale state, reload.
                        OzLog.w(TAG, "Ad state is LOADED but store is empty for key: $key. Reloading.")
                        OzAdsManager.getInstance().logAdEvent("ad_expired", key, "state_loaded_store_empty")
                        OzEventLogger.logAdExpired(context, getAdUnitId(key), adFormat, "state_loaded_store_empty", key)
                        setAdState(key, AdState.LOADING)
                        val ad = createAd(key)
                        if (ad == null) {
                            onAdLoadFailed(key, "Failed to create ad object.")
                            return
                        }
                        OzEventLogger.logAdRequest(context, getAdUnitId(key), adFormat, key)
                        onLoadAd(key, ad)
                    }
                    !isValid(existing) -> {
                        OzLog.d(TAG, "Ad for key: $key is expired/invalid. Reloading.")
                        OzAdsManager.getInstance().logAdEvent("ad_expired", key, "at_load_time")
                        OzEventLogger.logAdExpired(context, getAdUnitId(key), adFormat, "at_load_time", key)
                        onDestroyAd(key)
                        setAdState(key, AdState.LOADING)
                        val ad = createAd(key)
                        if (ad == null) {
                            onAdLoadFailed(key, "Failed to create ad object.")
                            return
                        }
                        OzEventLogger.logAdRequest(context, getAdUnitId(key), adFormat, key)
                        onLoadAd(key, ad)
                    }
                    else -> OzLog.d(TAG, "Ad already loaded for key: $key")
                }
                return
            }
        }
    }

    fun loadThenShow(key: String) {
        if (this.adKey == null) {
            setPreloadKey(key)
        }
        loadAd()
        showAds(key)
    }

    open fun loadThenShow() {
        // Ensure adKey is not null before calling
        adKey?.let { loadThenShow(it) } ?: OzLog.e(TAG, "loadThenShow called but adKey is null")
    }

    /**
     * Implementation of showAds() from the interface
     * @param key Key to identify the ad to show. Must match the key managed by this instance.
     */
    open fun showAds(key: String) {
        if (this.adKey == null) {
            OzLog.d(TAG, "Ad key not set, setting it to '$key' from showAds.")
            setPreloadKey(key)
        } else if (this.adKey != key) {
            OzLog.e(TAG, "Cannot show ad for key '$key', this view is already managing key '${this.adKey}'.")
            return
        }

        if (!isAdEnable()) {
            OzLog.d(TAG, "Should not show ad, skipping showAds() for key: $key")
            setAdState(key, AdState.IDLE)
            return
        }

        if (!OzAdsManager.getInstance().isAdInitialized()) {
            OzLog.w(TAG, "OzAdsManager is not initialized. Cannot show ad for key: $key")
            onAdShowFailed(key, "OzAdsManager is not initialized")
            return
        }

        val currentState = getAdState(key)

        when (currentState) {
            AdState.IDLE -> {
                val ad: AdType? = OzAdsManager.getInstance().getAd(key)
                if (ad != null && isValid(ad)) {
                    OzLog.d(TAG, "Ad state is IDLE but valid ad found in store. Recovering to SHOWING.")
                    setAdState(key, AdState.SHOWING)
                    OzEventLogger.logAdShowCalled(context, getAdUnitId(key), adFormat, key)
                    onShowAds(key, ad)
                } else {
                    onAdShowFailed(key, "Ad is not loaded, or not loading.")
                }
            }

            AdState.LOADING -> {
                OzLog.d(TAG, "Ad loading for key: $key, setting pending show")
                // Store the logic to run once loaded
                OzAdsManager.getInstance().setPendingShow(key) {
                    showAds(key)
                }
                // Close race window: if the ad completed loading while setting the pending show, trigger it.
                if (getAdState(key) == AdState.LOADED) {
                    OzAdsManager.getInstance().executePendingShow(key)
                }
            }

            AdState.SHOWING -> {
                OzLog.d(TAG, "Ad already showing for key: $key")
                return
            }

            AdState.LOADED -> {
                OzLog.d(TAG, "Showing ad for key: $key (state: LOADED)")
                val ad: AdType? = OzAdsManager.getInstance().getAd(key)
                if (ad != null) {
                    if (isValid(ad)) {
                        setAdState(key, AdState.SHOWING)
                        OzEventLogger.logAdShowCalled(context, getAdUnitId(key), adFormat, key)
                        onShowAds(key, ad)
                    } else {
                        OzLog.w(TAG, "Ad found in store is expired/invalid for key: $key")
                        OzAdsManager.getInstance().logAdEvent("ad_expired", key, "at_show_time")
                        OzEventLogger.logAdExpired(context, getAdUnitId(key), adFormat, "at_show_time", key)
                        onDestroyAd(key)
                        setAdState(key, AdState.IDLE)
                        onAdShowFailed(key, "Ad expired or invalid")
                    }
                } else {
                    onAdShowFailed(key, "Ad object not found in store.")
                }
            }
        }
    }

    /**
     * Hide ads
     * Specific implementations will override this method
     */
    protected abstract fun hideAds()

    /**
     * Create an ad object.
     * @param key Key to identify the ad.
     * @return The created ad object or null on failure.
     */
    protected abstract fun createAd(key: String): AdType?

    /**
     * Abstract method for specific implementations to load an ad from mediation
     * @param key Key to identify the ad to load
     * @param ad The ad object to be loaded
     */
    protected abstract fun onLoadAd(key: String, ad: AdType)

    /**
     * Abstract method for specific implementations to show an ad
     * @param key Key to identify the ad to show
     * @param ad The ad object to be shown
     */
    protected abstract fun onShowAds(key: String, ad: AdType)

    /**
     * Called when an ad loads successfully
     * Implementations should call this method after a successful ad load
     * @param key Key of the successfully loaded ad
     * @param ad The loaded ad object
     */
    protected open fun onAdLoaded(key: String, ad: AdType) {
        val currentState = getAdState(key)
        if (currentState == AdState.LOADING) {
            // Destroy previous ad if any, to prevent memory leaks
            val oldAd: AdType? = OzAdsManager.getInstance().getAd(key)
            oldAd?.let { destroyAd(it) }

            OzAdsManager.getInstance().setAd(key, ad as Any)
            setAdState(key, AdState.LOADED)
            OzLog.d(TAG, "Ad loaded successfully for key: $key")
            OzEventLogger.logAdLoadSuccess(context, getAdUnitId(key), adFormat, key)

            // Check if there's a pending show globally for this key
            OzAdsManager.getInstance().executePendingShow(key)
        } else {
            // Loaded ad is not expected, destroy it
            destroyAd(ad)
        }
    }

    /**
     * Called when an ad fails to load
     * Implementations should call this method after a failed ad load
     * @param key Key of the ad that failed to load
     * @param message Failure message
     * @param errorCode Error code from ad network
     */
    protected open fun onAdLoadFailed(key: String, message: String? = null, errorCode: Int? = null) {
        OzLog.e(TAG, "Ad load failed for key: $key. Reason: ${message ?: "Unknown"}")
        setAdState(key, AdState.IDLE)

        // Clear pending show since load failed
        OzAdsManager.getInstance().clearPendingShow(key)
        OzAdsManager.getInstance().logAdEvent("ad_load_failed", key, message)
        OzEventLogger.logAdLoadFailed(context, getAdUnitId(key), adFormat, errorCode, message, key)
    }

    /**
     * Called when an ad is shown successfully
     * Implementations should call this method after a successful ad show
     * @param key Key of the successfully shown ad
     */
    protected open fun onAdShown(key: String) {
        OzLog.d(TAG, "Ad shown successfully for key: $key")
        // State was already set to SHOWING in showAds()
        OzAdsManager.getInstance().logAdEvent("ad_shown", key)
    }

    /**
     * Called when an ad is dismissed/closed
     * Implementations should call this method after an ad is dismissed
     * @param key Key of the dismissed ad
     */
    protected open fun onAdDismissed(key: String) {
        if (adKey != key) return

        OzLog.d(TAG, "Ad dismissed for key: $key, cleaning up")
        OzEventLogger.logAdDismissed(context, getAdUnitId(key), adFormat, key)

        // Destroy ad to prevent memory leak
        onDestroyAd(key)

        // Reset state
        setAdState(key, AdState.IDLE)
    }

    /**
     * Called when an ad fails to show
     * Implementations should call this method after a failed ad show
     * @param key Key of the ad that failed to show
     * @param message Failure message
     * @param errorCode Error code from ad network
     */
    protected open fun onAdShowFailed(key: String, message: String? = null, errorCode: Int? = null) {
        if (adKey != key) return
        OzLog.e(TAG, "Ad show failed for key: $key. Reason: ${message ?: "Unknown"}")
        onDestroyAd(key)
        setAdState(key, AdState.IDLE)
        OzAdsManager.getInstance().clearPendingShow(key)
        OzAdsManager.getInstance().logAdEvent("ad_show_failed", key, message)
        OzEventLogger.logAdShowFailed(context, getAdUnitId(key), adFormat, errorCode, message, key)
    }

    /**
     * Called when an ad show is blocked (e.g. by cooldown or another ad showing)
     * Keeps the state as LOADED and does not destroy the ad.
     */
    protected open fun onAdShowBlocked(key: String, reason: String? = null) {
        if (adKey != key) return
        OzLog.w(TAG, "Ad show blocked for key: $key. Reason: ${reason ?: "Unknown"}")
        OzAdsManager.getInstance().logAdEvent("ad_show_blocked", key, reason)
        OzEventLogger.logAdSkip(context, getAdUnitId(key), adFormat, reason ?: "show_blocked", key)
    }

    /**
     * Check if the loaded ad is valid/not expired.
     */
    protected open fun isValid(ad: AdType): Boolean {
        return true
    }

    /**
     * Called when an ad is clicked
     * @param key Key of the clicked ad
     */
    protected open fun onAdClicked(key: String) {
        OzLog.d(TAG, "Ad clicked for key: $key")
        OzEventLogger.logAdClickedCustom(context, getAdUnitId(key), adFormat, key)
    }

    /**
     * Set the state of the ad for a given key
     * @param key Key to identify the ad
     * @param state New state
     */
    private fun setAdState(key: String, state: AdState) {
        OzAdsManager.getInstance().setAdState(key, state)
        OzLog.d(TAG, "Ad state changed for key: $key -> $state")
    }

    /**
     * Destroy ad for a specific key
     * @param key Key of the ad to destroy
     */
    @Suppress("UNCHECKED_CAST")
    protected fun onDestroyAd(key: String) {
        (OzAdsManager.getInstance().removeAd(key) as? AdType)?.let { ad ->
            destroyAd(ad)
        }
    }

    /**
     * Abstract method to destroy a specific ad object.
     * @param ad The ad object to destroy
     */
    protected abstract fun destroyAd(ad: AdType)

    /**
     * Destroy the ad managed by this view instance and clean up resources
     */
    open fun destroy() {
        initRetryJob?.cancel()
        initRetryJob = null
        adKey?.let { key ->
            OzLog.d(TAG, "Destroying ad for view instance, key: $key")
            onDestroyAd(key)
            setAdState(key, AdState.IDLE)
            OzAdsManager.getInstance().clearPendingShow(key) // remove pending show to avoid crash when show on destroyed view
        }
        adKey = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            OzAdsManager.getInstance().enableAd.collect { shouldShow ->
                if (!shouldShow && OzAdsManager.getInstance().isAdInitialized()) {
                    hideAds()
                }
            }
        }
    }
}