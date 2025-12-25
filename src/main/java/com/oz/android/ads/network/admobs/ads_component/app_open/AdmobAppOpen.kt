package com.oz.android.ads.network.admobs.ads_component.app_open

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.ads.network.admobs.ads_component.toOzError
import com.oz.android.wrapper.OzAdListener
import java.util.Date

/**
 * Class quản lý App Open ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AdmobAppOpen(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobAppOpen>? = null
) : AdmobBase<AdmobAppOpen>(context, adUnitId, listener) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var isShowingAd = false
    private var pendingActivity: Activity? = null
    private var loadTime: Long = 0

    companion object {
        private const val TAG = "AdmobAppOpen"
        private const val AD_EXPIRATION_HOURS = 4L // App Open ads expire after 4 hours
    }

    /**
     * Load quảng cáo App Open
     * Quảng cáo sẽ được load nhưng chưa hiển thị
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
                    isLoaded = true
                    adIsLoading = false
                    loadTime = Date().time

                    listener?.onAdLoaded(this@AdmobAppOpen)

                    // Note: FullScreenContentCallback is set up in show() method, not here
                    // This follows Google's best practice - callback should be set right before showing

                    // Nếu có activity đang chờ, tự động hiển thị
                    pendingActivity?.let { activity ->
                        show(activity)
                        pendingActivity = null
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "App Open ad failed to load: ${adError.message}")
                    appOpenAd = null
                    isLoaded = false
                    adIsLoading = false
                    pendingActivity = null

                    listener?.onAdFailedToLoad(adError.toOzError())
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo App Open (implementation từ interface)
     * Lưu ý: App Open cần Activity, sử dụng show(activity: Activity) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for App Open ads")
    }

    /**
     * Hiển thị quảng cáo App Open
     * @param activity Activity để hiển thị App Open ad
     */
    fun show(activity: Activity) {
        // If the app open ad is already showing, do not show the ad again.
        if (isShowingAd) {
            Log.d(TAG, "The app open ad is already showing.")
            return
        }

        // If the app open ad is not available yet, load it
        if (!isAdAvailable()) {
            Log.d(TAG, "The app open ad is not ready yet. Loading...")
            pendingActivity = activity
            load()
            return
        }

        val currentAd = appOpenAd
        if (currentAd == null) {
            Log.w(TAG, "AppOpenAd is null. Call load() first")
            pendingActivity = activity
            load()
            return
        }

        // Setup FullScreenContentCallback right before showing (Google's best practice)
        setupFullScreenContentCallback(currentAd)

        // Show the ad
        isShowingAd = true
        currentAd.show(activity)
        Log.d(TAG, "App Open ad displayed")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: App Open cần Activity, sử dụng loadThenShow(activity: Activity) thay vì method này
     */
    override fun loadThenShow() {
        if (pendingActivity != null) {
            loadThenShow(pendingActivity!!)
        } else {
            Log.w(TAG, "loadThenShow() called without activity. Use loadThenShow(activity: Activity) for App Open ads")
        }
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param activity Activity để hiển thị App Open ad
     */
    fun loadThenShow(activity: Activity) {
        pendingActivity = activity
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
     * Kiểm tra xem ad đã được load chưa
     * @return true nếu ad đã load, false nếu chưa
     */
    fun isAdLoaded(): Boolean {
        return isAdAvailable()
    }
}

