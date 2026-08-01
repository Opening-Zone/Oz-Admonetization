package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
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
            OzLog.d(TAG, "Ad already loaded (Next-Gen)")
            return
        }

        OzEventLogger.logAdRequest(context, adUnitId, "reward_nextgen")

        RewardedAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    // ── Runs on GMA background thread ──
                    // State updates: no UI, safe on BG.
                    OzLog.d(TAG, "Rewarded ad loaded successfully (Next-Gen)")
                    nextGenAd = ad

                    // setupNextGenFullScreenCallback only sets a property, safe on BG.
                    setupNextGenFullScreenCallback(ad)

                    OzEventLogger.logAdLoadSuccess(context, adUnitId, "reward_nextgen")

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdLoaded(this@AdmobNextReward)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // ── Runs on GMA background thread ──
                    // State cleanup: no UI, stays on BG.
                    OzLog.e(TAG, "Rewarded ad failed to load: ${error.message} (Next-Gen)")
                    nextGenAd = null

                    val ozError = error.toOzError()
                    OzEventLogger.logAdLoadFailed(context, adUnitId, "reward_nextgen", ozError.code, ozError.message)

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdFailedToLoad(ozError)
                }
            }
        )
    }

    override fun show() {
        OzLog.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    override fun show(activity: Activity, rewardCallback: com.google.android.gms.ads.OnUserEarnedRewardListener) {
        val showRunnable = Runnable {
            val currentAd = nextGenAd
            if (currentAd == null) {
                OzLog.w(TAG, "RewardedAd is null (Next-Gen). Call load() first")
                OzEventLogger.logAdSkip(context, adUnitId, "reward_nextgen", "ad_null")
                return@Runnable
            }

            OzEventLogger.logAdShowCalled(context, adUnitId, "reward_nextgen")
            currentAd.show(activity, object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    val gmsRewardItem = object : com.google.android.gms.ads.rewarded.RewardItem {
                        override val type: String = reward.type
                        override val amount: Int = reward.amount
                    }
                    OzEventLogger.logAdRewardEarned(context, adUnitId, reward.type, reward.amount)
                    // Reward callback: called on GMA BG thread — caller decides thread.
                    rewardCallback.onUserEarnedReward(gmsRewardItem)
                }
            })
            OzLog.d(TAG, "Rewarded ad displayed (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        OzLog.w(
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
                OzLog.d(TAG, "Ad was dismissed (Next-Gen)")
                nextGenAd = null
                OzEventLogger.logAdDismissed(context, adUnitId, "reward_nextgen")
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                OzLog.e(TAG, "Ad failed to show: ${error.message} (Next-Gen)")
                nextGenAd = null
                val ozError = error.toOzError()
                OzEventLogger.logAdShowFailed(context, adUnitId, "reward_nextgen", ozError.code, ozError.message)
                listener?.onAdFailedToShowFullScreenContent(ozError)
            }

            override fun onAdShowedFullScreenContent() {
                OzLog.d(TAG, "Ad showed fullscreen content (Next-Gen)")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                OzLog.d(TAG, "Ad recorded an impression (Next-Gen)")
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                OzLog.d(TAG, "Ad was clicked (Next-Gen)")
                OzEventLogger.logAdClickedCustom(context, adUnitId, "reward_nextgen")
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
