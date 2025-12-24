package com.oz.android.ads.network.admobs.ads_component.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.ads.network.admobs.ads_component.OzAdmobListener

/**
 * Class quản lý interstitial ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
class AdmobInterstitial(
    context: Context,
    adUnitId: String,
    listener: OzAdmobListener<AdmobInterstitial>? = null
) : AdmobBase<AdmobInterstitial>(context, adUnitId, listener) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var pendingActivity: Activity? = null

    companion object {
        private const val TAG = "AdmobInterstitial"
    }

    /**
     * Load quảng cáo interstitial
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    override fun load() {
        // Request a new ad if one isn't already loaded.
        if (adIsLoading || interstitialAd != null) {
            Log.d(TAG, "Ad already loading or loaded")
            return
        }

        adIsLoading = true

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoaded = true
                    adIsLoading = false

                    listener?.onAdLoaded(this@AdmobInterstitial)

                    // Setup FullScreenContentCallback
                    setupFullScreenContentCallback(ad)

                    // Nếu có activity đang chờ, tự động hiển thị
                    pendingActivity?.let { activity ->
                        show(activity)
                        pendingActivity = null
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                    isLoaded = false
                    adIsLoading = false
                    pendingActivity = null
                    
                    listener?.onAdFailedToLoad(adError)
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo interstitial (implementation từ interface)
     * Lưu ý: Interstitial cần Activity, sử dụng show(activity: Activity) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without activity. Use show(activity: Activity) for interstitial ads")
    }

    /**
     * Hiển thị quảng cáo interstitial
     * @param activity Activity để hiển thị interstitial ad
     */
    fun show(activity: Activity) {
        val currentAd = interstitialAd
        if (currentAd == null) {
            Log.w(TAG, "InterstitialAd is null. Call load() first")
            pendingActivity = activity
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet. It will be shown automatically when loaded")
            pendingActivity = activity
            return
        }

        // Show the ad
        currentAd.show(activity)
        Log.d(TAG, "Interstitial ad displayed")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Interstitial cần Activity, sử dụng loadThenShow(activity: Activity) thay vì method này
     */
    override fun loadThenShow() {
        if (pendingActivity != null) {
            loadThenShow(pendingActivity!!)
        } else {
            Log.w(TAG, "loadThenShow() called without activity. Use loadThenShow(activity: Activity) for interstitial ads")
        }
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param activity Activity để hiển thị interstitial ad
     */
    fun loadThenShow(activity: Activity) {
        pendingActivity = activity
        load()
    }

    /**
     * Setup FullScreenContentCallback cho interstitial ad
     */
    private fun setupFullScreenContentCallback(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed.
                Log.d(TAG, "Ad was dismissed")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                interstitialAd = null
                isLoaded = false
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show.
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                interstitialAd = null
                isLoaded = false
            }

            override fun onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown.
                Log.d(TAG, "Ad showed fullscreen content")
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
        return isLoaded && interstitialAd != null
    }
}
