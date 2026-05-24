package com.oz.android.ads.network.admobs.ads_component.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.ads.network.admobs.ads_component.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.OzLoadingDialog

/**
 * Class quản lý interstitial ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AdmobInterstitial(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobInterstitial>? = null
) : AdmobBase<AdmobInterstitial>(context, adUnitId, listener) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var loadTime: Long = 0

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
                    OzLoadingDialog.hideFullScreenLoadingDialog()
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoaded = true
                    adIsLoading = false
                    loadTime = System.currentTimeMillis()
                    interstitialAd?.onPaidEventListener = getOnPaidListener(interstitialAd!!.responseInfo)
                    listener?.onAdLoaded(this@AdmobInterstitial)

                    // Setup FullScreenContentCallback
                    setupFullScreenContentCallback(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    OzLoadingDialog.hideFullScreenLoadingDialog()
                    Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                    isLoaded = false
                    adIsLoading = false

                    listener?.onAdFailedToLoad(adError.toOzError())
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
        if (currentAd == null || isAdExpired()) {
            Log.w(TAG, "InterstitialAd is null or expired. Call load() first")
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet.")
            return
        }

        // Show the ad
        currentAd.show(activity)
        listener?.onNextAction()
        Log.d(TAG, "Interstitial ad displayed")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Interstitial cần Activity, sử dụng loadThenShow(activity: Activity) thay vì method này
     */
    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() is not supported on AdmobInterstitial. Use OzAdmobIntersAd instead.")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param activity Activity để hiển thị interstitial ad
     * @param showOverlay Hiển thị overlay quay vòng chờ load (tránh user chạm xung quanh)
     */
    fun loadThenShow(activity: Activity, showOverlay: Boolean = false) {
        if (isAdLoaded()) {
            show(activity)
            return
        }

        if (showOverlay) {
            OzLoadingDialog.showFullScreenLoadingDialog(activity)
            
            // Safety timeout for the loader overlay in case AdMob hangs
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                OzLoadingDialog.hideFullScreenLoadingDialog()
            }, 10000L)
        }
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
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show.
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                // Don't forget to set the ad reference to null so you
                // don't show the ad a second time.
                interstitialAd = null
                isLoaded = false
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
     * Kiểm tra xem ad đã được load và chưa bị hết hạn chưa (hết hạn sau 1 tiếng)
     * @return true nếu ad đã load và còn hợp lệ, false nếu chưa hoặc đã hết hạn
     */
    fun isAdLoaded(): Boolean {
        return isLoaded && interstitialAd != null && !isAdExpired()
    }

    /**
     * Kiểm tra xem ad đã hết hạn chưa (AdMob interstitial hết hạn sau 1 tiếng)
     */
    private fun isAdExpired(): Boolean {
        return (System.currentTimeMillis() - loadTime) >= 1L * 60L * 60L * 1000L
    }
}

