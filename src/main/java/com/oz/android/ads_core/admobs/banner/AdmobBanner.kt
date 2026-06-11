package com.oz.android.ads_core.admobs.banner

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.ads.mediation.admob.AdMobAdapter
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener

/**
 * Class managing banner ads from AdMob
 * Provides 3 main methods: load, show, and loadThenShow
 */
class AdmobBanner(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobBanner>?
) : AdmobBase<AdmobBanner>(
    context,
    adUnitId,
    listener
) {
    private var adView: AdView? = null
    private var isLoaded = false
    private var pendingContainer: ViewGroup? = null
    private var containerForSizeCalculation: ViewGroup? = null
    
    /**
     * Collapsible banner configuration
     * null = disabled (default)
     * "top" = collapse button at top
     * "bottom" = collapse button at bottom
     */
    var collapsiblePosition: String? = null
        private set

    companion object {
        private const val TAG = "AdmobBanner"
        
        // Collapsible banner positions
        const val COLLAPSIBLE_TOP = "top"
        const val COLLAPSIBLE_BOTTOM = "bottom"
    }

    /**
     * Set collapsible banner option
     * @param position "top" or "bottom" for collapsible position, null to disable
     */
    fun setCollapsible(position: String?) {
        if (position != null && position != COLLAPSIBLE_TOP && position != COLLAPSIBLE_BOTTOM) {
            Log.w(TAG, "Invalid collapsible position: $position. Use COLLAPSIBLE_TOP or COLLAPSIBLE_BOTTOM")
            return
        }
        collapsiblePosition = position
        Log.d(TAG, "Collapsible banner ${if (position != null) "enabled at $position" else "disabled"}")
    }
    
    /**
     * Enable collapsible banner at top
     */
    fun setCollapsibleTop() {
        setCollapsible(COLLAPSIBLE_TOP)
    }
    
    /**
     * Enable collapsible banner at bottom
     */
    fun setCollapsibleBottom() {
        setCollapsible(COLLAPSIBLE_BOTTOM)
    }

    /**
     * Load banner ad
     * The ad will be loaded but not shown yet
     */
    override fun load() {
        load(null)
    }

    /**
     * Load banner ad with a container to calculate size
     * @param container ViewGroup container to calculate ad size
     */
    fun load(container: ViewGroup?) {
        if (adView != null && isLoaded) {
            Log.d(TAG, "Ad already loaded")
            return
        }

        containerForSizeCalculation = container

        // If container is provided and not yet measured, wait for measurement to finish
        if (container != null && (container.width == 0 || container.height == 0)) {
            Log.d(TAG, "Container not measured yet, waiting for layout...")
            container.post {
                createAndLoadAdView()
            }
        } else {
            createAndLoadAdView()
        }
    }

    /**
     * Create and load AdView
     */
    private fun createAndLoadAdView() {
        // Create a new AdView if none exists yet
        if (adView == null) {
            val adSize = calculateAdSize()
            Log.d(TAG, "Creating AdView with size: ${adSize.width}dp x ${adSize.height}dp")

            adView = AdView(context).apply {
                this.adUnitId = this@AdmobBanner.adUnitId
                setAdSize(adSize)
                onPaidEventListener = getOnPaidListener(responseInfo)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isLoaded = true
                        Log.d(TAG, "Banner ad loaded successfully")
                        listener?.onAdLoaded(this@AdmobBanner)

                        // If there is a waiting container, show it automatically
                        pendingContainer?.let { container ->
                            show(container)
                            pendingContainer = null
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoaded = false
                        Log.e(TAG, "Banner ad failed to load: ${error.message}")
                        listener?.onAdFailedToLoad(error.toOzError())
                        pendingContainer = null
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Banner ad was clicked")
                        listener?.onAdClicked()
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "Banner ad recorded an impression")
                        listener?.onAdImpression()
                    }
                }
            }
        }

        // Load the ad
        val adRequest = buildAdRequest()
        adView?.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading started${if (collapsiblePosition != null) " (collapsible: $collapsiblePosition)" else ""}")
    }

    /**
     * Show banner ad (implementation from interface)
     * Note: Banner requires a container, use show(container: ViewGroup) instead of this method
     */
    override fun show() {
        Log.w(TAG, "show() called without container. Use show(container: ViewGroup) for banner ads")
        pendingContainer?.let { show(it) }
    }

    /**
     * Show banner ad in the container
     * @param container ViewGroup to contain the banner ad
     */
    fun show(container: ViewGroup) {
        val currentAdView = adView ?: run {
            Log.w(TAG, "AdView is null. Call load() first")
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet. It will be shown automatically when loaded")
            pendingContainer = container
            return
        }

        // Remove parent from the AdView if it has one
        val parent = currentAdView.parent
        if (parent is ViewGroup) {
            parent.removeView(currentAdView)
        }

        // Clear old views in the container
        container.removeAllViews()

        // Add AdView to the container
        container.addView(currentAdView)
        Log.d(TAG, "Banner ad displayed in container")
    }

    /**
     * Load the ad and automatically show it when load completes (implementation from interface)
     * Note: Banner requires a container, use loadThenShow(container: ViewGroup) instead of this method
     */
    override fun loadThenShow() {
        if (pendingContainer != null) {
            val container = pendingContainer!!
            loadThenShow(container)
        }
    }

    /**
     * Load the ad and automatically show it when load completes
     * @param container ViewGroup to contain the banner ad
     */
    fun loadThenShow(container: ViewGroup) {
        pendingContainer = container
        load(container)
    }

    /**
     * Pause ad (call in onPause of Activity/Fragment)
     */
    fun pause() {
        adView?.pause()
    }

    /**
     * Resume ad (call in onResume of Activity/Fragment)
     */
    fun resume() {
        adView?.resume()
    }

    /**
     * Detach adView from its current parent ViewGroup (if any)
     */
    fun detachFromParent() {
        adView?.let { view ->
            val parent = view.parent
            if (parent is ViewGroup) {
                parent.removeView(view)
            }
        }
    }

    /**
     * Destroy ad (call in onDestroy of Activity/Fragment)
     */
    fun destroy() {
        adView?.destroy()
        adView = null
        isLoaded = false
        pendingContainer = null
        containerForSizeCalculation = null
    }

    /**
     * Get the actual ad size being used
     * @return AdSize or null if ad not created yet
     */
    fun getAdSize(): AdSize? {
        return adView?.adSize
    }

    /**
     * Build AdRequest with collapsible extras if enabled
     * Based on official Google AdMob documentation
     */
    private fun buildAdRequest(): AdRequest {
        // Add collapsible banner extras if enabled
        val adRequest = if (collapsiblePosition != null) {
            // Create an extra parameter that aligns the collapse button
            val extras = Bundle()
            extras.putString("collapsible", collapsiblePosition)
            
            Log.d(TAG, "Creating AdRequest with collapsible: $collapsiblePosition")
            
            // Create an ad request with collapsible extras
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        } else {
            // Standard ad request without collapsible
            AdRequest.Builder().build()
        }
        
        return adRequest
    }

    /**
     * Calculate ad size based on the actual container
     * If a container is provided, use the container size
     * Otherwise, fallback to the screen width
     */
    private fun calculateAdSize(): AdSize {
        val container = containerForSizeCalculation
        val density = context.resources.displayMetrics.density

        val widthDp = if (container != null && container.width > 0) {
            (container.width / density).toInt()
        } else if (context is Activity) {
            val windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(context)
            val bounds = windowMetrics.bounds
            (bounds.width() / density).toInt()
        } else {
            val displayMetrics = context.resources.displayMetrics
            (displayMetrics.widthPixels / density).toInt()
        }

        // Check container layout height for adaptive ad size
        val heightParams = container?.layoutParams?.height ?: android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
        
        Log.d(TAG, "AdSize calculated: ${adSize.width}dp x ${adSize.height}dp (${adSize.getHeightInPixels(context)}px)")
        
        return adSize
    }

}

