package com.oz.android.oz_ads.ads_inline.admob

import android.content.Context
import android.util.AttributeSet
import com.oz.android.utils.OzLog
import android.view.LayoutInflater
import com.oz.android.ads_core.R
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.ads_core.admobs.native_advanced.AdmobNativeAdvanced
import com.oz.android.oz_ads.ads_inline.InlineAds
import com.oz.android.utils.listener.OzAdError
import com.oz.android.OzAdsManager
import java.util.concurrent.ConcurrentHashMap

open class OzAdmobNativeAd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : InlineAds<AdmobNativeAdvanced>(context, attrs, defStyleAttr) {

    override val adFormat: String = "native"

    companion object {
        private const val TAG = "OzAdmobNativeAd"
    }

    // Map key -> adUnitId
    private val adUnitIds = ConcurrentHashMap<String, String>()

    private var mediaAspectRatio: Int? = null

    fun setMediaRatio(ratio: Int) {
        this.mediaAspectRatio = ratio
        OzLog.d(TAG, "setMediaRatio: $ratio")
    }

    // Map key -> View
    private val nativeAdViews = ConcurrentHashMap<String, android.view.View>()

    // Map key -> Layout ID
    private var layoutId = 0

    /**
     * Set ad unit ID for a key
     * @param key Key to identify the placement
     * @param adUnitId Ad unit ID from AdMob
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        adUnitIds[key] = adUnitId
        OzLog.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Set NativeAdView for a key
     * @param key Key to identify the placement
     * @param nativeAdView Pre-setup View
     */
    fun setNativeAdView(key: String, nativeAdView: android.view.View) {
        nativeAdViews[key] = nativeAdView
        OzLog.d(TAG, "NativeAdView set for key: $key")
    }

    /**
     * Set Layout Resource ID.
     * NativeAdView will be inflated from this layout XML.
     * @param layoutId Layout Resource ID
     */
    fun setLayoutId(layoutId: Int) {
        this.layoutId = layoutId
        OzLog.d(TAG, "Layout ID set: $layoutId")
    }

    /**
     * Get ad unit ID for a key
     * @param key Key to identify the placement
     * @return Ad unit ID, null if not set yet
     */
    override fun getAdUnitId(key: String): String? = adUnitIds[key]

    override fun createAd(key: String): AdmobNativeAdvanced? {
        val adUnitId = adUnitIds[key]
        if (adUnitId.isNullOrBlank()) {
            OzLog.e(TAG, "Ad unit ID not set for key: $key")
            return null
        }

        val nativeListener = object : OzAdListener<AdmobNativeAdvanced>() {
            override fun onAdLoaded(ad: AdmobNativeAdvanced) {
                this@OzAdmobNativeAd.onAdLoaded(key, ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                this@OzAdmobNativeAd.onAdLoadFailed(key, error.message, error.code)
            }

            override fun onAdClicked() {
                this@OzAdmobNativeAd.onAdClicked(key)
            }

            override fun onAdImpression() {
                // Update internal state to SHOWING and stop shimmer when AdMob confirms impression
                this@OzAdmobNativeAd.onAdShown(key)
            }
        }

        val mergedListener = nativeListener.merge(listener)

        return AdmobNativeAdvanced.create(context, adUnitId, mergedListener).apply {
            this@OzAdmobNativeAd.mediaAspectRatio?.let { setMediaRatio(it) }
        }
    }

    override fun onLoadAd(key: String, ad: AdmobNativeAdvanced) {
        OzLog.d(TAG, "Loading native ad for key: $key")
        ad.load()
    }

    override fun setShimmerSize(key: String) {
        var heightPx = 0
        val nativeAdView = nativeAdViews[key]

        if (nativeAdView != null && nativeAdView.layoutParams != null && nativeAdView.layoutParams.height > 0) {
            heightPx = nativeAdView.layoutParams.height
        } else {
            val resId = layoutId
            if (resId != 0) {
                try {
                    // Inflate a dummy view to check height
                    val view = LayoutInflater.from(context).inflate(resId, this, false)
                    // If the root view has a fixed height, use it
                    if (view.layoutParams != null && view.layoutParams.height > 0) {
                        heightPx = view.layoutParams.height
                    } else {
                        // Measure the view to get an estimated height
                        val displayMetrics = context.resources.displayMetrics
                        val widthSpec = MeasureSpec.makeMeasureSpec(
                            displayMetrics.widthPixels,
                            MeasureSpec.AT_MOST
                        )
                        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                        view.measure(widthSpec, heightSpec)
                        heightPx = view.measuredHeight
                    }
                } catch (e: Exception) {
                    OzLog.e(TAG, "Failed to inflate layout for shimmer size: ${e.message}")
                }
            }
        }

        if (heightPx > 0) {
            shimmerLayout?.let { layout ->
                layout.layoutParams.height = heightPx
                layout.requestLayout()
            }
        }
    }

    override fun onShowAds(key: String, ad: AdmobNativeAdvanced) {
        var nativeAdView = nativeAdViews[key]

        // If no NativeAdView exists, check if layoutId is set
        if (nativeAdView == null) {
            val resId = layoutId
            OzLog.d(TAG, "Inflating native ad view from layout ID: $resId")
            try {
                val inflatedView = LayoutInflater.from(context).inflate(resId, this, false)
                nativeAdView = inflatedView
                // Cache for subsequent uses
                nativeAdViews[key] = nativeAdView
            } catch (e: Exception) {
                OzLog.e(TAG, "Failed to inflate layout: ${e.message}")
                onAdShowFailed(key, "Failed to inflate layout: ${e.message}")
                return
            }
        }

        OzLog.d(TAG, "Showing native ad for key: $key")
        // Show native ad in this ViewGroup
        ad.show(this, nativeAdView)
        // NOTE: onAdShown(key) is called from onAdImpression() when AdMob confirms visual impression
    }

    override fun hideAds() {
        removeAllViews()
        OzLog.d(TAG, "Native ads hidden")
    }

    override fun destroyAd(ad: AdmobNativeAdvanced) {
        OzLog.d(TAG, "Destroying native ad")
        ad.destroy()
    }

    override fun onPauseAd() {
        // Native ads generally don't need explicit pause handling
        OzLog.d(TAG, "Pausing native ads (no-op)")
        if (OzAdsManager.getInstance().config.offAdsOnPause) {
            visibility = INVISIBLE
        }
    }

    override fun onResumeAd() {
        // Native ads generally don't need explicit resume handling
        OzLog.d(TAG, "Resuming native ads (no-op)")
        if (isAdEnable()) {
            visibility = VISIBLE
        } else {
            visibility = GONE
        }
    }
}
