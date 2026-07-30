package com.oz.android.ads_impl.app_resume.admob

import android.app.Activity
import android.content.Context
import com.oz.android.utils.OzLog
import com.oz.android.ads_core.admobs.interstitial.AdmobInterstitial
import com.oz.android.ads_impl.app_resume.AppLifecycleAdManager
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.oz_ads.ads_overlay.admob.OzAdmobIntersAd
import com.oz.android.OzAdsManager
import com.oz.android.utils.OzLoadingDialog

/**
 * App Resume Ads Manager using Interstitial Ads
 *
 * Shows interstitial ads when the app returns from background to foreground.
 *
 * Usage:
 * ```kotlin
 * // In your Application class
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         val resumeAdsManager = AppResumeInterstitialManager.getInstance()
 *         resumeAdsManager.init(this)
 *         resumeAdsManager.setAdUnitId("ca-app-pub-xxxxx")
 *
 *         // Optional: Configure
 *         resumeAdsManager.setSplashActivity(SplashActivity::class.java)
 *         resumeAdsManager.disableAppResumeWithActivity(PaymentActivity::class.java)
 *     }
 * }
 * ```
 */
class AppResumeInterstitialManager private constructor() :
    AppLifecycleAdManager<AdmobInterstitial>() {

    private var adUnitId: String? = null
    private var adListener: OzAdListener<AdmobInterstitial>? = null

    private var interstitialAd: OzAdmobIntersAd? = null

    companion object {
        private const val TAG = "AppResumeInterstitial"

        @Volatile
        private var instance: AppResumeInterstitialManager? = null

        /**
         * Get singleton instance of AppResumeInterstitialManager
         */
        fun getInstance(): AppResumeInterstitialManager {
            return instance ?: synchronized(this) {
                instance ?: AppResumeInterstitialManager().also { instance = it }
            }
        }
    }

    /**
     * Set the Ad Unit ID for interstitial ads
     * Must be called before showing ads
     */
    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
        OzLog.d(TAG, "Ad Unit ID set: $adUnitId")
    }

    /**
     * Set custom ad listener for additional callbacks
     */
    fun setAdListener(listener: OzAdListener<AdmobInterstitial>) {
        this.adListener = listener
    }

    /**
     * Load interstitial ad
     */
    override fun loadAd(context: Context) {
        if (!adUnitId.isNullOrBlank()) {
            interstitialAd = OzAdmobIntersAd(context).apply {
                setAdUnitId(TAG, adUnitId!!)
                listener = createAdListener()
                loadAd(true)
            }
        }
    }

    /**
     * Show interstitial ad
     */
    override fun showAd(activity: Activity, onShowComplete: () -> Unit) {
        if (OzAdsManager.getInstance().isFullScreenAdShowing.value || OzLoadingDialog.isShowing()) {
            OzLog.d(TAG, "Another fullscreen ad or loading dialog is showing. Skipping app resume ad.")
            onShowComplete()
            return
        }

        if (isAdReady()) {
            interstitialAd?.show(activity)
        } else {
            OzLog.d(TAG, "Ad is not ready or expired. Calling loadThenShow to reload and show.")
            interstitialAd?.loadThenShow(activity, showOverlay = true)
        }
        onShowComplete()
    }

    /**
     * Create ad listener for load callbacks
     */
    private fun createAdListener(): OzAdListener<AdmobInterstitial> {
        return object : OzAdListener<AdmobInterstitial>() {
            override fun onAdLoaded(ad: AdmobInterstitial) {
                OzLog.d(TAG, "Interstitial ad loaded successfully")
                onAdLoadedSuccess(ad)
                adListener?.onAdLoaded(ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                OzLog.e(TAG, "Failed to load interstitial: ${error.message}")
                currentAd = null
                adListener?.onAdFailedToLoad(error)
            }

            override fun onAdClicked() {
                OzLog.d(TAG, "Interstitial ad clicked - disabling next resume ad")
                disableAdResumeByClickAction()
                adListener?.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                OzLog.d(TAG, "Interstitial ad dismissed")
                adListener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                OzLog.e(TAG, "Failed to show interstitial: ${adError.message}")
                adListener?.onAdFailedToShowFullScreenContent(adError)
            }
        }
    }

    /**
     * Check if interstitial ad is ready to show
     */
    fun isAdReady(): Boolean {
        return currentAd?.isAdLoaded() == true
    }

    /**
     * Manually show ad if available (outside of lifecycle events)
     */
    fun showAdManually(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (isAdReady()) {
            showAd(activity) {
                onComplete?.invoke()
            }
        } else {
            OzLog.w(TAG, "Ad not ready to show manually")
            onComplete?.invoke()
        }
    }
}

