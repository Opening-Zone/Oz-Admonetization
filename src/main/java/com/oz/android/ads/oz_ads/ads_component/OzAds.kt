package com.oz.android.ads.oz_ads.ads_component

import android.util.Log

/**
 * Abstract class chung cho tất cả các loại OzAds
 * Implement interface IOzAds và cung cấp các tính năng chung
 * 
 * Các implementation cụ thể (như AdmobInlineAds, AdmobOverlayAds) sẽ extend class này
 */
abstract class OzAds : IOzAds {

    companion object {
        private const val TAG = "OzAds"
    }

    // Ads format
    protected var adsFormat: AdsFormat? = null
        private set

    // Preload key
    protected var preloadKey: String? = null
        private set

    // Should show ad flag
    protected var shouldShow: Boolean = true
        private set

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
        } else {
            if (isAdLoaded()) {
                showAds()
            }
        }
    }

    /**
     * Implementation của setPreloadKey() từ interface
     * @param key Key để identify ad cần preload
     */
    override fun setPreloadKey(key: String) {
        preloadKey = key
        Log.d(TAG, "Preload key set to: $key")
    }

    /**
     * Get preload key hiện tại
     * @return Preload key, null nếu chưa được set
     */
    fun getPreloadKey(): String? = preloadKey

    /**
     * Implementation của loadAd() từ interface
     * Các implementation cụ thể sẽ override method này
     */
    override fun loadAd() {
        if (adsFormat == null) {
            Log.w(TAG, "Ads format not set. Call setAdsFormat() first")
            return
        }
        onLoadAd()
    }

    /**
     * Implementation của showAds() từ interface
     * Các implementation cụ thể sẽ override method này
     */
    override fun showAds() {
        if (!shouldShowAd()) {
            Log.d(TAG, "Should not show ad, skipping showAds()")
            return
        }
        if (!isAdLoaded()) {
            Log.w(TAG, "Ad not loaded yet. Call loadAd() first")
            return
        }
        onShowAds()
    }

    /**
     * Hide ads
     * Các implementation cụ thể sẽ override method này
     */
    protected abstract fun hideAds()

    /**
     * Kiểm tra xem ad đã được load chưa
     * Các implementation cụ thể sẽ override method này
     * @return true nếu ad đã load, false nếu chưa
     */
    protected abstract fun isAdLoaded(): Boolean

    /**
     * Abstract method để các implementation cụ thể load ad
     */
    protected abstract fun onLoadAd()

    /**
     * Abstract method để các implementation cụ thể show ad
     */
    protected abstract fun onShowAds()

    /**
     * Destroy ad và cleanup resources
     * Các implementation cụ thể sẽ override method này
     */
    abstract fun destroy()
}

