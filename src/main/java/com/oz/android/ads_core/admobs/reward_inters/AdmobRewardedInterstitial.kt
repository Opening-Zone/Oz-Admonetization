package com.oz.android.ads_core.admobs.reward_inters

import android.app.Activity
import android.content.Context
import com.oz.android.utils.OzLog
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger

/**
 * Class managing rewarded interstitial ads from AdMob
 * Provides 3 main methods: load, show, and loadThenShow
 */
class AdmobRewardedInterstitial(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobRewardedInterstitial>
) : AdmobBase<AdmobRewardedInterstitial>(context, adUnitId, listener) {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    companion object {
        private const val TAG = "AdmobRewardedInterstitial"
    }

    /**
     * Load rewarded interstitial ad
     * The ad will be loaded but not shown yet
     */
    override fun load() {
        // Request a new ad if one isn't already loaded or loading
        if (rewardedInterstitialAd != null) {
            OzLog.d(TAG, "Ad already loaded")
            return
        }

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    OzLog.d(TAG, "Rewarded interstitial ad loaded successfully")
                    rewardedInterstitialAd = ad

                    // Setup FullScreenContentCallback
                    setupFullScreenContentCallback(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    OzLog.e(TAG, "Rewarded interstitial ad failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                }
            }
        )
    }

    /**
     * Show rewarded interstitial ad (implementation from interface)
     * Note: Reward interstitial ad requires an Activity and a callback, use show(activity, callback) instead of this method
     */
    override fun show() {
        OzLog.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: (RewardItem) -> Unit) for rewarded interstitial ads"
        )
    }

    /**
     * Show rewarded interstitial ad
     * @param activity Activity to display the rewarded interstitial ad
     * @param rewardCallback Callback to handle when user receives reward
     */
    fun show(activity: Activity, rewardCallback: (RewardItem) -> Unit) {
        val currentAd = rewardedInterstitialAd
        if (currentAd == null) {
            OzLog.w(TAG, "RewardedInterstitialAd is null. Call load() first")
            return
        }

        // Show the ad
        currentAd.show(activity) { rewardItem ->
            OzLog.d(TAG, "User earned the reward: ${rewardItem.amount} ${rewardItem.type}")
            OzEventLogger.logAdRewardEarned(context, adUnitId, rewardItem.type, rewardItem.amount)
            rewardCallback.invoke(rewardItem)
        }
        OzLog.d(TAG, "Rewarded interstitial ad displayed")
    }

    /**
     * Load and automatically show the ad when loading finishes (implementation from interface)
     * Note: Reward interstitial ad requires an Activity and a callback, use loadThenShow(activity, callback) instead of this method
     */
    override fun loadThenShow() {
        OzLog.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: (RewardItem) -> Unit) for rewarded interstitial ads"
        )
    }

    /**
     * Load and automatically show the ad when loading finishes
     * @param activity Activity to display the rewarded interstitial ad
     * @param rewardCallback Callback to handle when user receives reward
     */
    fun loadThenShow(activity: Activity, rewardCallback: (RewardItem) -> Unit) {
        load()
    }

    /**
     * Setup FullScreenContentCallback cho rewarded interstitial ad
     */
    private fun setupFullScreenContentCallback(ad: RewardedInterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed
                OzLog.d(TAG, "Ad was dismissed")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedInterstitialAd = null
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show
                OzLog.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedInterstitialAd = null
            }

            override fun onAdShowedFullScreenContent() {
                OzLog.d(TAG, "Ad showed fullscreen content.")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad
                OzLog.d(TAG, "Ad recorded an impression")
            }

            override fun onAdClicked() {
                // Called when ad is clicked
                OzLog.d(TAG, "Ad was clicked")
            }
        }
    }

    /**
     * Check if the ad has been loaded
     * @return true if the ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean {
        return rewardedInterstitialAd != null
    }

    /**
     * Get reward item from the ad (if loaded)
     * @return RewardItem if ad is loaded, null otherwise
     */
    fun getRewardItem(): RewardItem? {
        return rewardedInterstitialAd?.rewardItem
    }
}

