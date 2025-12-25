package com.oz.android.ads.network.admobs.ads_component.reward

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.wrapper.OzAdListener

/**
 * Class quản lý rewarded video ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AdmobReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener) {
    private var rewardedAd: RewardedAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var pendingActivity: Activity? = null
    private var pendingRewardCallback: OnUserEarnedRewardListener? = null

    companion object {
        private const val TAG = "AdmobReward"
    }

    /**
     * Load quảng cáo rewarded video
     * Quảng cáo sẽ được load nhưng chưa hiển thị
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
                    Log.e(TAG, "Rewarded ad failed to load: ${adError.message}")
                    rewardedAd = null
                    isLoaded = false
                    adIsLoading = false
                    pendingActivity = null
                    pendingRewardCallback = null
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo rewarded video (implementation từ interface)
     * Lưu ý: Reward ad cần Activity và callback, sử dụng show(activity, callback) thay vì method này
     */
    override fun show() {
        Log.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    /**
     * Hiển thị quảng cáo rewarded video
     * @param activity Activity để hiển thị rewarded ad
     * @param rewardCallback Callback để xử lý khi user nhận reward
     */
    fun show(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        val currentAd = rewardedAd
        if (currentAd == null) {
            Log.w(TAG, "RewardedAd is null. Call load() first")
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
        currentAd.show(activity, rewardCallback)
        Log.d(TAG, "Rewarded ad displayed")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Reward ad cần Activity và callback, sử dụng loadThenShow(activity, callback) thay vì method này
     */
    override fun loadThenShow() {
        Log.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param activity Activity để hiển thị rewarded ad
     * @param rewardCallback Callback để xử lý khi user nhận reward
     */
    fun loadThenShow(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        pendingActivity = activity
        pendingRewardCallback = rewardCallback
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
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time
                rewardedAd = null
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
        return isLoaded && rewardedAd != null
    }
}

