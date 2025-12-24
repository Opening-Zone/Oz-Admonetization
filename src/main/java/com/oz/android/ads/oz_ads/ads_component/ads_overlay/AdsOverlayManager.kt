package com.oz.android.ads.oz_ads.ads_component.ads_overlay

import android.content.Context
import android.util.Log
import com.oz.android.ads.oz_ads.OzAdsManager
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.OzAds

/**
 * Abstract class for managing fullscreen overlay ads (Interstitial, App Open)
 * Extends OzAds to inherit basic ad management capabilities
 * Implements logic for time gaps between ad displays
 */
abstract class AdsOverlayManager<AdType> @JvmOverloads constructor(
    context: Context
) : OzAds<AdType>(context) {

    companion object {
        private const val TAG = "AdsOverlayManager"
        private const val DEFAULT_TIME_GAP = 25000L // 25 seconds
    }

    // Time when the last ad was closed
    private var lastAdClosedTime: Long = 0

    // Configurable time gap between ads
    private var timeGap: Long = DEFAULT_TIME_GAP

    init {
        // Initialize lastAdClosedTime to allow first ad to show immediately
        lastAdClosedTime = 0
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
     * Check if enough time has passed since the last ad was shown
     */
    private fun isTimeGapSatisfied(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAdClosedTime
        val satisfied = timeSinceLastAd >= timeGap
        
        if (!satisfied) {
            Log.d(TAG, "Time gap not satisfied. Time since last ad: $timeSinceLastAd ms, Required: $timeGap ms")
        }
        
        return satisfied
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
        if (!isTimeGapSatisfied()) {
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
}
