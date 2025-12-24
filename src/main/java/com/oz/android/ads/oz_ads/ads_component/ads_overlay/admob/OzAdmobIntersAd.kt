package com.oz.android.ads.oz_ads.ads_component.ads_overlay.admob

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.oz.android.ads.network.admobs.ads_component.OzAdmobListener
import com.oz.android.ads.network.admobs.ads_component.interstitial.AdmobInterstitial
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.ads_overlay.OverlayAds
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of OverlayAds for AdMob Interstitial ads.
 * This class only implements the abstract methods from OverlayAds/OzAds.
 * All business logic (state management, load/show flow) is handled by OzAds/OverlayAds.
 */
class OzAdmobIntersAd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OverlayAds<AdmobInterstitial>(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "OzAdmobIntersAd"
    }

    private val adUnitIds = ConcurrentHashMap<String, String>()
    // Store activity per key for showing ads (interstitial requires Activity)
    private val activities = ConcurrentHashMap<String, Activity>()
    
    // Error callbacks for external error tracking
    var onLoadErrorCallback: ((String, String?) -> Unit)? = null
    var onShowErrorCallback: ((String, String?) -> Unit)? = null

    init {
        // Set format to INTERSTITIAL by default for this specific class
        setAdsFormat(AdsFormat.INTERSTITIAL)
    }

    /**
     * Set ad unit ID for a specific placement key.
     * @param key A unique key to identify this ad placement.
     * @param adUnitId The AdMob ad unit ID for the interstitial ad.
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        adUnitIds[key] = adUnitId
        Log.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Set activity for showing the ad.
     * @param key The ad key
     * @param activity The activity context required to show the ad.
     */
    fun setActivity(key: String, activity: Activity) {
        activities[key] = activity
        Log.d(TAG, "Activity set for key: $key")
    }

    /**
     * Show the interstitial ad.
     * Convenience method that sets activity and triggers showAds().
     * @param activity The activity context required to show the ad.
     */
    fun show(activity: Activity) {
        adKey?.let { key ->
            setActivity(key, activity)
            showAds(key)
        } ?: Log.w(TAG, "show() called but no adKey is set. Use setAdUnitId() first.")
    }

    /**
     * Load and then show the interstitial ad.
     * Convenience method that sets activity and triggers loadThenShow().
     * @param activity The activity context required to show the ad.
     */
    fun loadThenShow(activity: Activity) {
        adKey?.let { key ->
            setActivity(key, activity)
            loadThenShow()
        } ?: Log.w(TAG, "loadThenShow() called but no adKey is set. Use setAdUnitId() first.")
    }

    /**
     * Create an AdmobInterstitial instance for the given key.
     * Sets up listener to bridge callbacks from AdmobInterstitial to OzAds callbacks.
     */
    override fun createAd(key: String): AdmobInterstitial? {
        val adUnitId = adUnitIds[key]
        if (adUnitId.isNullOrBlank()) {
            Log.e(TAG, "Ad unit ID is not set for key: $key")
            onAdLoadFailed(key, "Ad unit ID not set")
            return null
        }

        // Create listener that bridges AdmobInterstitial callbacks to OzAds callbacks
        val listener = object : OzAdmobListener<AdmobInterstitial>() {
            override fun onAdLoaded(ad: AdmobInterstitial) {
                // Bridge to OzAds.onAdLoaded() - handles state management
                this@OzAdmobIntersAd.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // Bridge to OzAds.onAdLoadFailed() - handles state management
                this@OzAdmobIntersAd.onAdLoadFailed(key, error.message)
            }

            override fun onAdShowedFullScreenContent() {
                // Bridge to OzAds.onAdShown() - handles state management
                this@OzAdmobIntersAd.onAdShown(key)
            }

            override fun onAdDismissedFullScreenContent() {
                // Bridge to OzAds.onAdDismissed() - handles state management and cleanup
                this@OzAdmobIntersAd.onAdDismissed(key)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Bridge to OzAds.onAdShowFailed() - handles state management
                this@OzAdmobIntersAd.onAdShowFailed(key, adError.message)
            }
        }

        return AdmobInterstitial(context, adUnitId, listener)
    }

    /**
     * Load the ad. This is called by OzAds when it's time to load.
     * Only implements the network-specific load call, no business logic.
     */
    override fun onLoadAd(key: String, ad: AdmobInterstitial) {
        Log.d(TAG, "Loading interstitial ad for key: $key")
        ad.load()
    }

    /**
     * Show the ad. This is called by OzAds when it's time to show.
     * Only implements the network-specific show call, no business logic.
     * Activity must be set via setActivity() before calling showAds().
     */
    override fun onShowAds(key: String, ad: AdmobInterstitial) {
        val activity = activities[key]
        if (activity == null) {
            Log.e(TAG, "Cannot show interstitial ad for key '$key' because activity is null. Call setActivity() first.")
            onAdShowFailed(key, "Activity is null")
            return
        }
        
        Log.d(TAG, "Showing interstitial ad for key: $key")
        ad.show(activity)
        
        // Note: We don't clear activity here because it might be needed for retry
        // Activity will be cleared when ad is dismissed or destroyed
    }

    /**
     * Destroy the ad object. Called by OzAds when cleaning up.
     * Only implements the network-specific cleanup, no business logic.
     */
    override fun destroyAd(ad: AdmobInterstitial) {
        Log.d(TAG, "Destroying interstitial ad")
        // Interstitial ads are one-time use objects. The AdmobInterstitial class handles
        // nullifying the ad reference after it's shown or fails to show.
        // No explicit destroy call is necessary on the ad object itself.
    }

    /**
     * Override onAdDismissed to clean up activity reference
     */
    override fun onAdDismissed(key: String) {
        super.onAdDismissed(key)
        activities.remove(key)
        Log.d(TAG, "Cleaned up activity reference for key: $key")
    }

    /**
     * Override onAdLoadFailed to notify error callback
     */
    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
        onLoadErrorCallback?.invoke(key, message)
    }

    /**
     * Override onAdShowFailed to clean up activity reference and notify error callback
     */
    override fun onAdShowFailed(key: String, message: String?) {
        super.onAdShowFailed(key, message)
        activities.remove(key)
        onShowErrorCallback?.invoke(key, message)
        Log.d(TAG, "Cleaned up activity reference for key: $key after show failed")
    }

    /**
     * Override destroy to clean up all activities
     */
    override fun destroy() {
        activities.clear()
        super.destroy()
    }

    /**
     * ViewGroup layout method - OverlayAds doesn't need layout, but ViewGroup requires it
     */
    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        // Overlay ads don't display content in the ViewGroup, so no layout needed
    }
}
