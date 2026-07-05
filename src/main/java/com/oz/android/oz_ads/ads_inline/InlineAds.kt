package com.oz.android.oz_ads.ads_inline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import com.oz.android.ads_core.R
import com.oz.android.OzAdsManager
import com.oz.android.oz_ads.OzAds
import io.github.usefulness.shimmer.android.ShimmerFrameLayout
import androidx.core.view.isVisible

/**
 * Abstract class to manage inline ads (banner, native) displayed with content
 * InlineAds is a ViewGroup that can be added to layout as a child view
 *
 * Supports multiple ad networks (AdMob, Max, Meta...) but currently optimized for AdMob
 *
 * InlineAds only supports BANNER and NATIVE formats
 * Refresh time is only available in inline format
 *
 * Concrete implementations will extend this class and implement abstract methods
 */
abstract class InlineAds<AdType> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OzAds<AdType>(context, attrs, defStyleAttr) {
    /**
     * Abstract method for implementations to handle ad pausing
     */
    protected abstract fun onPauseAd()

    /**
     * Abstract method for implementations to handle ad resuming
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

    override fun onAdLoaded(key: String, ad: AdType) {
        super.onAdLoaded(key, ad)
        if (isVisible) {
            scheduleNextRefresh()
        }
    }

    /**
     * Abstract method for implementations to set shimmer size based on ad configuration
     */
    protected abstract fun setShimmerSize(key: String)

    override fun loadAd() {
        loadAd(false)
    }

    fun loadAd(loadInBackground: Boolean) {
        adKey?.let { key ->
            if(!loadInBackground){
                setShimmerSize(key)
                startShimmer()
            }
            super.loadAd()
        } ?: run {
            Log.w(TAG, "No key set. Init the ads with key and id first")
        }
    }

    /**
     * Set ad refresh time (milliseconds)
     * @param timeInMillis Refresh time in milliseconds
     */
    fun setRefreshTime(timeInMillis: Long) {
        if (timeInMillis <= 0) {
            Log.w(TAG, "Refresh time must be greater than 0")
            return
        }
        refreshTime = timeInMillis
        // Only restart if we are already visible and loaded
        adKey?.let { key ->
            if(isVisible && isAdLoaded(key)) {
                scheduleNextRefresh()
            }
        }
    }

    /**
     * Get the current refresh time
     * @return Refresh time in milliseconds
     */
    fun getRefreshTime(): Long = refreshTime

    /**
     * Called when the ad fails to load
     * Implementations should call this method after an ad load failure
     * @param key Key of the ad that failed to load
     * @param message Failure message
     */
    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
        stopShimmer()

        if (isVisible) {
            // If failed, wait for the refresh time then try again
            scheduleNextRefresh()
        }
    }

    /**
     * Schedule ad refresh after a time interval
     */
    private fun scheduleNextRefresh() {
        cancelAutoRefresh()

        if (refreshTime <= 0) return

        refreshRunnable = Runnable {
            if (isVisible) {
                refreshAd()
            }
        }

        refreshHandler.postDelayed(refreshRunnable!!, refreshTime)
    }

    /**
     * Refresh ad (reload a new ad)
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
     * Pause ad (called in onPause of Activity/Fragment)
     */
    fun pause() {
        isVisible = false
        cancelAutoRefresh()
        onPauseAd()
    }

    /**
     * Resume ad (called in onResume of Activity/Fragment)
     */
    fun resume() {
        if (!isAdEnable()) {
            isVisible = false
            onResumeAd()
            return
        }
        isVisible = true
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
     * Override onDetachedFromWindow to cleanup when the view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
        destroy() // Destroy ads when the view is detached
    }

    /**
     * Override onVisibilityChanged to handle visibility changes
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val newVisibility = visibility == VISIBLE
        if (isVisible == newVisibility) return
        isVisible = newVisibility

        if (isVisible) {
            resume()
        } else {
            pause()
        }
    }

    /**
     * Check if the ad has been loaded
     * @param key Key to identify the ad
     * @return true if the ad is loaded, false otherwise
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

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return LayoutParams(p)
    }

    /**
     * Override onMeasure to measure child views
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
     * Override onLayout to layout child views
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
            }
        }
    }

    fun startShimmer() {
        val action = Runnable {
            shimmerLayout?.visibility = VISIBLE
            shimmerLayout?.startShimmer()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            refreshHandler.post(action)
        }
    }

    fun stopShimmer() {
        val action = Runnable {
            shimmerLayout?.stopShimmer()
            shimmerLayout?.visibility = GONE
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            refreshHandler.post(action)
        }
    }
}