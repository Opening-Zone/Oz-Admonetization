package com.oz.android.oz_ads.ads_overlay

import com.oz.android.ads_core.R
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import com.oz.android.ads_core.BuildConfig
import com.oz.android.utils.enums.AdState
import com.oz.android.oz_ads.OzAds
import com.oz.android.OzAdsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract class for managing fullscreen overlay ads (Interstitial, App Open)
 * Extends OzAds to inherit basic ad management capabilities
 * Implements logic for time gaps between ad displays.
 *
 * UPDATE: The time gap logic is now Global per Ad Category (Class Type).
 */
abstract class OverlayAds<AdType> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OzAds<AdType>(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "AdsOverlayManager"
        private val DEFAULT_TIME_GAP = if (BuildConfig.DEBUG) 30000L else 30000L

        /**
         * Global storage for the last closed time.
         * Key: The Ad Category String (usually the class name).
         * Value: Timestamp in milliseconds.
         * Using ConcurrentHashMap to handle potential access from different threads safely.
         */
        private val globalLastAdClosedTimes = ConcurrentHashMap<String, Long>()
    }

    // Configurable time gap between ads (Per instance configuration)
    private var timeGap: Long = DEFAULT_TIME_GAP

    // Loading indicator view (for ViewGroup display)
    protected var loadingIndicator: View? = null

    // Loading dialog (for overlay display when not in layout)
    private var loadingDialog: Dialog? = null

    init {
        setupLoadingIndicator()
    }

    /**
     * Determines the category for this ad instance to group cooldowns.
     * By default, it returns the Class Name of the implementation.
     *
     * Example:
     * If you have class `InterstitialAds : OverlayAds`, the key is "InterstitialAds".
     * All instances of `InterstitialAds` will share the same timer.
     *
     * You can override this if you want to group different classes together.
     */
    protected open fun getAdCategory(): String {
        return this::class.java.name
    }

    /**
     * Helper to get the last closed time for the current category
     */
    private fun getLastClosedTimeGlobal(): Long {
        return globalLastAdClosedTimes[getAdCategory()] ?: 0L
    }

    /**
     * Helper to update the last closed time for the current category
     */
    private fun updateLastClosedTimeGlobal() {
        val now = System.currentTimeMillis()
        globalLastAdClosedTimes[getAdCategory()] = now
        Log.d(TAG, "Updated global cooldown for [${getAdCategory()}]: $now")
    }

    private fun setupLoadingIndicator() {
        val loadingResId = R.layout.layout_overlay_loading
        if (loadingResId != 0) {
            try {
                // Setup as ViewGroup child (for when OverlayAds is added to layout)
                loadingIndicator = LayoutInflater.from(context).inflate(loadingResId, this, false)
                loadingIndicator?.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                loadingIndicator?.visibility = GONE
                addView(loadingIndicator)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inflate loading indicator layout: ${e.message}")
            }
        }
    }

    /**
     * Set the time gap between ads
     * @param timeMillis Time in milliseconds
     */
    fun setTimeGap(timeMillis: Long) {
        if (timeMillis < 0) {
            Log.w(TAG, "Time gap cannot be negative. Ignoring.")
            return
        }
        timeGap = timeMillis
        Log.d(TAG, "Time gap set to: $timeMillis ms for instance of ${getAdCategory()}")
    }

    /**
     * Get the current time gap setting
     */
    fun getTimeGap(): Long = timeGap

    /**
     * Get the remaining time until the next ad can be shown (in milliseconds)
     * @return Remaining time in milliseconds, or 0 if time gap is satisfied
     */
    fun getRemainingCooldownTime(): Long {
        val currentTime = System.currentTimeMillis()
        val lastClosed = getLastClosedTimeGlobal()
        val timeSinceLastAd = currentTime - lastClosed

        // If lastClosed is 0, it means never shown, so remaining is 0
        if (lastClosed == 0L) return 0L

        val remaining = timeGap - timeSinceLastAd
        return if (remaining > 0) remaining else 0
    }

    /**
     * Check if enough time has passed since the last ad was shown
     */
    fun isTimeGapSatisfied(): Boolean {
        return getRemainingCooldownTime() == 0L
    }

    /**
     * Override showAds to enforce time gap logic and check if another fullscreen ad is showing
     */
    override fun showAds(key: String) {
        if (!OzAdsManager.getInstance().canShowFullScreenAd()) {
            Log.d(TAG, "Skipping showAds for key: $key. Another fullscreen ad is already showing.")
            onAdShowFailed(key, "Another fullscreen ad is already showing.")
            return
        }

        if (!isTimeGapSatisfied()) {
            val remaining = getRemainingCooldownTime()
            val category = getAdCategory()
            Log.d(TAG, "Skipping showAds ($category) for key: $key. Cooldown active. Remaining: ${remaining}ms")

            onAdShowFailed(key, "Time gap not satisfied. Wait ${remaining}ms")
            return
        }
        super.showAds(key)
    }

    /**
     * Override onAdDismissed to update the last closed time globally for this type
     */
    override fun onAdDismissed(key: String) {
        super.onAdDismissed(key)
        updateLastClosedTimeGlobal()
        OzAdsManager.getInstance().onAdsFullScreenDismissed()
        Log.d(TAG, "Ad dismissed for key: $key. Global timer updated for ${getAdCategory()}")
    }

    override fun hideAds() {
        Log.d(TAG, "hideAds called - no-op for Overlay ads")
    }

    /**
     * Show loading indicator
     */
    fun showLoading() {
        val activity = context as? Activity
        if (activity != null && !isAttachedToWindow) {
            showLoadingDialog(activity)
        } else {
            loadingIndicator?.visibility = VISIBLE
        }
        Log.d(TAG, "Loading indicator shown")
    }

    /**
     * Hide loading indicator
     */
    fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
        loadingIndicator?.visibility = GONE
        Log.d(TAG, "Loading indicator hidden")
    }

    private fun showLoadingDialog(activity: Activity) {
        if (loadingDialog != null && loadingDialog!!.isShowing) {
            return
        }

        val loadingResId = R.layout.layout_overlay_loading
        if (loadingResId == 0) return

        try {
            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val view = LayoutInflater.from(activity).inflate(loadingResId, null)
            dialog.setContentView(view)

            dialog.window?.let { window ->
                window.setLayout(MATCH_PARENT, MATCH_PARENT)
                window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                window.setFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            }

            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
            loadingDialog = dialog
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show loading dialog: ${e.message}")
        }
    }

    fun loadAd(loadInBackground: Boolean = true) {
        if (adKey == null) return
        when (getAdState(adKey!!)) {
            AdState.IDLE -> {
                if (!loadInBackground) showLoading()
                super.loadAd()
            }
            else -> super.loadAd()
        }
    }

    override fun loadThenShow() {
        showLoading()
        super.loadThenShow()
    }

    override fun onAdLoaded(key: String, ad: AdType) {
        super.onAdLoaded(key, ad)
        hideLoading()
    }

    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
        hideLoading()
    }

    override fun onAdShown(key: String) {
        super.onAdShown(key)
        hideLoading()
        OzAdsManager.getInstance().onAdsFullScreenShowing()
    }

    override fun destroy() {
        hideLoading()
        super.destroy()
    }
}