package com.oz.android.ads_core.admobs.interstitial

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.OzLoadingDialog

/**
 * Standard SDK implementation of Interstitial ads.
 */
class AdmobStandardInterstitial(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobInterstitial>? = null
) : AdmobBase<AdmobInterstitial>(context, adUnitId, listener), AdmobInterstitial {

    private var interstitialAd: InterstitialAd? = null
    private var loadTime: Long = 0

    companion object {
        private const val TAG = "AdmobStandardInterstitial"
        private const val AD_EXPIRATION_HOURS = 1L
    }

    override fun load() {
        if (interstitialAd != null) {
            Log.d(TAG, "Ad already loaded")
            return
        }

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    OzLoadingDialog.hideFullScreenLoadingDialog()
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    loadTime = System.currentTimeMillis()
                    interstitialAd?.onPaidEventListener = getOnPaidListener(interstitialAd!!.responseInfo)
                    listener?.onAdLoaded(this@AdmobStandardInterstitial)

                    setupFullScreenContentCallback(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    OzLoadingDialog.hideFullScreenLoadingDialog()
                    Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null

                    listener?.onAdFailedToLoad(adError.toOzError())
                }
            }
        )
    }

    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for interstitial ads")
    }

    override fun show(activity: Activity) {
        val currentAd = interstitialAd
        if (currentAd == null || isAdExpired()) {
            Log.w(TAG, "InterstitialAd is null or expired. Call load() first")
            return
        }
        currentAd.show(activity)
        listener?.onNextAction()
        Log.d(TAG, "Interstitial ad displayed")
    }

    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() is not supported on AdmobStandardInterstitial. Use OzAdmobIntersAd instead.")
    }

    override fun loadThenShow(activity: Activity, showOverlay: Boolean) {
        if (isAdLoaded()) {
            show(activity)
            return
        }

        if (showOverlay) {
            OzLoadingDialog.showFullScreenLoadingDialog(activity)
            Handler(Looper.getMainLooper()).postDelayed({
                OzLoadingDialog.hideFullScreenLoadingDialog()
            }, 10000L)
        }
        load()
    }

    private fun setupFullScreenContentCallback(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed")
                interstitialAd = null
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                interstitialAd = null
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
        return interstitialAd != null && !isAdExpired()
    }

    private fun isAdExpired(): Boolean {
        return (System.currentTimeMillis() - loadTime) >= AD_EXPIRATION_HOURS * 60L * 60L * 1000L
    }
}
