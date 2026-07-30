package com.oz.android.ads_core.admobs.app_open

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
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger
import java.util.Date

class AdmobNextAppOpen(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobAppOpen>? = null
) : AdmobBase<AdmobAppOpen>(context, adUnitId, listener), AdmobAppOpen {

    private var nextGenAd: AppOpenAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var isShowingAd = false
    private var loadTime: Long = 0

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobNextAppOpen"
        private const val AD_EXPIRATION_HOURS = 4L
    }

    override fun load() {
        if (adIsLoading || isAdAvailable()) {
            OzLog.d(TAG, "Ad already loading or available (Next-Gen)")
            return
        }

        adIsLoading = true

        AppOpenAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    // ── Runs on GMA background thread ──
                    // State updates: no UI, safe on BG.
                    OzLog.d(TAG, "App Open ad loaded successfully (Next-Gen)")
                    nextGenAd = ad
                    isLoaded = true
                    adIsLoading = false
                    loadTime = Date().time

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdLoaded(this@AdmobNextAppOpen)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // ── Runs on GMA background thread ──
                    // State cleanup: no UI, stays on BG.
                    OzLog.e(TAG, "App Open ad failed to load: ${error.message} (Next-Gen)")
                    nextGenAd = null
                    isLoaded = false
                    adIsLoading = false

                    // Listener callbacks: called on GMA BG thread — callers decide their own thread.
                    listener?.onAdFailedToLoad(error.toOzError())
                    listener?.onNextAction()
                }
            }
        )
    }

    override fun show() {
        OzLog.w(TAG, "show() called without activity. Use show(activity: Activity) for App Open ads")
    }

    override fun show(activity: Activity) {
        val showRunnable = Runnable {
            if (isShowingAd) {
                OzLog.d(TAG, "The app open ad is already showing (Next-Gen).")
                return@Runnable
            }

            if (!isAdAvailable()) {
                OzLog.d(TAG, "The app open ad is not ready yet (Next-Gen).")
                return@Runnable
            }

            val currentAd = nextGenAd ?: run {
                OzLog.w(TAG, "AppOpenAd is null (Next-Gen). Call load() first")
                return@Runnable
            }

            setupNextGenFullScreenCallback(currentAd)

            isShowingAd = true
            currentAd.show(activity)
            listener?.onNextAction()
            OzLog.d(TAG, "App Open ad displayed (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        OzLog.w(TAG, "loadThenShow() is not supported on AdmobNextAppOpen.")
    }

    override fun loadThenShow(activity: Activity) {
        load()
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < (numMilliSecondsPerHour * numHours)
    }

    override fun isAdAvailable(): Boolean {
        return isLoaded && nextGenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRATION_HOURS)
    }

    private fun setupNextGenFullScreenCallback(ad: AppOpenAd) {
        ad.adEventCallback = object : AppOpenAdEventCallback {
            // All callbacks fire on GMA BG thread — callers decide their own threading.
            override fun onAdDismissedFullScreenContent() {
                OzLog.d(TAG, "Ad was dismissed (Next-Gen)")
                nextGenAd = null
                isLoaded = false
                isShowingAd = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                OzLog.e(TAG, "Ad failed to show: ${error.message} (Next-Gen)")
                nextGenAd = null
                isLoaded = false
                isShowingAd = false
                listener?.onAdFailedToShowFullScreenContent(error.toOzError())
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
        return isAdAvailable()
    }
}
