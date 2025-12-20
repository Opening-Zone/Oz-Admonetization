package com.oz.android.ads.network.admobs.ads_component.reward

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.oz.android.ads.network.admobs.ads_component.IAdmobAds

/**
 * Class quản lý rewarded interstitial ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
class AdmobRewardedInterstitial(
    private val context: Context,
    private val adUnitId: String
) : IAdmobAds {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var pendingActivity: Activity? = null
    private var pendingRewardCallback: ((RewardItem) -> Unit)? = null

    companion object {
        private const val TAG = "AdmobRewardedInterstitial"
    }

    /**
     * Load quảng cáo rewarded interstitial
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    override fun load() {
        // Request a new ad if one isn't already loaded or loading
        if (adIsLoading || rewardedInterstitialAd != null) {
            Log.d(TAG, "Ad already loading or loaded")
            return
        }

        adIsLoading = true

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded interstitial ad loaded successfully")
                    rewardedInterstitialAd = ad
                    isLoaded = true
                    adIsLoading = false

                    // Setup FullScreenContentCallback
                    setupFullScreenContentCallback(ad)

                    // Nếu có activity và callback đang chờ, tự động hiển thị
                    pendingActivity?.let { activity ->
                        pendingRewardCallback?.let { callback ->
                            show(activity, callback)
                            pendingActivity = null
                            pendingRewardCallback = null
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Rewarded interstitial ad failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                    isLoaded = false
                    adIsLoading = false
                    pendingActivity = null
                    pendingRewardCallback = null
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo rewarded interstitial (implementation từ interface)
     * Lưu ý: Reward interstitial ad cần Activity và callback, sử dụng show(activity, callback) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without activity and callback. Use show(activity: Activity, callback: (RewardItem) -> Unit) for rewarded interstitial ads")
    }

    /**
     * Hiển thị quảng cáo rewarded interstitial
     * @param activity Activity để hiển thị rewarded interstitial ad
     * @param rewardCallback Callback để xử lý khi user nhận reward
     */
    fun show(activity: Activity, rewardCallback: (RewardItem) -> Unit) {
        val currentAd = rewardedInterstitialAd
        if (currentAd == null) {
            Log.w(TAG, "RewardedInterstitialAd is null. Call load() first")
            pendingActivity = activity
            pendingRewardCallback = rewardCallback
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet. It will be shown automatically when loaded")
            pendingActivity = activity
            pendingRewardCallback = rewardCallback
            return
        }

        // Show the ad
        currentAd.show(activity) { rewardItem ->
            Log.d(TAG, "User earned the reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardCallback.invoke(rewardItem)
        }
        Log.d(TAG, "Rewarded interstitial ad displayed")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Reward interstitial ad cần Activity và callback, sử dụng loadThenShow(activity, callback) thay vì method này
     */
    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: (RewardItem) -> Unit) for rewarded interstitial ads")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param activity Activity để hiển thị rewarded interstitial ad
     * @param rewardCallback Callback để xử lý khi user nhận reward
     */
    fun loadThenShow(activity: Activity, rewardCallback: (RewardItem) -> Unit) {
        pendingActivity = activity
        pendingRewardCallback = rewardCallback
        load()
    }

    /**
     * Setup FullScreenContentCallback cho rewarded interstitial ad
     */
    private fun setupFullScreenContentCallback(ad: RewardedInterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed
                Log.d(TAG, "Ad was dismissed")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedInterstitialAd = null
                isLoaded = false
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedInterstitialAd = null
                isLoaded = false
            }

            override fun onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown
                Log.d(TAG, "Ad showed fullscreen content")
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad
                Log.d(TAG, "Ad recorded an impression")
            }

            override fun onAdClicked() {
                // Called when ad is clicked
                Log.d(TAG, "Ad was clicked")
            }
        }
    }

    /**
     * Kiểm tra xem ad đã được load chưa
     * @return true nếu ad đã load, false nếu chưa
     */
    fun isAdLoaded(): Boolean {
        return isLoaded && rewardedInterstitialAd != null
    }

    /**
     * Lấy reward item từ ad (nếu đã load)
     * @return RewardItem nếu ad đã load, null nếu chưa
     */
    fun getRewardItem(): RewardItem? {
        return rewardedInterstitialAd?.rewardItem
    }
}

