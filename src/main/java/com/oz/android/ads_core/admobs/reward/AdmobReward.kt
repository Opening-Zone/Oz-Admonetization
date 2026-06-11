package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener

/**
 * Class managing rewarded video ads from AdMob
 * Provides 3 main methods: load, show, and loadThenShow
 */
class AdmobReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener) {
    private var rewardedAd: RewardedAd? = null
    private var isLoaded = false
    private var adIsLoading = false

    companion object {
        private const val TAG = "AdmobReward"
    }

    /**
     * Load rewarded video ad
     * The ad will be loaded but not shown yet
     */
    override fun load() {
        // Request a new ad if one isn't already loaded or loading
        if (adIsLoading || rewardedAd != null) {
            Log.d(TAG, "Ad already loading or loaded")
            return
        }

        adIsLoading = true

        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    isLoaded = true
                    adIsLoading = false
                    
                    rewardedAd?.onPaidEventListener = getOnPaidListener(rewardedAd!!.responseInfo)
                    listener?.onAdLoaded(this@AdmobReward)

                    // Setup FullScreenContentCallback
                    setupFullScreenContentCallback(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${adError.message}")
                    rewardedAd = null
                    isLoaded = false
                    adIsLoading = false
                    
                    listener?.onAdFailedToLoad(adError.toOzError())
                }
            }
        )
    }

    /**
     * Show rewarded video ad (implementation from interface)
     * Note: Reward ad requires an Activity and a callback, use show(activity, callback) instead of this method
     */
    override fun show() {
        Log.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    /**
     * Show rewarded video ad
     * @param activity Activity to display the rewarded ad
     * @param rewardCallback Callback to handle when user receives reward
     */
    fun show(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        val currentAd = rewardedAd
        if (currentAd == null) {
            Log.w(TAG, "RewardedAd is null. Call load() first")
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet.")
            return
        }

        // Show the ad
        currentAd.show(activity, rewardCallback)
        Log.d(TAG, "Rewarded ad displayed")
    }

    /**
     * Load and automatically show the ad when loading finishes (implementation from interface)
     * Note: Reward ad requires an Activity and a callback, use loadThenShow(activity, callback) instead of this method
     */
    override fun loadThenShow() {
        Log.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    /**
     * Load and automatically show the ad when loading finishes
     * @param activity Activity to display the rewarded ad
     * @param rewardCallback Callback to handle when user receives reward
     */
    fun loadThenShow(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        load()
    }

    /**
     * Setup FullScreenContentCallback cho rewarded ad
     */
    private fun setupFullScreenContentCallback(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed
                Log.d(TAG, "Ad was dismissed")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedAd = null
                isLoaded = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedAd = null
                isLoaded = false
                listener?.onAdFailedToShowFullScreenContent(adError.toOzError())
            }

            override fun onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown
                Log.d(TAG, "Ad showed fullscreen content")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad
                Log.d(TAG, "Ad recorded an impression")
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                // Called when ad is clicked
                Log.d(TAG, "Ad was clicked")
                listener?.onAdClicked()
            }
        }
    }

    /**
     * Check if the ad has been loaded
     * @return true if the ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean {
        return isLoaded && rewardedAd != null
    }
}

