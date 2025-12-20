package com.oz.android.ads.oz_ads.ads_component.ads_inline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.IOzAds

/**
 * Abstract class để quản lý inline ads (banner, native) hiển thị cùng với content
 * InlineAds là một ViewGroup có thể được thêm vào layout như một child view
 * 
 * Hỗ trợ nhiều ad network (AdMob, Max, Meta...) nhưng hiện tại tối ưu cho AdMob
 * 
 * InlineAds chỉ hỗ trợ format: BANNER, NATIVE
 * Refresh time chỉ có trong inline format
 * 
 * Các implementation cụ thể sẽ extend class này và implement các abstract methods
 */
abstract class InlineAds @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), IOzAds {

    companion object {
        private const val TAG = "InlineAds"
        
        // Default refresh times (in milliseconds)
        private const val DEFAULT_REFRESH_TIME = 30_000L // 30 seconds
        private const val DEFAULT_MAX_REFRESH_TIME = 300_000L // 5 minutes
    }

    // Refresh time management (chỉ có trong inline format)
    private var refreshTime: Long = DEFAULT_REFRESH_TIME
    private var maxRefreshTime: Long = DEFAULT_MAX_REFRESH_TIME
    private var lastRefreshTime: Long = 0
    private var totalRefreshTime: Long = 0

    // Auto refresh handler
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // Ad state
    private var isAdVisible = false

    // OzAds properties
    private var adsFormat: AdsFormat? = null
    private var preloadKey: String? = null
    private var shouldShow: Boolean = true

    init {
        // Inline ads chỉ hỗ trợ BANNER và NATIVE format
    }

    /**
     * Set ads format (từ OzAds pattern)
     * Inline ads chỉ hỗ trợ BANNER và NATIVE
     */
    fun setAdsFormat(format: AdsFormat) {
        if (format != AdsFormat.BANNER && format != AdsFormat.NATIVE) {
            throw IllegalArgumentException(
                "Format $format is not valid for InlineAds. " +
                "Valid formats: BANNER, NATIVE"
            )
        }
        adsFormat = format
        Log.d(TAG, "Ads format set to: $format")
    }

    /**
     * Get ads format hiện tại
     */
    fun getAdsFormat(): AdsFormat? = adsFormat

    /**
     * Implementation của shouldShowAd() từ IOzAds
     */
    override fun shouldShowAd(): Boolean {
        return shouldShow && isAdVisible
    }

    /**
     * Set trạng thái có nên hiển thị ad hay không
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
     * Implementation của setPreloadKey() từ IOzAds
     */
    override fun setPreloadKey(key: String) {
        preloadKey = key
        Log.d(TAG, "Preload key set to: $key")
    }

    /**
     * Get preload key hiện tại
     */
    fun getPreloadKey(): String? = preloadKey

    /**
     * Implementation của loadAd() từ IOzAds
     */
    override fun loadAd() {
        if (adsFormat == null) {
            Log.w(TAG, "Ads format not set. Call setAdsFormat() first")
            return
        }
        onLoadAd()
    }

    /**
     * Implementation của showAds() từ IOzAds
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
     */
    protected abstract fun hideAds()

    /**
     * Set thời gian refresh ad (milliseconds)
     * @param timeInMillis Thời gian refresh tính bằng milliseconds
     */
    fun setRefreshTime(timeInMillis: Long) {
        if (timeInMillis <= 0) {
            Log.w(TAG, "Refresh time must be greater than 0")
            return
        }
        refreshTime = timeInMillis
        restartAutoRefresh()
    }

    /**
     * Set thời gian tối đa để refresh ad (milliseconds)
     * Sau thời gian này, ad sẽ không tự động refresh nữa
     * @param timeInMillis Thời gian tối đa tính bằng milliseconds
     */
    fun setMaxRefreshTime(timeInMillis: Long) {
        if (timeInMillis <= 0) {
            Log.w(TAG, "Max refresh time must be greater than 0")
            return
        }
        maxRefreshTime = timeInMillis
    }

    /**
     * Get thời gian refresh hiện tại
     * @return Thời gian refresh tính bằng milliseconds
     */
    fun getRefreshTime(): Long = refreshTime

    /**
     * Get thời gian tối đa refresh
     * @return Thời gian tối đa tính bằng milliseconds
     */
    fun getMaxRefreshTime(): Long = maxRefreshTime

    /**
     * Destroy ad và cleanup resources
     */
    fun destroy() {
        cancelAutoRefresh()
        isAdVisible = false
        destroyAd()
    }

