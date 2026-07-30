package com.oz.android.ads_core.admobs.banner

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.oz.android.utils.OzLog
import android.view.ViewGroup
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.ads.mediation.admob.AdMobAdapter
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger

/**
 * Standard SDK implementation of Banner ads.
 */
class AdmobStandardBanner(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobBanner>?
) : AdmobBanner(context, adUnitId, listener) {
    private var adView: AdView? = null
    private var pendingContainer: ViewGroup? = null
    private var containerForSizeCalculation: ViewGroup? = null
    
    companion object {
        private const val TAG = "AdmobStandardBanner"
    }



    override fun load() {
        load(null)
    }

    override fun load(container: ViewGroup?) {
        if (adView != null) {
            OzLog.d(TAG, "Ad already loaded")
            return
        }

        containerForSizeCalculation = container

        if (container != null && (container.width == 0 || container.height == 0)) {
            OzLog.d(TAG, "Container not measured yet, waiting for layout...")
            container.post {
                createAndLoadAdView()
            }
        } else {
            createAndLoadAdView()
        }
    }

    private fun createAndLoadAdView() {
        if (adView == null) {
            val adSize = calculateAdSize()
            OzLog.d(TAG, "Creating AdView with size: ${adSize.width}dp x ${adSize.height}dp")

            val standardAdView = AdView(context).apply {
                this.adUnitId = this@AdmobStandardBanner.adUnitId
                setAdSize(adSize)
                onPaidEventListener = OnPaidEventListener { adValue ->
                    OzEventLogger.logPaidAdImpression(
                        context,
                        adValue,
                        this@AdmobStandardBanner.adUnitId,
                        this.responseInfo
                    )
                }
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        OzLog.d(TAG, "Banner ad loaded successfully")
                        listener?.onAdLoaded(this@AdmobStandardBanner)

                        pendingContainer?.let { container ->
                            show(container)
                            pendingContainer = null
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        OzLog.e(TAG, "Banner ad failed to load: ${error.message}")
                        listener?.onAdFailedToLoad(error.toOzError())
                        pendingContainer = null
                    }

                    override fun onAdClicked() {
                        OzLog.d(TAG, "Banner ad was clicked")
                        listener?.onAdClicked()
                    }

                    override fun onAdImpression() {
                        OzLog.d(TAG, "Banner ad recorded an impression")
                        listener?.onAdImpression()
                    }
                }
            }
            adView = standardAdView
        }

        val standardAdView = adView as AdView
        val adRequest = buildAdRequest()
        standardAdView.loadAd(adRequest)
        OzLog.d(TAG, "Banner ad loading started${if (collapsiblePosition != null) " (collapsible: $collapsiblePosition)" else ""}")
    }

    override fun show() {
        pendingContainer?.let { show(it) }
    }

    override fun show(container: ViewGroup) {
        val currentAdView = adView
        if (currentAdView == null) {
            OzLog.w(TAG, "AdView not ready. It will be shown automatically when loaded")
            pendingContainer = container
            return
        }

        val parent = currentAdView.parent
        if (parent is ViewGroup) {
            parent.removeView(currentAdView)
        }

        container.removeAllViews()
        container.addView(currentAdView)
        OzLog.d(TAG, "Banner ad displayed in container")
    }

    override fun loadThenShow() {
        pendingContainer?.let { loadThenShow(it) }
    }

    override fun loadThenShow(container: ViewGroup) {
        pendingContainer = container
        load(container)
    }

    override fun pause() {
        adView?.pause()
    }

    override fun resume() {
        adView?.resume()
    }

    override fun detachFromParent() {
        adView?.let { view ->
            val parent = view.parent
            if (parent is ViewGroup) {
                parent.removeView(view)
            }
        }
    }

    override fun destroy() {
        adView?.destroy()
        adView = null
        pendingContainer = null
        containerForSizeCalculation = null
    }

    override fun getAdSize(): AdSize? {
        return adView?.adSize
    }

    private fun buildAdRequest(): AdRequest {
        val adRequest = if (collapsiblePosition != null) {
            val extras = Bundle()
            extras.putString("collapsible", collapsiblePosition)
            OzLog.d(TAG, "Creating AdRequest with collapsible: $collapsiblePosition")
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        } else {
            AdRequest.Builder().build()
        }
        return adRequest
    }

    private fun calculateAdSize(): AdSize {
        val widthDp = calculateAdSizeDp()
        val container = containerForSizeCalculation
        val density = context.resources.displayMetrics.density

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
        
        OzLog.d(TAG, "AdSize calculated: ${adSize.width}dp x ${adSize.height}dp")
        return adSize
    }

    private fun calculateAdSizeDp(): Int {
        val container = containerForSizeCalculation
        val density = context.resources.displayMetrics.density

        return if (container != null && container.width > 0) {
            (container.width / density).toInt()
        } else if (context is Activity) {
            val windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(context)
            val bounds = windowMetrics.bounds
            (bounds.width() / density).toInt()
        } else {
            val displayMetrics = context.resources.displayMetrics
            (displayMetrics.widthPixels / density).toInt()
        }
    }
}
