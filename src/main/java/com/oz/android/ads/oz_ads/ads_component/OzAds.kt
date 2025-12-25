package com.oz.android.ads.oz_ads.ads_component

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.oz.android.wrapper.OzAdListener
import com.oz.android.wrapper.OzAdsManager
import kotlinx.coroutines.launch

/**
 * Abstract class chung cho tất cả các loại OzAds
 * Implement interface IOzAds và cung cấp các tính năng chung
 *
 * Quản lý preload ads bằng key (key đại diện cho placement, không phải ad id)
 * Ads có 4 state: IDLE, LOADING, LOADED, SHOWING
 *
 * Các implementation cụ thể (như AdmobInlineAds, AdmobOverlayAds) sẽ extend class này
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

    // Single pending show callback instead of a Map
    private var pendingShowRunnable: (() -> Unit)? = null

    //listener
    var listener: OzAdListener<AdType>? = null

    /**
     * Implementation of isAdEnable() from the interface
     * @return true if ad should be shown or load, false otherwise
     */
    fun isAdEnable(): Boolean {
        Log.d(TAG, "isAdEnable: " + OzAdsManager.getInstance().enableAd.value)
        return OzAdsManager.getInstance().enableAd.value
    }

    /**
     * Implementation of setPreloadKey() from the interface.
     * Sets the single ad key for this view instance and pre-registers its state.
     * @param key The ad key to be managed by this instance.
     */
    fun setPreloadKey(key: String) {
        this.adKey = key
        Log.d(TAG, "Ad key set to: $key")
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
            Log.w(TAG, "Ad key not set. Call setPreloadKey() first.")
            return
        }

        if (!isAdEnable()) {
            Log.d(TAG, "Should not show ad, skipping showAds() for key: $key")
            return
        }

        val currentState = getAdState(key)

        when (currentState) {
            AdState.IDLE, AdState.SHOWING -> {
                if (currentState == AdState.IDLE) Log.d(
                    TAG,
                    "Loading ad for key: $key (state: IDLE)"
                )
                else Log.d(TAG, "Ad is showing for key: $key, loading a new one")

                setAdState(key, AdState.LOADING)
                val ad = createAd(key)
                if (ad == null) {
                    onAdLoadFailed(key, "Failed to create ad object.")
                    return
                }
                onLoadAd(key, ad)
            }

            AdState.LOADING -> {
                Log.d(TAG, "Ad already loading for key: $key")
                return
            }

            AdState.LOADED -> {
                Log.d(TAG, "Ad already loaded for key: $key")
                return
            }
        }
    }

    fun loadThenShow(key: String) {
        loadAd()
        showAds(key)
    }

    open fun loadThenShow() {
        // Ensure adKey is not null before calling
        adKey?.let { loadThenShow(it) } ?: Log.e(TAG, "loadThenShow called but adKey is null")
    }

    /**
     * Implementation of showAds() from the interface
     * @param key Key to identify the ad to show. Must match the key managed by this instance.
     */
    open fun showAds(key: String) {
        if (this.adKey == null) {
            Log.d(TAG, "Ad key not set, setting it to '$key' from showAds.")
            setPreloadKey(key)
        } else if (this.adKey != key) {
            Log.e(TAG, "Cannot show ad for key '$key', this view is already managing key '${this.adKey}'.")
            return
        }

        if (!isAdEnable()) {
            Log.d(TAG, "Should not show ad, skipping showAds() for key: $key")
            setAdState(key, AdState.IDLE)
            return
        }

        val currentState = getAdState(key)

        when (currentState) {
            AdState.IDLE -> {
                onAdShowFailed(key, "Ad is not loaded, or not loading.")
            }

            AdState.LOADING -> {
                Log.d(TAG, "Ad loading for key: $key, setting pending show")
                // Store the logic to run once loaded
                pendingShowRunnable = {
                    val ad: AdType? = OzAdsManager.getInstance().getAd(key)
                    if (ad != null) {
                        setAdState(key, AdState.SHOWING)
                        onShowAds(key, ad)
                    } else {
                        onAdShowFailed(key, "Ad disappeared after loading.")
                    }
                }
            }

            AdState.SHOWING -> {
                Log.d(TAG, "Ad already showing for key: $key")
                return
            }

            AdState.LOADED -> {
                Log.d(TAG, "Showing ad for key: $key (state: LOADED)")
                val ad: AdType? = OzAdsManager.getInstance().getAd(key)
                if (ad != null) {
                    setAdState(key, AdState.SHOWING)
                    onShowAds(key, ad)
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
            Log.d(TAG, "Ad loaded successfully for key: $key")

            // Check if there's a pending show for this instance
            val runnable = pendingShowRunnable
            if (runnable != null) {
                pendingShowRunnable = null // Clear it
                runnable.invoke()
            }
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
     */
    protected open fun onAdLoadFailed(key: String, message: String? = null) {
        Log.e(TAG, "Ad load failed for key: $key. Reason: ${message ?: "Unknown"}")
        setAdState(key, AdState.IDLE)

        // Clear pending show since load failed
        pendingShowRunnable = null
    }

    /**
     * Called when an ad is shown successfully
     * Implementations should call this method after a successful ad show
     * @param key Key of the successfully shown ad
     */
    protected open fun onAdShown(key: String) {
        Log.d(TAG, "Ad shown successfully for key: $key")
        // State was already set to SHOWING in showAds()
    }

    /**
     * Called when an ad is dismissed/closed
     * Implementations should call this method after an ad is dismissed
     * @param key Key of the dismissed ad
     */
    protected open fun onAdDismissed(key: String) {
        if (adKey != key) return

        Log.d(TAG, "Ad dismissed for key: $key, cleaning up")

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
     */
    protected open fun onAdShowFailed(key: String, message: String? = null) {
        if (adKey != key) return

        Log.e(TAG, "Ad show failed for key: $key. Reason: ${message ?: "Unknown"}")
        setAdState(key, AdState.IDLE)
    }

    /**
     * Called when an ad is clicked
     * @param key Key of the clicked ad
     */
    protected open fun onAdClicked(key: String) {
        Log.d(TAG, "Ad clicked for key: $key")
    }

    /**
     * Set the state of the ad for a given key
     * @param key Key to identify the ad
     * @param state New state
     */
    private fun setAdState(key: String, state: AdState) {
        OzAdsManager.getInstance().setAdState(key, state)
        Log.d(TAG, "Ad state changed for key: $key -> $state")
    }

    /**
     * Destroy ad for a specific key
     * @param key Key of the ad to destroy
     */
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
        adKey?.let { key ->
            Log.d(TAG, "Destroying ad for view instance, key: $key")
            onDestroyAd(key)
            setAdState(key, AdState.IDLE)
        }
        adKey = null

        // Clear pending show
        pendingShowRunnable = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            OzAdsManager.getInstance().enableAd.collect { shouldShow ->
                if (!shouldShow) {
                    hideAds()
                }
            }
        }
    }
}