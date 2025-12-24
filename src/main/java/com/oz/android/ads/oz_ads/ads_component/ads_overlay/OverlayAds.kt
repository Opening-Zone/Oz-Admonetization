package com.oz.android.ads.oz_ads.ads_component.ads_overlay

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.view.WindowManager
import com.oz.android.ads.R
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.OzAds

/**
 * Abstract class for managing fullscreen overlay ads (Interstitial, App Open)
 * Extends OzAds to inherit basic ad management capabilities
 * Implements logic for time gaps between ad displays
 */
abstract class OverlayAds<AdType> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OzAds<AdType>(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "AdsOverlayManager"
        private const val DEFAULT_TIME_GAP = 25000L // 25 seconds
    }

    // Time when the last ad was closed
    private var lastAdClosedTime: Long = 0

    // Configurable time gap between ads
    private var timeGap: Long = DEFAULT_TIME_GAP

    // Loading indicator view (for ViewGroup display)
    protected var loadingIndicator: View? = null
    
    // Loading dialog (for overlay display when not in layout)
    private var loadingDialog: Dialog? = null

    init {
        // Initialize lastAdClosedTime to allow first ad to show immediately
        lastAdClosedTime = 0
        setupLoadingIndicator()
    }

    /**
     * Setup loading indicator layout
     * Similar to how InlineAds sets up shimmer
     * Can be displayed as ViewGroup child or as Dialog overlay
     */
    private fun setupLoadingIndicator() {
        val loadingResId = R.layout.layout_overlay_loading
        if (loadingResId != 0) {
            try {
                // Setup as ViewGroup child (for when OverlayAds is added to layout)
                loadingIndicator = LayoutInflater.from(context).inflate(loadingResId, this, false)
                loadingIndicator?.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                loadingIndicator?.visibility = View.GONE
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
        Log.d(TAG, "Time gap set to: $timeMillis ms")
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
        val timeSinceLastAd = currentTime - lastAdClosedTime
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
     * Internal method to check time gap (used by showAds)
     */
    private fun isTimeGapSatisfiedInternal(): Boolean {
        return isTimeGapSatisfied()
    }

    /**
     * Override isValidFormat to restrict to overlay formats
     */
    override fun isValidFormat(format: AdsFormat): Boolean {
        return format == AdsFormat.INTERSTITIAL || format == AdsFormat.APP_OPEN
    }

    /**
     * Override getValidFormats to return overlay formats
     */
    override fun getValidFormats(): List<AdsFormat> {
        return listOf(AdsFormat.INTERSTITIAL, AdsFormat.APP_OPEN)
    }

    /**
     * Override showAds to enforce time gap logic
     */
    override fun showAds(key: String) {
        if (!isTimeGapSatisfiedInternal()) {
            Log.d(TAG, "Skipping showAds for key: $key due to time gap restriction")
            // Optionally, we could notify failure here, but skipping is often desired behavior for frequency capping
            onAdShowFailed(key, "Time gap not satisfied")
            return
        }
        super.showAds(key)
    }

    /**
     * Override onAdDismissed to update the last closed time
     */
    override fun onAdDismissed(key: String) {
        super.onAdDismissed(key)
        lastAdClosedTime = System.currentTimeMillis()
        Log.d(TAG, "Ad dismissed for key: $key. Updated lastAdClosedTime to: $lastAdClosedTime")
    }
    
    /**
     * Since Overlay ads are not ViewGroups that display content directly,
     * hideAds implementation might be empty or specific to clearing internal states.
     * For full-screen ads, "hiding" usually means they are dismissed, which is handled by the SDK.
     */
    override fun hideAds() {
         Log.d(TAG, "hideAds called - no-op for Overlay ads as they manage their own visibility via SDK")
    }

    /**
     * Show loading indicator
     * If parent is Activity, show as dialog overlay. Otherwise show as ViewGroup child.
     */
    fun showLoading() {
        // Try to show as dialog overlay first (for overlay ads not in layout)
        val activity = context as? Activity
        if (activity != null && !isAttachedToWindow) {
            showLoadingDialog(activity)
        } else {
            // Show as ViewGroup child (for when OverlayAds is in layout)
            loadingIndicator?.visibility = View.VISIBLE
        }
        Log.d(TAG, "Loading indicator shown")
    }

    /**
     * Hide loading indicator
     */
    fun hideLoading() {
        // Hide dialog if showing
        loadingDialog?.dismiss()
        loadingDialog = null
        
        // Hide ViewGroup child if showing
        loadingIndicator?.visibility = View.GONE
        Log.d(TAG, "Loading indicator hidden")
    }

    /**
     * Show loading indicator as dialog overlay
     */
    private fun showLoadingDialog(activity: Activity) {
        if (loadingDialog != null && loadingDialog!!.isShowing) {
            return // Already showing
        }

        val loadingResId = R.layout.layout_overlay_loading
        if (loadingResId == 0) {
            return
        }

        try {
            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val view = LayoutInflater.from(activity).inflate(loadingResId, null)
            dialog.setContentView(view)
            
            // Make dialog fullscreen and non-cancelable
            dialog.window?.let { window ->
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                )
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                )
            }
            
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
            loadingDialog = dialog
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show loading dialog: ${e.message}")
        }
    }

    /**
     * Override loadAd to show loading indicator
     */
    override fun loadAd() {
        showLoading()
        super.loadAd()
    }

    /**
     * Override onAdLoaded to hide loading indicator
     */
    override fun onAdLoaded(key: String, ad: AdType) {
        super.onAdLoaded(key, ad)
        hideLoading()
    }

    /**
     * Override onAdLoadFailed to hide loading indicator
     */
    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
        hideLoading()
    }

    /**
     * Override onAdShown to hide loading indicator
     */
    override fun onAdShown(key: String) {
        super.onAdShown(key)
        hideLoading()
    }

    /**
     * Override destroy to cleanup loading dialog
     */
    override fun destroy() {
        hideLoading()
        super.destroy()
    }
}
