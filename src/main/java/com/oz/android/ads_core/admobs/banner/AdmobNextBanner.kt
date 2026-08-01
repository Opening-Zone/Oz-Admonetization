package com.oz.android.ads_core.admobs.banner

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
import android.view.ViewGroup
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger

class AdmobNextBanner(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobBanner>? = null
) : AdmobBanner(context, adUnitId, listener) {

    @Volatile private var adView: AdView? = null
    @Volatile private var pendingContainer: ViewGroup? = null
    @Volatile private var containerForSizeCalculation: ViewGroup? = null

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobNextBanner"
        const val COLLAPSIBLE_TOP = "top"
        const val COLLAPSIBLE_BOTTOM = "bottom"
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
            container.post {
                createAndLoadAdView()
            }
        } else {
            createAndLoadAdView()
        }
    }

    private fun createAndLoadAdView() {

        // Always (re)create the AdView to allow retries after load failure.
        // The old adView is destroyed before a new one is created.
        adView?.destroy()
        adView = null

        val widthDp = calculateAdSizeDp()
        OzLog.d(TAG, "Creating Next-Gen AdView with width: ${widthDp}dp")

        // Use standard orientation anchored adaptive size for ALL banners (including collapsible — Google only
        // requires anchored adaptive; large is optional). This formula must match the shimmer height
        // in OzAdmobBannerAd.setShimmerSizeInternal() — if modified here, it MUST be modified there
        // as well, otherwise layout jumping issues will reoccur.
        val nextGenAdSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)

        val nextGenAdView = AdView(context)
        adView = nextGenAdView

        val builder = BannerAdRequest.Builder(adUnitId, nextGenAdSize)
        if (collapsiblePosition != null) {
            val extras = Bundle()
            extras.putString("collapsible", collapsiblePosition)
            builder.setGoogleExtrasBundle(extras)
            OzLog.d(TAG, "Collapsible banner request built with position: $collapsiblePosition")
        }
        val adRequest = builder.build()

        nextGenAdView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    // ── Runs on GMA background thread ──
                    // State update: no UI, safe on BG.
                    OzLog.d(TAG, "Banner ad loaded successfully (Next-Gen)")

                    // Event callbacks: callers decide their own threading.
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdImpression() {
                            listener?.onAdImpression()
                        }

                        override fun onAdClicked() {
                            listener?.onAdClicked()
                        }

                        override fun onAdShowedFullScreenContent() {
                            listener?.onAdShowedFullScreenContent()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            listener?.onAdDismissedFullScreenContent()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                            val ozError = error.toOzError()
                            listener?.onAdFailedToShowFullScreenContent(ozError)
                        }

                        override fun onAdPaid(value: AdValue) {
                            OzEventLogger.logPaidAdImpressionNextGen(
                                context,
                                value.valueMicros,
                                value.currencyCode,
                                adUnitId,
                                ad.getResponseInfo().adapterClassName ?: "unknown"
                            )
                        }
                    }

                    ad.bannerAdRefreshCallback = object : BannerAdRefreshCallback {
                        override fun onAdRefreshed() {
                            OzLog.d(TAG, "Next-Gen Banner ad refreshed")
                        }

                        override fun onAdFailedToRefresh(error: LoadAdError) {
                            OzLog.e(TAG, "Next-Gen Banner ad failed to refresh: ${error.message}")
                        }
                    }

                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdLoaded(this@AdmobNextBanner)

                    // show() has its own main-thread guard — safe to call from BG.
                    pendingContainer?.let { container ->
                        show(container)
                        pendingContainer = null
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // ── Runs on GMA background thread ──
                    // Null out adView so subsequent load() calls can create a fresh one (retry support).
                    adView = null
                    OzLog.e(TAG, "Banner ad failed to load: ${error.message} (Next-Gen)")
                    pendingContainer = null

                    val ozError = error.toOzError()
                    // Listener callback: called on GMA BG thread — caller decides thread.
                    listener?.onAdFailedToLoad(ozError)
                }
            }
        )
    }

    override fun show() {
        pendingContainer?.let { show(it) }
    }

    override fun show(container: ViewGroup) {
        val showRunnable = Runnable {
            val currentAdView = adView
            if (currentAdView == null) {
                OzLog.w(TAG, "AdView not ready. It will be shown automatically when loaded")
                pendingContainer = container
                return@Runnable
            }

            val parent = currentAdView.parent
            if (parent is ViewGroup) {
                parent.removeView(currentAdView)
            }

            container.removeAllViews()
            container.addView(currentAdView)
            OzLog.d(TAG, "Banner ad displayed in container")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        pendingContainer?.let { loadThenShow(it) }
    }

    override fun loadThenShow(container: ViewGroup) {
        pendingContainer = container
        load(container)
    }

    override fun detachFromParent() {
        val detachRunnable = Runnable {
            adView?.let { view ->
                val parent = view.parent
                if (parent is ViewGroup) {
                    parent.removeView(view)
                }
            }
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachRunnable.run()
        } else {
            mainHandler.post(detachRunnable)
        }
    }

    override fun destroy() {
        adView?.destroy()
        adView = null
        pendingContainer = null
        containerForSizeCalculation = null
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

    fun isAdLoaded(): Boolean = adView != null

    override fun pause() {}
    override fun resume() {}
    override fun getAdSize(): com.google.android.gms.ads.AdSize? = null
}
