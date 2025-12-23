package com.oz.android.ads.oz_ads.ads_component.ads_inline.admob

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAdView
import com.oz.android.ads.network.admobs.ads_component.OzAdmobListener
import com.oz.android.ads.network.admobs.ads_component.native_advanced.AdmobNativeAdvanced
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.ads_inline.InlineAds
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation cụ thể của InlineAds cho AdMob Native
 * Chỉ xử lý NATIVE format
 */
class OzAdmobNativeAd @JvmOverloads constructor(
    context: Context,
) : InlineAds<AdmobNativeAdvanced>(context) {

    companion object {
        private const val TAG = "OzAdmobNativeAd"
    }

    // Map key -> adUnitId
    private val adUnitIds = ConcurrentHashMap<String, String>()

    // Map key -> NativeAdView
    private val nativeAdViews = ConcurrentHashMap<String, NativeAdView>()

    init {
        setAdsFormat(AdsFormat.NATIVE)
    }

    /**
     * Set ad unit ID cho một key
     * @param key Key để identify placement
     * @param adUnitId Ad unit ID từ AdMob
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        adUnitIds[key] = adUnitId
        Log.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Set NativeAdView cho một key
     * @param key Key để identify placement
     * @param nativeAdView NativeAdView đã được setup
     */
    fun setNativeAdView(key: String, nativeAdView: NativeAdView) {
        nativeAdViews[key] = nativeAdView
        Log.d(TAG, "NativeAdView set for key: $key")
    }

    /**
     * Get ad unit ID cho một key
     * @param key Key để identify placement
     * @return Ad unit ID, null nếu chưa được set
     */
    fun getAdUnitId(key: String): String? = adUnitIds[key]

    override fun createAd(key: String): AdmobNativeAdvanced? {
        val adUnitId = adUnitIds[key]
        if (adUnitId.isNullOrBlank()) {
            Log.e(TAG, "Ad unit ID not set for key: $key")
            return null
        }

        val listener = object : OzAdmobListener<AdmobNativeAdvanced>() {
            override fun onAdLoaded(ad: AdmobNativeAdvanced) {
                this@OzAdmobNativeAd.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                this@OzAdmobNativeAd.onAdLoadFailed(key, error.message)
            }
        }

        return AdmobNativeAdvanced(context, adUnitId, listener)
    }

    override fun onLoadAd(key: String, ad: AdmobNativeAdvanced) {
        Log.d(TAG, "Loading native ad for key: $key")
        ad.load()
    }

    override fun onShowAds(key: String, ad: AdmobNativeAdvanced) {
        val nativeAdView = nativeAdViews[key]
        if (nativeAdView == null) {
            Log.e(TAG, "NativeAdView not set for key: $key")
            onAdShowFailed(key, "NativeAdView not set")
            return
        }

        Log.d(TAG, "Showing native ad for key: $key")
        // Show native ad in this ViewGroup
        ad.show(this, nativeAdView)
        // Notify parent that the ad has been shown
        onAdShown(key)
    }

    override fun hideAds() {
        removeAllViews()
        Log.d(TAG, "Native ads hidden")
    }

    override fun destroyAd(ad: AdmobNativeAdvanced) {
        Log.d(TAG, "Destroying native ad")
        ad.destroy()
    }

    override fun onPauseAd() {
        // Native ads generally don't need explicit pause handling
        Log.d(TAG, "Pausing native ads (no-op)")
    }

    override fun onResumeAd() {
        // Native ads generally don't need explicit resume handling
        Log.d(TAG, "Resuming native ads (no-op)")
    }
}
