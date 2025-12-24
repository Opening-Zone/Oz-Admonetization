package com.oz.android.ads.oz_ads.ads_component.ads_inline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import com.oz.android.ads.R
import com.oz.android.ads.oz_ads.OzAdsManager
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.OzAds
import io.github.usefulness.shimmer.android.ShimmerFrameLayout

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
) : OzAds<AdType>(context, attrs, defStyleAttr) {
    /**
     * Abstract method để các implementation xử lý pause ad
     */
    protected abstract fun onPauseAd()

    /**
     * Abstract method để các implementation xử lý resume ad
     */
    protected abstract fun onResumeAd()

    companion object {
        private const val TAG = "InlineAds"

        // Default refresh times (in milliseconds)
        private const val DEFAULT_REFRESH_TIME = 0L //default is turned off
    }

    // Refresh time management
    private var refreshTime: Long = DEFAULT_REFRESH_TIME

    // Auto refresh handler
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // Ad visibility state
    private var isAdVisible = false

    //shimmer
    protected var shimmerLayout: ShimmerFrameLayout? = null

    init {
        setupShimmerLayout()
    }

    private fun setupShimmerLayout() {
        shimmerLayout = ShimmerFrameLayout(context)
        shimmerLayout?.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

        // Get the resource ID from the child class
        val shimmerResId = R.layout.layout_flex_shimmer

        if (shimmerResId != 0) {
            // Inflate the XML layout into the ShimmerFrameLayout
            LayoutInflater.from(context).inflate(shimmerResId, shimmerLayout, true)
        }

        shimmerLayout?.visibility = GONE
        addView(shimmerLayout)
    }

    override fun onAdShown(key: String) {
        super.onAdShown(key)
        stopShimmer()
    }

    override fun isValidFormat(format: AdsFormat): Boolean {
        return format == AdsFormat.BANNER || format == AdsFormat.NATIVE
    }

    override fun getValidFormats(): List<AdsFormat> {
        return listOf(AdsFormat.BANNER, AdsFormat.NATIVE)
    }

    /**
     * Abstract method để các implementation set shimmer size dựa trên cấu hình ad
     */
    protected abstract fun setShimmerSize(key: String)

    override fun loadAd() {
        adKey?.let { key ->
            setShimmerSize(key)
            startShimmer()
            super.loadAd()
        } ?: run {
            Log.w(TAG, "No key set. Init the ads with key and id first")
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
        // Only restart if we are already visible and loaded
        adKey?.let { key ->
            if(isAdVisible && isAdLoaded(key)) {
                scheduleNextRefresh()
            }
        }
    }

    /**
     * Get thời gian refresh hiện tại
     * @return Thời gian refresh tính bằng milliseconds
     */
    fun getRefreshTime(): Long = refreshTime

    /**
     * Called khi ad được load thành công
     * Các implementation nên gọi method này sau khi load ad thành công
     * @param key Key của ad đã load thành công
     * @param ad The loaded ad object
     */
    override fun onAdLoaded(key: String, ad: AdType) {
        super.onAdLoaded(key, ad)

        if (isAdVisible) {
            // FIX: Show the ad immediately when it loads!
            showAds(key)

            // FIX: Start the timer only AFTER the ad has loaded/shown
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
        stopShimmer()

        if (isAdVisible) {
            // If failed, wait for the refresh time then try again
            scheduleNextRefresh()
        }
    }

    /**
     * Schedule refresh ad sau một khoảng thời gian
     */
    private fun scheduleNextRefresh() {
        cancelAutoRefresh()

        if (refreshTime <= 0) return

        refreshRunnable = Runnable {
            if (isAdVisible) {
                refreshAd()
            }
        }

        refreshHandler.postDelayed(refreshRunnable!!, refreshTime)
    }

    /**
     * Refresh ad (load lại ad mới)
     */
    fun refreshAd() {
        Log.d(TAG, "Refreshing ad...")
        adKey?.let {
            loadThenShow()
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
        adKey?.let { key ->
            if (isAdEnable()) {
                if (isAdLoaded(key)) {
                    // CASE 1: Ad is already loaded. Show it immediately.
                    showAds(key)
                    // Since it's shown, we now start the timer for the *next* refresh.
                    scheduleNextRefresh()
                } else {
                    // CASE 2: Ad is NOT loaded. Load it.
                    // DO NOT call scheduleNextRefresh() here.
                    // Wait for onAdLoaded() to call it.
                    loadAd()
                }
            }
        }
        onResumeAd()
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
        val newVisibility = visibility == View.VISIBLE
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
        return OzAdsManager.getInstance().getAd<AdType>(key) != null
    }

    /**
     * Default layout params cho InlineAds
     */
    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams {
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
            if (child.visibility != View.GONE) {
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
            if (child.visibility != View.GONE) {
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
            }
        }
    }

    fun startShimmer(){
        shimmerLayout?.visibility = View.VISIBLE
        shimmerLayout?.startShimmer()
    }

    fun stopShimmer(){
        shimmerLayout?.stopShimmer()
        shimmerLayout?.visibility = View.GONE
    }
}