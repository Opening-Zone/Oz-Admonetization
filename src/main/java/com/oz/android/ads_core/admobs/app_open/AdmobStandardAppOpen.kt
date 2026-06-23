package com.oz.android.ads_core.admobs.app_open

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import java.util.Date

/**
 * Standard SDK implementation of App Open ads.
 */
class AdmobStandardAppOpen(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobAppOpen>? = null
) : AdmobBase<AdmobAppOpen>(context, adUnitId, listener), AdmobAppOpen {

    private var appOpenAd: AppOpenAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var isShowingAd = false
    private var loadTime: Long = 0

    companion object {
        private const val TAG = "AdmobStandardAppOpen"
        private const val AD_EXPIRATION_HOURS = 4L
    }

    override fun load() {
        if (adIsLoading || isAdAvailable()) {
            Log.d(TAG, "Ad already loading or available")
            return
        }

        adIsLoading = true

        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open ad loaded successfully")
                    appOpenAd = ad
                    appOpenAd?.onPaidEventListener = getOnPaidListener(appOpenAd!!.responseInfo)
                    isLoaded = true
                    adIsLoading = false
                    loadTime = Date().time

                    listener?.onAdLoaded(this@AdmobStandardAppOpen)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "App Open ad failed to load: ${adError.message}")
                    appOpenAd = null
                    isLoaded = false
                    adIsLoading = false

                    listener?.onAdFailedToLoad(adError.toOzError())
                    listener?.onNextAction()
                }
            }
        )
    }

    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for App Open ads")
    }

    override fun show(activity: Activity) {
        if (isShowingAd) {
            Log.d(TAG, "The app open ad is already showing.")
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "The app open ad is not ready yet.")
            return
        }

        val currentAd = appOpenAd ?: run {
            Log.w(TAG, "AppOpenAd is null. Call load() first")
            return
        }

        setupFullScreenContentCallback(currentAd)

        isShowingAd = true
        currentAd.show(activity)
        listener?.onNextAction()
        Log.d(TAG, "App Open ad displayed")
    }

    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() is not supported on AdmobStandardAppOpen. Use OzAdmobAppOpenAd instead.")
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
        return isLoaded && appOpenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRATION_HOURS)
    }

    private fun setupFullScreenContentCallback(ad: AppOpenAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed")
                appOpenAd = null
                isLoaded = false
                isShowingAd = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                appOpenAd = null
                isLoaded = false
                isShowingAd = false
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
        return isAdAvailable()
    }
}
