package com.oz.android.ads.oz_ads.ads_component

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract class chung cho tất cả các loại OzAds
 * Implement interface IOzAds và cung cấp các tính năng chung
 * 
 * Quản lý preload ads bằng key (key đại diện cho placement, không phải ad id)
 * Ads có 4 state: IDLE, LOADING, LOADED, SHOWING
 * 
 * Các implementation cụ thể (như AdmobInlineAds, AdmobOverlayAds) sẽ extend class này
 */
abstract class OzAds<AdType> : IOzAds {

    companion object {
        private const val TAG = "OzAds"
    }

    // Ads format
    protected var adsFormat: AdsFormat? = null
        private set

    // Should show ad flag
    protected var shouldShow: Boolean = true
        private set

    // Preloaded ads state management (key -> state)
    private val adStates = ConcurrentHashMap<String, AdState>()
    
    // Ad store (key -> ad object)
    protected val adStore = ConcurrentHashMap<String, AdType>()

    // Pending show callbacks (key -> callback)
    private val pendingShows = ConcurrentHashMap<String, () -> Unit>()

    // Current showing key
    private var currentShowingKey: String? = null

    /**
     * Set ads format
     * @param format AdsFormat để định nghĩa loại ad
     * @throws IllegalArgumentException nếu format không hợp lệ cho loại ads này
     */
    fun setAdsFormat(format: AdsFormat) {
        // Validate format dựa trên loại ads (inline hoặc overlay)
        if (!isValidFormat(format)) {
            throw IllegalArgumentException(
                "Format $format is not valid for ${this::class.simpleName}. " +
                        "Valid formats: ${getValidFormats().joinToString()}"
            )
        }
        
        adsFormat = format
        Log.d(TAG, "Ads format set to: $format")
    }

    /**
     * Get ads format hiện tại
     * @return AdsFormat hiện tại, null nếu chưa được set
     */
    fun getAdsFormat(): AdsFormat? = adsFormat

    /**
     * Kiểm tra xem format có hợp lệ cho loại ads này không
     * Các implementation cụ thể sẽ override method này
     * @param format Format cần kiểm tra
     * @return true nếu hợp lệ, false nếu không
     */
    protected abstract fun isValidFormat(format: AdsFormat): Boolean

    /**
     * Get danh sách các format hợp lệ cho loại ads này
     * Các implementation cụ thể sẽ override method này
     * @return List các format hợp lệ
     */
    protected abstract fun getValidFormats(): List<AdsFormat>

    /**
     * Implementation của shouldShowAd() từ interface
     * @return true nếu nên hiển thị, false nếu không
     */
    override fun shouldShowAd(): Boolean {
        return shouldShow
    }

    /**
     * Set trạng thái có nên hiển thị ad hay không
     * @param shouldShow true để hiển thị, false để ẩn
     */
    fun setShouldShowAd(shouldShow: Boolean) {
        this.shouldShow = shouldShow
        if (!shouldShow) {
            hideAds()
        }
        // Note: Showing ads when shouldShow becomes true should be handled by implementation
        // as it requires a key parameter
    }

    /**
     * Implementation của setPreloadKey() từ interface
     * Preload ad với key này
     * @param key Key để identify ad cần preload (đại diện cho placement)
     */
    override fun setPreloadKey(key: String) {
        if (adsFormat == null) {
            Log.w(TAG, "Ads format not set. Call setAdsFormat() first")
            return
        }
        
        // Set state to IDLE nếu chưa có
        adStates.putIfAbsent(key, AdState.IDLE)
        
        // Preload ad
        loadAd(key)
        Log.d(TAG, "Preload key set to: $key")
    }

    /**
     * Get state của ad với key
     * @param key Key để identify ad
     * @return AdState hiện tại, IDLE nếu chưa có
     */
    fun getAdState(key: String): AdState {
        return adStates.getOrDefault(key, AdState.IDLE)
    }

    /**
     * Implementation của loadAd() từ interface
     * Load ad với preload key đã set (nếu có)
     */
    override fun loadAd() {
        // Nếu có preload key, load với key đó
        // Nếu không, implementation cụ thể sẽ handle
        Log.d(TAG, "loadAd() called without key")
    }

