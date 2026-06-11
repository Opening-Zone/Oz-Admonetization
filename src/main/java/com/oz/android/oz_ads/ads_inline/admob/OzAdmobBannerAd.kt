package com.oz.android.oz_ads.ads_inline.admob

import com.oz.android.ads_core.admobs.banner.AdmobBanner
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.google.android.gms.ads.AdSize
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.oz_ads.ads_inline.InlineAds
import com.oz.android.utils.listener.OzAdError
import com.oz.android.OzAdsManager
import com.oz.android.utils.enums.AdState

/**
 * Concrete implementation of InlineAds for AdMob Banner
 * Handles BANNER format only
 */
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
     * Set ad unit ID for a key
     * @param key Key to identify the placement
     * @param adUnitId Ad unit ID from AdMob
     */
    fun setAdUnitId(key: String, adUnitId: String) {
        setPreloadKey(key)
        banner.adUnitId = adUnitId
        Log.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Enable collapsible banner
     * @param position "top" or "bottom" for collapse button position
     */
    fun setCollapsible(position: String) {
        banner.setCollapsible(position)
        Log.d(TAG, "Collapsible banner enabled at: $position")
    }

    /**
     * Enable collapsible banner at top
     */
    fun setCollapsibleTop() {
        banner.setCollapsibleTop()
        Log.d(TAG, "Collapsible banner enabled at top")
    }

    /**
     * Enable collapsible banner at bottom
     */
    fun setCollapsibleBottom() {
        banner.setCollapsibleBottom()
        Log.d(TAG, "Collapsible banner enabled at bottom")
    }

    /**
     * Disable collapsible banner
     */
    fun disableCollapsible() {
        banner.setCollapsible(null)
        Log.d(TAG, "Collapsible banner disabled")
    }

    override fun createAd(key: String): AdmobBanner? {
        val adUnitId = banner.adUnitId
        if (adUnitId.isBlank()) {
            Log.e(TAG, "Ad unit ID not set for key: $key")
            return null
        }

        val bannerListener = object : OzAdListener<AdmobBanner>() {
            override fun onAdLoaded(ad: AdmobBanner) {
                // Pass the loaded ad object to the parent only if the state is LOADING (prevents auto-refresh conflicts)
                if (getAdState(key) == AdState.LOADING) {
                    this@OzAdmobBannerAd.onAdLoaded(key, ad)
                }
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
        // Pass this ViewGroup as container so AdmobBanner can calculate size from actual layout dimensions
        ad.load(this)
    }

    override fun setShimmerSize(key: String) {
        // Wait for the view to be measured before setting shimmer size
        if (width == 0 || height == 0) {
            post {
                setShimmerSizeInternal()
            }
        } else {
            setShimmerSizeInternal()
        }
    }

    /**
     * Set shimmer size based on actual layout dimensions
     * Uses the same ad size calculation as the actual banner ad
     */
    private fun setShimmerSizeInternal() {
        if (width == 0) {
            Log.w(TAG, "Layout not measured yet, skipping shimmer size")
            return
        }

        val density = context.resources.displayMetrics.density
        val widthDp = (width / density).toInt()
        
        Log.d(TAG, "Setting shimmer size from container: ${width}px (${widthDp}dp)")
        
        // Use the same logic as AdmobBanner.calculateAdSize()
        val heightParams = layoutParams?.height ?: android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        val adSize = if (heightParams == android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
            AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
        } else {
            val heightDp = (heightParams / density).toInt()
            if (heightDp > 32) {
                AdSize.getInlineAdaptiveBannerAdSize(widthDp, heightDp)
            } else {
                AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
            }
        }
        val heightPx = adSize.getHeightInPixels(context)
        
        // Set shimmer height to match the ad size
        if (heightPx > 0) {
            shimmerLayout?.let { layout ->
                layout.layoutParams.height = heightPx
                layout.requestLayout()
            }
            Log.d(TAG, "Shimmer size set to: width=${width}px, height=${heightPx}px (AdSize: ${adSize.width}dp x ${adSize.height}dp)")
        } else {
            Log.e(TAG, "Failed to calculate valid shimmer height")
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
        // Detach from parent first to avoid "WebView.destroy() called while WebView is still attached"
        ad.detachFromParent()
        ad.destroy()
    }

    override fun onPauseAd() {
        Log.d(TAG, "Pausing all banner ads")
        if (OzAdsManager.getInstance().config.offAdsOnPause) {
            visibility = INVISIBLE
        }
    }

    override fun onResumeAd() {
        Log.d(TAG, "Resuming all banner ads")
        if (isAdEnable()) {
            visibility = VISIBLE
        } else {
            visibility = GONE
        }
    }
}




