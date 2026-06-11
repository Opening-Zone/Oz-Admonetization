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
 * Class managing App Open ads from AdMob
 * Provides 3 main methods: load, show, and loadThenShow
 */
class AdmobAppOpen(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobAppOpen>? = null
) : AdmobBase<AdmobAppOpen>(context, adUnitId, listener) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var isShowingAd = false
    private var loadTime: Long = 0

    companion object {
        private const val TAG = "AdmobAppOpen"
        private const val AD_EXPIRATION_HOURS = 4L // App Open ads expire after 4 hours
    }

    /**
     * Load App Open ad
     * The ad will be loaded but not shown yet
     */
    override fun load() {
        // Request a new ad if one isn't already loaded or expired.
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

                    listener?.onAdLoaded(this@AdmobAppOpen)
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

    /**
     * Show App Open ad (implementation from interface)
     * Note: App Open requires an Activity, use show(activity: Activity) instead of this method
     */
    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for App Open ads")
    }

    /**
     * Show App Open ad
     * @param activity Activity to display the App Open ad
     */
    fun show(activity: Activity) {
        // If the app open ad is already showing, do not show the ad again.
        if (isShowingAd) {
            Log.d(TAG, "The app open ad is already showing.")
            return
        }

        // If the app open ad is not available yet, load it
        if (!isAdAvailable()) {
            Log.d(TAG, "The app open ad is not ready yet.")
            return
        }

        val currentAd = appOpenAd
        if (currentAd == null) {
            Log.w(TAG, "AppOpenAd is null. Call load() first")
            return
        }

        // Setup FullScreenContentCallback right before showing (Google's best practice)
        setupFullScreenContentCallback(currentAd)

        // Show the ad
        isShowingAd = true
        currentAd.show(activity)
        listener?.onNextAction()
        Log.d(TAG, "App Open ad displayed")
    }

    /**
     * Load and automatically show the ad when loading finishes (implementation from interface)
     * Note: App Open requires an Activity, use loadThenShow(activity: Activity) instead of this method
     */
    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() is not supported on AdmobAppOpen. Use OzAdmobAppOpenAd instead.")
    }

    /**
     * Load and automatically show the ad when loading finishes
     * @param activity Activity to display the App Open ad
     */
    fun loadThenShow(activity: Activity) {
        load()
    }

    /**
     * Check if ad was loaded less than n hours ago
     */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < (numMilliSecondsPerHour * numHours)
    }

    /**
     * Check if ad exists and can be shown (not expired)
     */
    fun isAdAvailable(): Boolean {
        return isLoaded && appOpenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRATION_HOURS)
    }

    /**
     * Setup FullScreenContentCallback cho App Open ad
     * Should be called right before showing the ad (Google's best practice)
     */
    private fun setupFullScreenContentCallback(ad: AppOpenAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed.
                Log.d(TAG, "Ad was dismissed")
                // Set the reference to null so isAdAvailable() returns false.
                appOpenAd = null
                isLoaded = false
                isShowingAd = false
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show.
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Set the reference to null so isAdAvailable() returns false.
                appOpenAd = null
                isLoaded = false
                isShowingAd = false
                listener?.onAdFailedToShowFullScreenContent(adError.toOzError())
            }

            override fun onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown.
                Log.d(TAG, "Ad showed fullscreen content")
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad.
                Log.d(TAG, "Ad recorded an impression")
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                // Called when ad is clicked.
                Log.d(TAG, "Ad was clicked")
                listener?.onAdClicked()
            }
        }
    }

    /**
     * Check if the ad has been loaded
     * @return true if the ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean {
        return isAdAvailable()
    }
}