    /**
     * Destroy ad - Abstract method để các implementation cụ thể implement
     */
    protected abstract fun destroyAd()

    /**
     * Abstract method để các implementation cụ thể load ad
     */
    protected abstract fun onLoadAd()

    /**
     * Abstract method để các implementation cụ thể show ad
     */
    protected abstract fun onShowAds()

    /**
     * Kiểm tra xem ad đã được load chưa
     * Abstract method để các implementation cụ thể implement
     */
    protected abstract fun isAdLoaded(): Boolean

    /**
     * Called khi ad được load thành công
     * Các implementation nên gọi method này sau khi load ad thành công
     */
    protected fun onAdLoaded() {
        lastRefreshTime = System.currentTimeMillis()
        totalRefreshTime = 0
        
        if (shouldShowAd() && isAdVisible) {
            showAds()
        }
        
        // Bắt đầu auto refresh nếu chưa vượt quá max refresh time
        if (totalRefreshTime < maxRefreshTime) {
            scheduleNextRefresh()
        }
    }

    /**
     * Called khi ad load thất bại
     * Các implementation nên gọi method này sau khi load ad thất bại
     */
    protected fun onAdLoadFailed() {
        // Retry sau một khoảng thời gian
        if (totalRefreshTime < maxRefreshTime) {
            scheduleNextRefresh()
        }
    }

    /**
     * Schedule refresh ad sau một khoảng thời gian
     */
    private fun scheduleNextRefresh() {
        cancelAutoRefresh()
        
        refreshRunnable = Runnable {
            if (totalRefreshTime < maxRefreshTime && isAdVisible) {
                refreshAd()
            }
        }
        
        refreshHandler.postDelayed(refreshRunnable!!, refreshTime)
    }

    /**
     * Refresh ad (load lại ad mới)
     * Chỉ có trong inline format
     */
    fun refreshAd() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastRefreshTime
        totalRefreshTime += elapsedTime
        lastRefreshTime = currentTime

        if (totalRefreshTime >= maxRefreshTime) {
            Log.d(TAG, "Max refresh time reached, stopping auto refresh")
            cancelAutoRefresh()
            return
        }

        Log.d(TAG, "Refreshing ad... (total time: ${totalRefreshTime}ms, max: ${maxRefreshTime}ms)")
        destroyAd()
        loadAd()
    }

    /**
     * Restart auto refresh mechanism
     */
    private fun restartAutoRefresh() {
        if (isAdLoaded() && isAdVisible) {
            scheduleNextRefresh()
        }
    }

    /**
     * Cancel auto refresh
     */
    private fun cancelAutoRefresh() {
        refreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
            refreshRunnable = null
        }
    }

    /**
     * Pause ad (gọi trong onPause của Activity/Fragment)
     */
    fun pause() {
        isAdVisible = false
        cancelAutoRefresh()
        onPauseAd()
    }

    /**
     * Resume ad (gọi trong onResume của Activity/Fragment)
     */
    fun resume() {
        isAdVisible = true
        if (shouldShowAd()) {
            if (isAdLoaded()) {
                showAds()
            } else {
                loadAd()
            }
            if (totalRefreshTime < maxRefreshTime) {
                scheduleNextRefresh()
            }
        }
        onResumeAd()
    }

    /**
     * Override onAttachedToWindow để tự động load ad khi view được attach
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAdVisible = true
        if (shouldShowAd() && !isAdLoaded()) {
            loadAd()
        }
    }

    /**
     * Override onDetachedFromWindow để cleanup khi view được detach
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }

    /**
     * Override onVisibilityChanged để handle visibility changes
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        isAdVisible = visibility == VISIBLE
        
        if (isAdVisible && shouldShowAd()) {
            if (isAdLoaded()) {
                showAds()
            } else {
                loadAd()
            }
            if (totalRefreshTime < maxRefreshTime) {
                scheduleNextRefresh()
            }
        } else {
            cancelAutoRefresh()
        }
    }

    /**
     * Abstract method để các implementation xử lý pause ad
     */
    protected abstract fun onPauseAd()

    /**
     * Abstract method để các implementation xử lý resume ad
     */
    protected abstract fun onResumeAd()

    /**
     * Default layout params cho InlineAds
     */
    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return LayoutParams(p)
    }

    /**
     * Override onMeasure để measure child views
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxHeight = 0
        var maxWidth = 0

        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                maxWidth = maxOf(maxWidth, child.measuredWidth)
                maxHeight = maxOf(maxHeight, child.measuredHeight)
            }
        }

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        )
    }

    /**
     * Override onLayout để layout child views
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
            }
        }
    }
}

