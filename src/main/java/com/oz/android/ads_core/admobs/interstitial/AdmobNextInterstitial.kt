package com.oz.android.ads_core.admobs.interstitial

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
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.OzLoadingDialog
import com.oz.android.utils.event.OzEventLogger

class AdmobNextInterstitial(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobInterstitial>? = null
) : AdmobBase<AdmobInterstitial>(context, adUnitId, listener), AdmobInterstitial {

    private var nextGenAd: InterstitialAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var loadTime: Long = 0

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobNextInterstitial"
    }

    override fun load() {
        if (adIsLoading || nextGenAd != null) {
            Log.d(TAG, "Ad already loading or loaded (Next-Gen)")
            return
        }

        adIsLoading = true

        InterstitialAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    // ── Runs on GMA background thread ──
                    // State updates: no UI, safe on BG.
                    Log.d(TAG, "Interstitial ad loaded successfully (Next-Gen)")
                    nextGenAd = ad
                    isLoaded = true
                    adIsLoading = false
                    loadTime = System.currentTimeMillis()

                    // setupNextGenFullScreenCallback only sets a property, safe on BG.
                    setupNextGenFullScreenCallback(ad)

                    // OzLoadingDialog is library-owned UI — must be on main.
                    mainHandler.post { OzLoadingDialog.hideFullScreenLoadingDialog() }
                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdLoaded(this@AdmobNextInterstitial)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // ── Runs on GMA background thread ──
                    // State cleanup: no UI, stays on BG.
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message} (Next-Gen)")
                    nextGenAd = null
                    isLoaded = false
                    adIsLoading = false

                    // OzLoadingDialog is library-owned UI — must be on main.
                    mainHandler.post { OzLoadingDialog.hideFullScreenLoadingDialog() }
                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdFailedToLoad(error.toOzError())
                }
            }
        )
    }

    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for interstitial ads")
    }

    override fun show(activity: Activity) {
        val showRunnable = Runnable {
            val currentAd = nextGenAd
            if (currentAd == null || isAdExpired()) {
                Log.w(TAG, "InterstitialAd is null or expired (Next-Gen). Call load() first")
                return@Runnable
            }
            if (!isLoaded) {
                Log.w(TAG, "Ad not loaded yet (Next-Gen).")
                return@Runnable
            }
            currentAd.show(activity)
            listener?.onNextAction()
            Log.d(TAG, "Interstitial ad displayed (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() is not supported on AdmobNextInterstitial.")
    }

    override fun loadThenShow(activity: Activity, showOverlay: Boolean) {
        if (isAdLoaded()) {
            show(activity)
            return
        }

        if (showOverlay) {
            OzLoadingDialog.showFullScreenLoadingDialog(activity)

            // Timeout safety: hide dialog after 10s on main thread (UI op).
            mainHandler.postDelayed({
                OzLoadingDialog.hideFullScreenLoadingDialog()
            }, 10000L)
        }
        load()
    }

    private fun setupNextGenFullScreenCallback(ad: InterstitialAd) {
        ad.adEventCallback = object : InterstitialAdEventCallback {
            // All callbacks fire on GMA BG thread — callers decide their own threading.
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed (Next-Gen)")
                nextGenAd = null
                isLoaded = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "Ad failed to show: ${error.message} (Next-Gen)")
                nextGenAd = null
                isLoaded = false
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
        return isLoaded && nextGenAd != null && !isAdExpired()
    }

    private fun isAdExpired(): Boolean {
        return (System.currentTimeMillis() - loadTime) >= 1L * 60L * 60L * 1000L
    }
}
