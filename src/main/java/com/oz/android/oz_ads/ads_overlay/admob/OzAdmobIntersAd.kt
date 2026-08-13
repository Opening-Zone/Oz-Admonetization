package com.oz.android.oz_ads.ads_overlay.admob

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.oz.android.utils.OzLog
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.ads_core.admobs.interstitial.AdmobInterstitial
import com.oz.android.oz_ads.ads_overlay.OverlayAds
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.OzLoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementation of OverlayAds for AdMob Interstitial ads.
 * This class only implements the abstract methods from OverlayAds/OzAds.
 * All business logic (state management, load/show flow) is handled by OzAds/OverlayAds.
 *
 * Update: Now holds a single AdUnitId and a single Activity reference.
 */
open class OzAdmobIntersAd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : OverlayAds<AdmobInterstitial>(context, attrs, defStyleAttr) {

    override val adFormat: String = "interstitial"
    override fun getAdUnitId(key: String): String? = currentAdUnitId

    companion object {
        private const val TAG = "OzAdmobIntersAd"
    }

    // Single variables instead of Maps
    private var currentAdUnitId: String? = null
    private var currentActivity: Activity? = null

    /**
     * Set ad unit ID for a specific placement key.
     * @param key A unique key to identify this ad placement (passed to parent for state management).
     * @param adUnitId The AdMob ad unit ID for the interstitial ad.
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        this.currentAdUnitId = adUnitId
        OzLog.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Set activity for showing the ad.
     * @param key The ad key (used for logging context)
     * @param activity The activity context required to show the ad.
     */
    fun setActivity(key: String, activity: Activity) {
        this.currentActivity = activity
        OzLog.d(TAG, "Activity set for key: $key")
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
        } ?: OzLog.w(TAG, "show() called but no adKey is set. Use setAdUnitId() first.")
    }

    /**
     * Load and then show the interstitial ad.
     * Convenience method that sets activity and triggers loadThenShow().
     * @param activity The activity context required to show the ad.
     * @param showOverlay Show a loading overlay while waiting for ad loads.
     */
    fun loadThenShow(activity: Activity, showOverlay: Boolean = false) {
        adKey?.let { key ->
            setActivity(key, activity)
            if (showOverlay) {
                OzLoadingDialog.showFullScreenLoadingDialog(activity)

                // Launch coroutine on the Main thread
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10_000L) // 10 seconds
                    OzLoadingDialog.hideFullScreenLoadingDialog()
                }
            }
            loadThenShow()
        } ?: OzLog.w(TAG, "loadThenShow() called but no adKey is set. Use setAdUnitId() first.")
    }

    /**
     * Create an AdmobInterstitial instance.
     * Sets up listener to bridge callbacks from AdmobInterstitial to OzAds callbacks.
     */
    override fun createAd(key: String): AdmobInterstitial? {
        val adUnitId = currentAdUnitId

        if (adUnitId.isNullOrBlank()) {
            OzLog.e(TAG, "Ad unit ID is not set for key: $key")
            onAdLoadFailed(key, "Ad unit ID not set")
            return null
        }

        // Create listener that bridges AdmobInterstitial callbacks to OzAds callbacks
        val intersListener = object : OzAdListener<AdmobInterstitial>() {
            override fun onAdLoaded(ad: AdmobInterstitial) {
                OzLoadingDialog.hideFullScreenLoadingDialog()
                // Bridge to OzAds.onAdLoaded() - handles state management
                this@OzAdmobIntersAd.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                OzLoadingDialog.hideFullScreenLoadingDialog()
                // Bridge to OzAds.onAdLoadFailed() - handles state management
                this@OzAdmobIntersAd.onAdLoadFailed(key, error.message, error.code)
            }

            override fun onAdShowedFullScreenContent() {
                // Bridge to OzAds.onAdShown() - handles state management
                this@OzAdmobIntersAd.onAdShown(key)
            }

            override fun onAdDismissedFullScreenContent() {
                // Bridge to OzAds.onAdDismissed() - handles state management and cleanup
                this@OzAdmobIntersAd.onAdDismissed(key)
            }

            override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                // Bridge to OzAds.onAdShowFailed() - handles state management
                this@OzAdmobIntersAd.onAdShowFailed(key, adError.message, adError.code)
            }

            override fun onAdClicked() {
                // Bridge to OzAds.onAdClicked()
                this@OzAdmobIntersAd.onAdClicked(key)
            }
        }

        val mergedListener = intersListener.merge(listener)

        return AdmobInterstitial.create(context, adUnitId, mergedListener)
    }

    /**
     * Load the ad. This is called by OzAds when it's time to load.
     * Only implements the network-specific load call, no business logic.
     */
    override fun onLoadAd(key: String, ad: AdmobInterstitial) {
        OzLog.d(TAG, "Loading interstitial ad for key: $key")
        ad.load()
    }

    /**
     * Show the ad. This is called by OzAds when it's time to show.
     * Only implements the network-specific show call, no business logic.
     * Activity must be set via setActivity() before calling showAds().
     */
    override fun onShowAds(key: String, ad: AdmobInterstitial) {
        OzLoadingDialog.hideFullScreenLoadingDialog()
        val activity = currentActivity
        if (activity == null) {
            OzLog.e(TAG, "Cannot show interstitial ad for key '$key' because activity is null. Call setActivity() first.")
            onAdShowFailed(key, "Activity is null")
            return
        }

        OzLog.d(TAG, "Showing interstitial ad for key: $key")
        ad.show(activity)
    }

    /**
     * Destroy the ad object. Called by OzAds when cleaning up.
     * Only implements the network-specific cleanup, no business logic.
     */
    override fun destroyAd(ad: AdmobInterstitial) {
        OzLog.d(TAG, "Destroying interstitial ad")
        // Interstitial ads are one-time use objects.
    }

    override fun isValid(ad: AdmobInterstitial): Boolean {
        return ad.isAdLoaded()
    }

    override fun onAdShowBlocked(key: String, reason: String?) {
        super.onAdShowBlocked(key, reason)
        currentActivity = null
        listener?.onNextAction()
        OzLog.d(TAG, "Cleaned up activity reference for key: $key after show blocked")
    }

    override fun onAdDismissed(key: String) {
        super.onAdDismissed(key)
        currentActivity = null
        OzLog.d(TAG, "Cleaned up activity reference for key: $key")
    }

    override fun onAdLoadFailed(key: String, message: String?, errorCode: Int?) {
        super.onAdLoadFailed(key, message, errorCode)
    }

    override fun onAdShowFailed(key: String, message: String?, errorCode: Int?) {
        super.onAdShowFailed(key, message, errorCode)
        currentActivity = null
        listener?.onNextAction()
        OzLog.d(TAG, "Cleaned up activity reference for key: $key after show failed")
    }

    /**
     * Override destroy to clean up
     */
    override fun destroy() {
        currentActivity = null
        currentAdUnitId = null
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