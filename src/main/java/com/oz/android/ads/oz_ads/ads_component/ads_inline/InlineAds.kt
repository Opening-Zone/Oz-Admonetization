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
import com.oz.android.ads.oz_ads.ads_component.AdState
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.IOzAds
import com.oz.android.ads.oz_ads.ads_component.OzAds

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
abstract class InlineAds<AdType> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OzAds<AdType>() {

    companion object {
        private const val TAG = "InlineAds"
        
        // Default refresh times (in milliseconds)
        private const val DEFAULT_REFRESH_TIME = 30_000L // 30 seconds
        private const val DEFAULT_MAX_REFRESH_TIME = 300_000L // 5 minutes
    }

    // Refresh time management
    private var refreshTime: Long = DEFAULT_REFRESH_TIME
    private var maxRefreshTime: Long = DEFAULT_MAX_REFRESH_TIME
    private var lastRefreshTime: Long = 0
    private var totalRefreshTime: Long = 0

    // Auto refresh handler
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // Ad visibility state
    private var isAdVisible = false

    // Track preload key for refresh and visibility logic
    private var preloadKey: String? = null

    override fun isValidFormat(format: AdsFormat): Boolean {
        return format == AdsFormat.BANNER || format == AdsFormat.NATIVE
    }

    override fun getValidFormats(): List<AdsFormat> {
        return listOf(AdsFormat.BANNER, AdsFormat.NATIVE)
    }

    override fun shouldShowAd(): Boolean {
        return super.shouldShowAd() && isAdVisible
    }
    
    override fun setPreloadKey(key: String) {
        this.preloadKey = key
        super.setPreloadKey(key)
    }

    override fun loadAd() {
        preloadKey?.let { key ->
            super.loadAd(key)
        } ?: run {
            Log.w(TAG, "No preload key set. Call setPreloadKey() first or use loadAd(key: String)")
        }
    }

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
     * Called khi ad được load thành công
     * Các implementation nên gọi method này sau khi load ad thành công
     * @param key Key của ad đã load thành công
     * @param ad The loaded ad object
     */
    override fun onAdLoaded(key: String, ad: AdType) {
        super.onAdLoaded(key, ad)
        
        // Update refresh time tracking
        lastRefreshTime = System.currentTimeMillis()
        totalRefreshTime = 0
        
        // Start auto refresh if conditions are met
        if (totalRefreshTime < maxRefreshTime && isAdVisible) {
            scheduleNextRefresh()
        }
    }

    /**
     * Called khi ad load thất bại
     * Các implementation nên gọi method này sau khi load ad thất bại
     * @param key Key của ad đã load thất bại
     * @param message Failure message
     */
    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
        
        // Retry after a delay
        if (totalRefreshTime < maxRefreshTime && isAdVisible) {
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
        preloadKey?.let { key ->
            onDestroyAd(key)
            loadAd(key)
        }
    }

    /**
     * Restart auto refresh mechanism
     */
    private fun restartAutoRefresh() {
        preloadKey?.let { key ->
            if (isAdLoaded(key) && isAdVisible) {
                scheduleNextRefresh()
            }
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
        preloadKey?.let { key ->
            if (super.shouldShowAd()) { // Use super.shouldShowAd to avoid isAdVisible check
                if (isAdLoaded(key)) {
                    showAds(key)
                } else {
                    loadAd(key)
                }
                if (totalRefreshTime < maxRefreshTime) {
                    scheduleNextRefresh()
                }
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
        preloadKey?.let { key ->
            if (super.shouldShowAd() && !isAdLoaded(key)) {
                loadAd(key)
            }
        }
    }

    /**
     * Override onDetachedFromWindow để cleanup khi view được detach
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
        destroy() // Destroy ads when the view is detached
    }

    /**
     * Override onVisibilityChanged để handle visibility changes
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val newVisibility = visibility == VISIBLE
        if (isAdVisible == newVisibility) return
        isAdVisible = newVisibility
        
        if (isAdVisible) {
            resume()
        } else {
            pause()
        }
    }

    /**
     * Kiểm tra xem ad đã được load chưa
     * @param key Key để identify ad
     * @return true nếu ad đã load, false nếu chưa
     */
    protected fun isAdLoaded(key: String): Boolean {
        return adStore.containsKey(key)
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

        for (i in 0 until childCount) {
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
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
            }
        }
    }
}
