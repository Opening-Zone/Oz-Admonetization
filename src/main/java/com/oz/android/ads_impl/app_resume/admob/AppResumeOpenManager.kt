package com.oz.android.ads_impl.app_resume.admob

import android.app.Activity
import android.content.Context
import com.oz.android.utils.OzLog
import com.oz.android.ads_core.admobs.app_open.AdmobAppOpen
import com.oz.android.ads_impl.app_resume.AppLifecycleAdManager
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.oz_ads.ads_overlay.admob.OzAdmobOpenAd
import com.oz.android.OzAdsManager
import com.oz.android.utils.OzLoadingDialog

/**
 * App Resume Ads Manager using App Open Ads
 *
 * Shows App Open ads when the app returns from background to foreground.
 * This is the **recommended** implementation for app resume ads — App Open ads are
 * optimised for the resume use-case and generally produce higher revenue.
 *
 * Usage:
 * ```kotlin
 * // In your Application class
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         val resumeManager = AppResumeOpenManager.getInstance()
 *         resumeManager.init(this)
 *         resumeManager.setAdUnitId("ca-app-pub-xxxxx")
 *
 *         // Optional: Configure
 *         resumeManager.setSplashActivity(SplashActivity::class.java)
 *         resumeManager.disableAppResumeWithActivity(PaymentActivity::class.java)
 *     }
 * }
 * ```
 *
 * Quick-switch tip:
 * To toggle between App Open and Interstitial resume ads at the call site, keep both
 * managers initialised and flip which one has [isAppResumeEnabled] set to `true`.
 */
class AppResumeOpenManager private constructor() :
    AppLifecycleAdManager<AdmobAppOpen>() {

    private var adUnitId: String? = null
    private var adListener: OzAdListener<AdmobAppOpen>? = null

    private var openAd: OzAdmobOpenAd? = null

    companion object {
        private const val TAG = "AppResumeOpen"

        @Volatile
        private var instance: AppResumeOpenManager? = null

        /**
         * Get singleton instance of AppResumeOpenManager
         */
        fun getInstance(): AppResumeOpenManager {
            return instance ?: synchronized(this) {
                instance ?: AppResumeOpenManager().also { instance = it }
            }
        }
    }

    /**
     * Set the Ad Unit ID for App Open ads.
     * Must be called before showing ads.
     */
    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
        OzLog.d(TAG, "Ad Unit ID set: $adUnitId")
    }

    /**
     * Set custom ad listener for additional callbacks.
     */
    fun setAdListener(listener: OzAdListener<AdmobAppOpen>) {
        this.adListener = listener
    }

    /**
     * Load App Open ad
     */
    override fun loadAd(context: Context) {
        if (!adUnitId.isNullOrBlank()) {
            openAd = OzAdmobOpenAd(context).apply {
                setAdUnitId(TAG, adUnitId!!)
                listener = createAdListener()
                loadAd(true)
            }
        }
    }

    /**
     * Show App Open ad
     */
    override fun showAd(activity: Activity, onShowComplete: () -> Unit) {
        if (OzAdsManager.getInstance().isFullScreenAdShowing.value || OzLoadingDialog.isShowing()) {
            OzLog.d(TAG, "Another fullscreen ad or loading dialog is showing. Skipping app resume ad.")
            onShowComplete()
            return
        }

        if (isAdReady()) {
            openAd?.show(activity)
        } else {
            OzLog.d(TAG, "Ad is not ready or expired. Calling loadThenShow to reload and show.")
            openAd?.loadThenShow(activity)
        }
        onShowComplete()
    }

    /**
     * Create ad listener for load/show callbacks
     */
    private fun createAdListener(): OzAdListener<AdmobAppOpen> {
        return object : OzAdListener<AdmobAppOpen>() {
            override fun onAdLoaded(ad: AdmobAppOpen) {
                OzLog.d(TAG, "App Open ad loaded successfully")
                onAdLoadedSuccess(ad)
                adListener?.onAdLoaded(ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                OzLog.e(TAG, "Failed to load App Open ad: ${error.message}")
                currentAd = null
                adListener?.onAdFailedToLoad(error)
            }

            override fun onAdClicked() {
                OzLog.d(TAG, "App Open ad clicked - disabling next resume ad")
                disableAdResumeByClickAction()
                adListener?.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                OzLog.d(TAG, "App Open ad dismissed")
                adListener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                OzLog.e(TAG, "Failed to show App Open ad: ${adError.message}")
                adListener?.onAdFailedToShowFullScreenContent(adError)
            }
        }
    }

    /**
     * Check if App Open ad is ready to show
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
