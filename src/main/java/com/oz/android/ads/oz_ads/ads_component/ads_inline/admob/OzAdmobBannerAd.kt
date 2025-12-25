package com.oz.android.ads.oz_ads.ads_component.ads_inline.admob

import AdmobBanner
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.RestrictTo
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.oz.android.ads.network.admobs.ads_component.interstitial.AdmobInterstitial
import com.oz.android.wrapper.OzAdListener
import com.oz.android.ads.oz_ads.ads_component.ads_inline.InlineAds
import com.oz.android.wrapper.OzAdError

/**
 * Implementation cụ thể của InlineAds cho AdMob Banner
 * Chỉ xử lý BANNER format
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
open class OzAdmobBannerAd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : InlineAds<AdmobBanner>(context, attrs, defStyleAttr) {

    private var banner:AdmobBanner = AdmobBanner(context, "", null)

    companion object {
        private const val TAG = "OzAdmobBannerAd"
    }

    /**
     * Set ad unit ID cho một key
     * @param key Key để identify placement
     * @param adUnitId Ad unit ID từ AdMob
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        banner.adUnitId = adUnitId
        Log.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    override fun createAd(key: String): AdmobBanner? {
        val adUnitId = banner.adUnitId
        if (adUnitId.isBlank()) {
            Log.e(TAG, "Ad unit ID not set for key: $key")
            return null
        }

        val bannerListener = object : OzAdListener<AdmobBanner>() {
            override fun onAdLoaded(ad: AdmobBanner) {
                // Pass the loaded ad object to the parent
                this@OzAdmobBannerAd.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                // Notify parent about the failure
                this@OzAdmobBannerAd.onAdLoadFailed(key, error.message)
            }

            override fun onAdClicked() {
                // Bridge to OzAds.onAdClicked()
                this@OzAdmobBannerAd.onAdClicked(key)
            }
        }

        val mergedListener = bannerListener.merge(listener)

        return AdmobBanner(context, adUnitId, mergedListener)
    }

    override fun onLoadAd(key: String, ad: AdmobBanner) {
        Log.d(TAG, "Loading banner ad for key: $key")
        ad.load()
    }

    override fun setShimmerSize(key: String) {
        // Calculate the height of the adaptive banner (assuming 360 width as per AdmobBanner implementation)
        // If we could access the specific width used in AdmobBanner, that would be better, but 360 is common standard
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 360)
        val heightPx = adSize.getHeightInPixels(context)
        
        shimmerLayout?.let { layout ->
            layout.layoutParams.height = heightPx
            layout.requestLayout()
        }
    }

    override fun onShowAds(key: String, ad: AdmobBanner) {
        Log.d(TAG, "Showing banner ad for key: $key")
        // Show banner in this ViewGroup
        ad.show(this)
        // Notify parent that the ad has been shown
        onAdShown(key)
    }

    override fun hideAds() {
        // Remove all child views to hide the ad
        removeAllViews()
        Log.d(TAG, "Banner ads hidden")
    }

    override fun destroyAd(ad: AdmobBanner) {
        Log.d(TAG, "Destroying banner ad")
        ad.destroy()
    }

    override fun onPauseAd() {
        Log.d(TAG, "Pausing all banner ads")
    }

    override fun onResumeAd() {
        Log.d(TAG, "Resuming all banner ads")
    }
}




