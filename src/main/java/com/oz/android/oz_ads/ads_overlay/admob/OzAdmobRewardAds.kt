package com.oz.android.oz_ads.ads_overlay.admob

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.oz.android.utils.OzLog
import com.oz.android.ads_core.admobs.reward.AdmobReward
import com.oz.android.oz_ads.ads_overlay.OverlayAds
import com.oz.android.utils.OzLoadingDialog
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.listener.OzAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementation of OverlayAds for AdMob Rewarded ads.
 * This class only implements the abstract methods from OverlayAds/OzAds.
 * All business logic (state management, load/show flow) is handled by OzAds/OverlayAds.
 *
 * Update: Now holds a single AdUnitId, a single Activity reference, and a reward callback.
 */
open class OzAdmobRewardAds @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : OverlayAds<AdmobReward>(context, attrs, defStyleAttr) {

    init {
        setTimeGap(0)
    }

    companion object {
        private const val TAG = "OzAdmobRewardAds"
    }

    // Single variables instead of Maps
    private var currentAdUnitId: String? = null
    private var currentActivity: Activity? = null
    private var currentRewardCallback: OnUserEarnedRewardListener? = null

    /**
     * Set ad unit ID for a specific placement key.
     * @param key A unique key to identify this ad placement (passed to parent for state management).
     * @param adUnitId The AdMob ad unit ID for the rewarded ad.
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
     * Show the rewarded ad.
     * Convenience method that sets activity, callback, and triggers showAds().
     * @param activity The activity context required to show the ad.
     * @param rewardCallback Callback to handle when the user earns a reward.
     */
    fun show(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        adKey?.let { key ->
            setActivity(key, activity)
            this.currentRewardCallback = rewardCallback
            showAds(key)
        } ?: OzLog.w(TAG, "show() called but no adKey is set. Use setAdUnitId() first.")
    }

    /**
     * Load and then show the rewarded ad.
     * Convenience method that sets activity, callback, and triggers loadThenShow().
     * @param activity The activity context required to show the ad.
     * @param rewardCallback Callback to handle when the user earns a reward.
     * @param showOverlay Show a loading overlay while waiting for ad loads.
     */
    fun loadThenShow(activity: Activity, rewardCallback: OnUserEarnedRewardListener, showOverlay: Boolean = false) {
        adKey?.let { key ->
            setActivity(key, activity)
            this.currentRewardCallback = rewardCallback
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
     * Create an AdmobReward instance.
     * Sets up listener to bridge callbacks from AdmobReward to OzAds callbacks.
     */
    override fun createAd(key: String): AdmobReward? {
        val adUnitId = currentAdUnitId

        if (adUnitId.isNullOrBlank()) {
            OzLog.e(TAG, "Ad unit ID is not set for key: $key")
            onAdLoadFailed(key, "Ad unit ID not set")
            return null
        }

        // Create listener that bridges AdmobReward callbacks to OzAds callbacks
        val rewardListener = object : OzAdListener<AdmobReward>() {
            override fun onAdLoaded(ad: AdmobReward) {
                OzLoadingDialog.hideFullScreenLoadingDialog()
                // Bridge to OzAds.onAdLoaded() - handles state management
                this@OzAdmobRewardAds.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                OzLoadingDialog.hideFullScreenLoadingDialog()
                // Bridge to OzAds.onAdLoadFailed() - handles state management
                this@OzAdmobRewardAds.onAdLoadFailed(key, error.message)
            }

            override fun onAdShowedFullScreenContent() {
                // Bridge to OzAds.onAdShown() - handles state management
                this@OzAdmobRewardAds.onAdShown(key)
            }

            override fun onAdDismissedFullScreenContent() {
                // Bridge to OzAds.onAdDismissed() - handles state management and cleanup
                this@OzAdmobRewardAds.onAdDismissed(key)
            }

            override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                // Bridge to OzAds.onAdShowFailed() - handles state management
                this@OzAdmobRewardAds.onAdShowFailed(key, adError.message)
            }

            override fun onAdClicked() {
                // Bridge to OzAds.onAdClicked()
                this@OzAdmobRewardAds.onAdClicked(key)
            }
        }

        val mergedListener = rewardListener.merge(listener)

        return AdmobReward.create(context, adUnitId, mergedListener)
    }

    /**
     * Load the ad. This is called by OzAds when it's time to load.
     * Only implements the network-specific load call, no business logic.
     */
    override fun onLoadAd(key: String, ad: AdmobReward) {
        OzLog.d(TAG, "Loading rewarded ad for key: $key")
        ad.load()
    }

    /**
     * Show the ad. This is called by OzAds when it's time to show.
     * Only implements the network-specific show call, no business logic.
     * Activity and rewardCallback must be set before calling showAds().
     */
    override fun onShowAds(key: String, ad: AdmobReward) {
        OzLoadingDialog.hideFullScreenLoadingDialog()
        val activity = currentActivity
        val callback = currentRewardCallback
        
        if (activity == null || callback == null) {
            OzLog.e(TAG, "Cannot show rewarded ad for key '$key' because activity or callback is null. Call show(activity, callback) or loadThenShow(activity, callback) first.")
            onAdShowFailed(key, "Activity or callback is null")
            return
        }

        OzLog.d(TAG, "Showing rewarded ad for key: $key")
        ad.show(activity, callback)
    }

    /**
     * Destroy the ad object. Called by OzAds when cleaning up.
     * Only implements the network-specific cleanup, no business logic.
     */
    override fun destroyAd(ad: AdmobReward) {
        OzLog.d(TAG, "Destroying rewarded ad")
        // Rewarded ads are one-time use objects in AdMob.
    }

    override fun isValid(ad: AdmobReward): Boolean {
        return ad.isAdLoaded()
    }

    override fun onAdShowBlocked(key: String, reason: String?) {
        super.onAdShowBlocked(key, reason)
        currentActivity = null
        currentRewardCallback = null
        listener?.onNextAction()
        OzLog.d(TAG, "Cleaned up activity and callback reference for key: $key after show blocked")
    }

    /**
     * Override onAdDismissed to clean up activity and callback references
     */
    override fun onAdDismissed(key: String) {
        super.onAdDismissed(key)
        currentActivity = null
        currentRewardCallback = null
        OzLog.d(TAG, "Cleaned up activity and callback reference for key: $key")
    }

    /**
     * Override onAdLoadFailed to notify error callback
     */
    override fun onAdLoadFailed(key: String, message: String?) {
        super.onAdLoadFailed(key, message)
    }

    /**
     * Override onAdShowFailed to clean up activity and callback references and notify error callback
     */
    override fun onAdShowFailed(key: String, message: String?) {
        super.onAdShowFailed(key, message)
        currentActivity = null
        currentRewardCallback = null
        listener?.onNextAction()
        OzLog.d(TAG, "Cleaned up activity and callback reference for key: $key after show failed")
    }

    /**
     * Override destroy to clean up
     */
    override fun destroy() {
        currentActivity = null
        currentAdUnitId = null
        currentRewardCallback = null
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
