package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger

class AdmobNextReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener), AdmobReward {

    private var nextGenAd: RewardedAd? = null

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobNextReward"
    }

    override fun load() {
        if (nextGenAd != null) {
            Log.d(TAG, "Ad already loaded (Next-Gen)")
            return
        }

        RewardedAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    // ── Runs on GMA background thread ──
                    // State updates: no UI, safe on BG.
                    Log.d(TAG, "Rewarded ad loaded successfully (Next-Gen)")
                    nextGenAd = ad

                    // setupNextGenFullScreenCallback only sets a property, safe on BG.
                    setupNextGenFullScreenCallback(ad)

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdLoaded(this@AdmobNextReward)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // ── Runs on GMA background thread ──
                    // State cleanup: no UI, stays on BG.
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message} (Next-Gen)")
                    nextGenAd = null

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdFailedToLoad(error.toOzError())
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

    override fun show(activity: Activity, rewardCallback: com.google.android.gms.ads.OnUserEarnedRewardListener) {
        val showRunnable = Runnable {
            val currentAd = nextGenAd
            if (currentAd == null) {
                Log.w(TAG, "RewardedAd is null (Next-Gen). Call load() first")
                return@Runnable
            }

            currentAd.show(activity, object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    val gmsRewardItem = object : com.google.android.gms.ads.rewarded.RewardItem {
                        override val type: String = reward.type
                        override val amount: Int = reward.amount
                    }
                    // Reward callback: called on GMA BG thread — caller decides thread.
                    rewardCallback.onUserEarnedReward(gmsRewardItem)
                }
            })
            Log.d(TAG, "Rewarded ad displayed (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        Log.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    override fun loadThenShow(activity: Activity, rewardCallback: com.google.android.gms.ads.OnUserEarnedRewardListener) {
        load()
    }

    private fun setupNextGenFullScreenCallback(ad: RewardedAd) {
        ad.adEventCallback = object : RewardedAdEventCallback {
            // All callbacks fire on GMA BG thread — callers decide their own threading.
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed (Next-Gen)")
                nextGenAd = null
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "Ad failed to show: ${error.message} (Next-Gen)")
                nextGenAd = null
                listener?.onAdFailedToShowFullScreenContent(error.toOzError())
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content (Next-Gen)")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad recorded an impression (Next-Gen)")
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                Log.d(TAG, "Ad was clicked (Next-Gen)")
                listener?.onAdClicked()
            }

            override fun onAdPaid(value: AdValue) {
                OzEventLogger.logPaidAdImpressionNextGen(
                    context,
                    value.valueMicros,
                    value.currencyCode,
                    adUnitId,
                    ad.getResponseInfo().adapterClassName ?: "unknown"
                )
            }
        }
    }

    override fun isAdLoaded(): Boolean {
        return nextGenAd != null
    }
}
