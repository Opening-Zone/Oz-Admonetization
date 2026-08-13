package com.oz.android.oz_ads.ads_inline.admob

import com.oz.android.ads_core.admobs.banner.AdmobBanner
import android.content.Context
import android.util.AttributeSet
import com.oz.android.utils.OzLog
import com.google.android.gms.ads.AdSize
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.config.AdsCoreType
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

    private var currentAdUnitId: String = ""
    private var collapsiblePosition: String? = null
    private var currentBannerAd: AdmobBanner? = null

    override val adFormat: String = "banner"
    override fun getAdUnitId(key: String): String = currentAdUnitId

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
        this.currentAdUnitId = adUnitId
        OzLog.d(TAG, "Ad unit ID set for key: $key -> $adUnitId")
    }

    /**
     * Enable collapsible banner
     * @param position "top" or "bottom" for collapse button position
     */
    fun setCollapsible(position: String) {
        this.collapsiblePosition = position
        OzLog.d(TAG, "Collapsible banner enabled at: $position")
    }

    /**
     * Enable collapsible banner at top
     */
    fun setCollapsibleTop() {
        this.collapsiblePosition = "top"
        OzLog.d(TAG, "Collapsible banner enabled at top")
    }

    /**
     * Enable collapsible banner at bottom
     */
    fun setCollapsibleBottom() {
        this.collapsiblePosition = "bottom"
        OzLog.d(TAG, "Collapsible banner enabled at bottom")
    }

    /**
     * Disable collapsible banner
     */
    fun disableCollapsible() {
        this.collapsiblePosition = null
        OzLog.d(TAG, "Collapsible banner disabled")
    }

    override fun createAd(key: String): AdmobBanner? {
        val adUnitId = currentAdUnitId
        if (adUnitId.isBlank()) {
            OzLog.e(TAG, "Ad unit ID not set for key: $key")
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
                this@OzAdmobBannerAd.onAdLoadFailed(key, error.message, error.code)
            }

            override fun onAdClicked() {
                // Bridge to OzAds.onAdClicked()
                this@OzAdmobBannerAd.onAdClicked(key)
            }

            override fun onAdImpression() {
                // Update internal state to SHOWING and stop shimmer when AdMob confirms impression
                this@OzAdmobBannerAd.onAdShown(key)
            }
        }

        val mergedListener = bannerListener.merge(listener)

        val bannerAd = AdmobBanner.create(context, adUnitId, mergedListener)
        bannerAd.setCollapsible(collapsiblePosition)
        return bannerAd
    }


    override fun onLoadAd(key: String, ad: AdmobBanner) {
        OzLog.d(TAG, "Loading banner ad for key: $key")
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
            OzLog.w(TAG, "Layout not measured yet, skipping shimmer size")
            return
        }

        val density = context.resources.displayMetrics.density
        val widthDp = (width / density).toInt()

        OzLog.d(TAG, "Setting shimmer size from container: ${width}px (${widthDp}dp)")

        val isNextGen = OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN

        // Match the AdSize type used by each banner implementation:
        // - Next-Gen (AdmobNextBanner) always uses anchored adaptive banner.
        // - Standard (AdmobStandardBanner) uses inline adaptive banner.
        val adSize = if (isNextGen) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        } else {
            val heightParams = layoutParams?.height ?: android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            if (heightParams == android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
                AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
            } else {
                val heightDp = (heightParams / density).toInt()
                if (heightDp > 32) {
                    AdSize.getInlineAdaptiveBannerAdSize(widthDp, heightDp)
                } else {
                    AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
                }
            }
        }

        val heightPx = adSize.getHeightInPixels(context)

        // Set shimmer height to match the ad size
        if (heightPx > 0) {
            shimmerLayout?.let { layout ->
                layout.layoutParams.height = heightPx
                layout.requestLayout()
            }
            OzLog.d(TAG, "Shimmer size set to: width=${width}px, height=${heightPx}px (AdSize: ${adSize.width}dp x ${adSize.height}dp)")
        } else {
            OzLog.e(TAG, "Failed to calculate valid shimmer height")
        }
    }

    override fun onShowAds(key: String, ad: AdmobBanner) {
        OzLog.d(TAG, "Showing banner ad for key: $key")
        currentBannerAd = ad
        // Show banner in this ViewGroup
        ad.show(this)
        // NOTE: onAdShown(key) is called from onAdImpression() when AdMob confirms visual impression
    }

    override fun hideAds() {
        // Remove all child views to hide the ad
        removeAllViews()
        OzLog.d(TAG, "Banner ads hidden")
    }

    override fun destroyAd(ad: AdmobBanner) {
        OzLog.d(TAG, "Destroying banner ad")
        // Detach from parent first to avoid "WebView.destroy() called while WebView is still attached"
        ad.detachFromParent()
        ad.destroy()
    }

    override fun onPauseAd() {
        OzLog.d(TAG, "Pausing all banner ads")
        currentBannerAd?.pause()
        if (OzAdsManager.getInstance().config.offAdsOnPause) {
            visibility = INVISIBLE
        }
    }

    override fun onResumeAd() {
        OzLog.d(TAG, "Resuming all banner ads")
        currentBannerAd?.resume()
        if (isAdEnable()) {
            visibility = VISIBLE
        } else {
            visibility = GONE
        }
    }
}




