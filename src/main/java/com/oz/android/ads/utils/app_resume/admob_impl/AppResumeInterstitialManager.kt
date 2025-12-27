package com.oz.android.ads.utils.app_resume.admob_impl

import android.app.Activity
import android.content.Context
import android.util.Log
import com.oz.android.ads.network.admobs.ads_component.interstitial.AdmobInterstitial
import com.oz.android.ads.utils.app_resume.AppLifecycleAdManager
import com.oz.android.ads.utils.listener.OzAdError
import com.oz.android.ads.utils.listener.OzAdListener
import com.oz.android.wrapper.OzAdmobIntersAd

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
        Log.d(TAG, "Ad Unit ID set: $adUnitId")
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
                listener = object : OzAdListener<AdmobInterstitial>() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                    }

                    override fun onAdFailedToShowFullScreenContent(error: OzAdError) {
                        Log.e(TAG, "Failed to show interstitial: ${error.message}")
                    }
                }
                loadAd(true)
            }
        }
    }

    /**
     * Show interstitial ad
     */
    override fun showAd(activity: Activity, onShowComplete: () -> Unit) {
        interstitialAd?.show(activity)
        onShowComplete()
    }

    /**
     * Create ad listener for load callbacks
     */
    private fun createAdListener(): OzAdListener<AdmobInterstitial> {
        return object : OzAdListener<AdmobInterstitial>() {
            override fun onAdLoaded(ad: AdmobInterstitial) {
                Log.d(TAG, "Interstitial ad loaded successfully")
                onAdLoadedSuccess(ad)
                adListener?.onAdLoaded(ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                Log.e(TAG, "Failed to load interstitial: ${error.message}")
                currentAd = null
                adListener?.onAdFailedToLoad(error)
            }

            override fun onAdClicked() {
                Log.d(TAG, "Interstitial ad clicked - disabling next resume ad")
                disableAdResumeByClickAction()
                adListener?.onAdClicked()
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
            Log.w(TAG, "Ad not ready to show manually")
            onComplete?.invoke()
        }
    }
}

