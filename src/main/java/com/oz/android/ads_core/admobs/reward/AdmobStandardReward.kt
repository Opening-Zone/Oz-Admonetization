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
 * Standard SDK implementation of Rewarded ads.
 */
class AdmobStandardReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener), AdmobReward {
    private var rewardedAd: RewardedAd? = null
    private var isLoaded = false
    private var adIsLoading = false

    companion object {
        private const val TAG = "AdmobStandardReward"
    }

    override fun load() {
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
                    listener?.onAdLoaded(this@AdmobStandardReward)

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

    override fun show() {
        Log.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    override fun show(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        val currentAd = rewardedAd
        if (currentAd == null) {
            Log.w(TAG, "RewardedAd is null. Call load() first")
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet.")
            return
        }

        currentAd.show(activity, rewardCallback)
        Log.d(TAG, "Rewarded ad displayed")
    }

    override fun loadThenShow() {
        Log.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    override fun loadThenShow(activity: Activity, rewardCallback: OnUserEarnedRewardListener) {
        load()
    }

    private fun setupFullScreenContentCallback(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed")
                rewardedAd = null
                isLoaded = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                rewardedAd = null
                isLoaded = false
                listener?.onAdFailedToShowFullScreenContent(adError.toOzError())
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad recorded an impression")
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                Log.d(TAG, "Ad was clicked")
                listener?.onAdClicked()
            }
        }
    }

    override fun isAdLoaded(): Boolean {
        return isLoaded && rewardedAd != null
    }
}