    /**
     * Load ad với key cụ thể
     * @param key Key để identify ad cần load
     */
    fun loadAd(key: String) {
        if (adsFormat == null) {
            Log.w(TAG, "Ads format not set. Call setAdsFormat() first")
            setAdState(key, AdState.IDLE)
            return
        }

        val currentState = getAdState(key)
        
        when (currentState) {
            AdState.IDLE, AdState.SHOWING -> {
                if (currentState == AdState.IDLE) Log.d(TAG, "Loading ad for key: $key (state: IDLE)")
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

    /**
     * Implementation của showAds() từ interface
     * @param key Key để identify ad cần show (đại diện cho placement)
     */
    override fun showAds(key: String) {
        if (!shouldShowAd()) {
            Log.d(TAG, "Should not show ad, skipping showAds() for key: $key")
            setAdState(key, AdState.IDLE)
            return
        }

        val currentState = getAdState(key)
        
        when (currentState) {
            AdState.IDLE -> {
                Log.d(TAG, "Showing ad for key: $key (state: IDLE -> loadThenShow)")
                setAdState(key, AdState.LOADING)
                
                pendingShows[key] = {
                    val ad = adStore[key]
                    if (ad != null) {
                        setAdState(key, AdState.SHOWING)
                        currentShowingKey = key
                        onShowAds(key, ad)
                    } else {
                        onAdShowFailed(key, "Ad disappeared after loading.")
                    }
                }

                val ad = createAd(key)
                if (ad == null) {
                    onAdLoadFailed(key, "Failed to create ad object for loadThenShow.")
                    return
                }
                onLoadAd(key, ad)
            }
            AdState.LOADING -> {
                Log.d(TAG, "Ad loading for key: $key, setting pending show")
                pendingShows[key] = {
                    val ad = adStore[key]
                    if (ad != null) {
                        setAdState(key, AdState.SHOWING)
                        currentShowingKey = key
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
                val ad = adStore[key]
                if (ad != null) {
                    setAdState(key, AdState.SHOWING)
                    currentShowingKey = key
                    onShowAds(key, ad)
                } else {
                    onAdShowFailed(key, "Ad object not found in store.")
                }
            }
        }
    }

    /**
     * Hide ads
     * Các implementation cụ thể sẽ override method này
     */
    protected abstract fun hideAds()

    /**
     * Create an ad object.
     * @param key Key to identify the ad.
     * @return The created ad object or null on failure.
     */
    protected abstract fun createAd(key: String): AdType?

    /**
     * Abstract method để các implementation cụ thể load ad từ mediation
     * @param key Key để identify ad cần load
     * @param ad The ad object to be loaded
     */
    protected abstract fun onLoadAd(key: String, ad: AdType)

    /**
     * Abstract method để các implementation cụ thể show ad
     * @param key Key để identify ad cần show
     * @param ad The ad object to be shown
     */
    protected abstract fun onShowAds(key: String, ad: AdType)

    /**
     * Called khi ad load thành công
     * Các implementation nên gọi method này sau khi load ad thành công
     * @param key Key của ad đã load thành công
     * @param ad The loaded ad object
     */
    protected fun onAdLoaded(key: String, ad: AdType) {
        val currentState = getAdState(key)
        if (currentState == AdState.LOADING) {
            // Destroy previous ad if any, to prevent memory leaks
            adStore[key]?.let { oldAd ->
                destroyAd(oldAd)
            }
            adStore[key] = ad
            setAdState(key, AdState.LOADED)
            Log.d(TAG, "Ad loaded successfully for key: $key")
            
            // Check if there's a pending show
            pendingShows.remove(key)?.invoke()
        } else {
            // Loaded ad is not expected, destroy it
            destroyAd(ad)
        }
    }

    /**
     * Called khi ad load thất bại
     * Các implementation nên gọi method này sau khi load ad thất bại
     * @param key Key của ad đã load thất bại
     * @param message Failure message
     */
    protected fun onAdLoadFailed(key: String, message: String? = null) {
        Log.e(TAG, "Ad load failed for key: $key. Reason: ${message ?: "Unknown"}")
        setAdState(key, AdState.IDLE)
        pendingShows.remove(key)
    }

    /**
     * Called khi ad show thành công
     * Các implementation nên gọi method này sau khi show ad thành công
     * @param key Key của ad đã show thành công
     */
    protected fun onAdShown(key: String) {
        Log.d(TAG, "Ad shown successfully for key: $key")
        // State đã được set thành SHOWING trong showAds()
    }

    /**
     * Called khi ad dismissed/closed
     * Các implementation nên gọi method này sau khi ad bị dismiss
     * @param key Key của ad đã bị dismiss
     */
    protected fun onAdDismissed(key: String) {
        Log.d(TAG, "Ad dismissed for key: $key, cleaning up")
        
        // Destroy ad to prevent memory leak
        onDestroyAd(key)
        
        // Reset state
        setAdState(key, AdState.IDLE)
        
        // Clear current showing key if it matches
        if (currentShowingKey == key) {
            currentShowingKey = null
        }
    }

    /**
     * Called khi ad show thất bại
     * Các implementation nên gọi method này sau khi show ad thất bại
     * @param key Key của ad đã show thất bại
     * @param message Failure message
     */
    protected fun onAdShowFailed(key: String, message: String? = null) {
        Log.e(TAG, "Ad show failed for key: $key. Reason: ${message ?: "Unknown"}")
        setAdState(key, AdState.IDLE)
        
        // Clear current showing key if it matches
        if (currentShowingKey == key) {
            currentShowingKey = null
        }
    }

    /**
     * Set state của ad với key
     * @param key Key để identify ad
     * @param state State mới
     */
    private fun setAdState(key: String, state: AdState) {
        adStates[key] = state
        Log.d(TAG, "Ad state changed for key: $key -> $state")
    }

    /**
     * Destroy ad với key cụ thể
     * @param key Key của ad cần destroy
     */
    protected fun onDestroyAd(key: String) {
        adStore.remove(key)?.let { ad ->
            destroyAd(ad)
        }
    }

    /**
     * Abstract method to destroy a specific ad object.
     * @param ad The ad object to destroy
     */
    protected abstract fun destroyAd(ad: AdType)

    /**
     * Destroy tất cả ads và cleanup resources
     */
    fun destroy() {
        Log.d(TAG, "Destroying all ads")
        
        // Destroy all ads in the store
        adStore.values.forEach { ad ->
            destroyAd(ad)
        }
        
        // Clear all states and pending shows
        adStore.clear()
        adStates.clear()
        pendingShows.clear()
        currentShowingKey = null
    }

    /**
     * Get current showing key
     * @return Key đang được show, null nếu không có
     */
    fun getCurrentShowingKey(): String? = currentShowingKey
}

