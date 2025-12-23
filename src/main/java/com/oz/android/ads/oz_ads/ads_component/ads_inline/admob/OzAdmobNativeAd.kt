package com.oz.android.ads.oz_ads.ads_component.ads_inline.admob

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import com.oz.android.ads.network.admobs.ads_component.OzAdmobListener
import com.oz.android.ads.network.admobs.ads_component.native_advanced.AdmobNativeAdvanced
import com.oz.android.ads.oz_ads.ads_component.AdsFormat
import com.oz.android.ads.oz_ads.ads_component.ads_inline.InlineAds
import java.util.concurrent.ConcurrentHashMap

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

    // Map key -> Layout ID
    private var layoutId = 0

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
     * Set Layout Resource ID.
     * NativeAdView sẽ được inflate từ layout XML này.
     * @param layoutId Layout Resource ID
     */
    fun setLayoutId(layoutId: Int) {
        this.layoutId = layoutId
        Log.d(TAG, "Layout ID set: $layoutId")
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
        var nativeAdView = nativeAdViews[key]
        
        // Nếu chưa có NativeAdView, kiểm tra xem có layoutId không
        if (nativeAdView == null) {
            val resId = layoutId
            if (resId != null) {
                Log.d(TAG, "Inflating NativeAdView from layout ID: $resId")
                try {
                    val inflatedView = LayoutInflater.from(context).inflate(resId, null)
                    if (inflatedView is NativeAdView) {
                        nativeAdView = inflatedView
                        bindStandardViews(nativeAdView)
                        // Cache lại để dùng cho lần sau
                        nativeAdViews[key] = nativeAdView
                    } else {
                        Log.e(TAG, "Inflated view is not a NativeAdView")
                        onAdShowFailed(key, "Inflated view is not a NativeAdView")
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to inflate layout: ${e.message}")
                    onAdShowFailed(key, "Failed to inflate layout: ${e.message}")
                    return
                }
            }
        }

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

    /**
     * Bind các view con trong NativeAdView dựa trên ID chuẩn (tương thích với layout_native_large.xml)
     */
    private fun bindStandardViews(nativeAdView: NativeAdView) {
        // Map các ID từ layout XML vào properties của NativeAdView
        // Sử dụng identifier string để tìm ID resource
        
        // Headline
        findIdAndSet(nativeAdView, "ad_headline") { view -> nativeAdView.headlineView = view }
        
        // Body
        findIdAndSet(nativeAdView, "ad_body") { view -> nativeAdView.bodyView = view }
        
        // Call To Action
        findIdAndSet(nativeAdView, "ad_call_to_action") { view -> nativeAdView.callToActionView = view }
        
        // App Icon
        findIdAndSet(nativeAdView, "ad_app_icon") { view -> nativeAdView.iconView = view }
        
        // Price
        findIdAndSet(nativeAdView, "ad_price") { view -> nativeAdView.priceView = view }
        
        // Star Rating (ad_stars in XML)
        findIdAndSet(nativeAdView, "ad_stars") { view -> nativeAdView.starRatingView = view }
        
        // Store
        findIdAndSet(nativeAdView, "ad_store") { view -> nativeAdView.storeView = view }
        
        // Advertiser
        findIdAndSet(nativeAdView, "ad_advertiser") { view -> nativeAdView.advertiserView = view }
        
        // Media View
        findIdAndSet(nativeAdView, "ad_media") { view -> 
            if (view is MediaView) {
                nativeAdView.mediaView = view
            }
        }
    }

    private fun findIdAndSet(root: NativeAdView, idName: String, setter: (android.view.View) -> Unit) {
        val packageName = context.packageName
        val resId = context.resources.getIdentifier(idName, "id", packageName)
        if (resId != 0) {
            val view = root.findViewById<android.view.View>(resId)
            if (view != null) {
                setter(view)
            }
        }
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
