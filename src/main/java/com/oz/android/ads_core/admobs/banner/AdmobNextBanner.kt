package com.oz.android.ads_core.admobs.banner

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
) : AdmobBase<AdmobBanner>(context, adUnitId, listener), AdmobBanner {

    private var adView: AdView? = null
    private var isLoaded = false
    private var pendingContainer: ViewGroup? = null
    private var containerForSizeCalculation: ViewGroup? = null

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    override var collapsiblePosition: String? = null
        private set

    companion object {
        private const val TAG = "AdmobNextBanner"
        const val COLLAPSIBLE_TOP = "top"
        const val COLLAPSIBLE_BOTTOM = "bottom"
    }

    override fun setCollapsible(position: String?) {
        if (position != null && position != COLLAPSIBLE_TOP && position != COLLAPSIBLE_BOTTOM) {
            Log.w(TAG, "Invalid collapsible position: $position. Use COLLAPSIBLE_TOP or COLLAPSIBLE_BOTTOM")
            return
        }
        collapsiblePosition = position
    }

    override fun setCollapsibleTop() {
        setCollapsible(COLLAPSIBLE_TOP)
    }

    override fun setCollapsibleBottom() {
        setCollapsible(COLLAPSIBLE_BOTTOM)
    }

    override fun load() {
        load(null)
    }

    override fun load(container: ViewGroup?) {
        if (adView != null && isLoaded) {
            Log.d(TAG, "Ad already loaded")
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
        if (adView == null) {
            val widthDp = calculateAdSizeDp()
            Log.d(TAG, "Creating Next-Gen AdView with width: ${widthDp}dp")

            val nextGenAdSize = if (collapsiblePosition != null) {
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
            } else {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            }

            val nextGenAdView = AdView(context)
            adView = nextGenAdView

            val extras = Bundle()
            if (collapsiblePosition != null) {
                extras.putString("collapsible", collapsiblePosition)
            }

            @Suppress("UNCHECKED_CAST")
            val adapterClass = try {
                Class.forName("com.google.ads.mediation.admob.AdMobAdapter") as? Class<out com.google.android.gms.ads.mediation.MediationExtrasReceiver>
            } catch (e: Throwable) {
                null
            }

            val builder = BannerAdRequest.Builder(adUnitId, nextGenAdSize)
            if (adapterClass != null) {
                builder.putAdSourceExtrasBundle(adapterClass, extras)
            }
            val adRequest = builder.build()

            nextGenAdView.loadAd(
                adRequest,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        // ── Runs on GMA background thread ──
                        // State update: no UI, safe on BG.
                        isLoaded = true
                        Log.d(TAG, "Banner ad loaded successfully (Next-Gen)")

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
                                listener?.onAdFailedToShowFullScreenContent(error.toOzError())
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
                                Log.d(TAG, "Next-Gen Banner ad refreshed")
                            }

                            override fun onAdFailedToRefresh(error: LoadAdError) {
                                Log.e(TAG, "Next-Gen Banner ad failed to refresh: ${error.message}")
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
                        // State cleanup: no UI, stays on BG.
                        isLoaded = false
                        Log.e(TAG, "Banner ad failed to load: ${error.message} (Next-Gen)")
                        pendingContainer = null

                        // Listener callback: called on GMA BG thread — caller decides thread.
                        listener?.onAdFailedToLoad(error.toOzError())
                    }
                }
            )
        }
    }

    override fun show() {
        pendingContainer?.let { show(it) }
    }

    override fun show(container: ViewGroup) {
        val showRunnable = Runnable {
            val currentAdView = adView
            if (currentAdView == null) {
                Log.w(TAG, "AdView is null. Call load() first")
                return@Runnable
            }

            if (!isLoaded) {
                Log.w(TAG, "Ad not loaded yet. It will be shown automatically when loaded")
                pendingContainer = container
                return@Runnable
            }

            val parent = currentAdView.parent
            if (parent is ViewGroup) {
                parent.removeView(currentAdView)
            }

            container.removeAllViews()
            container.addView(currentAdView)
            Log.d(TAG, "Banner ad displayed in container")
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
        isLoaded = false
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

    fun isAdLoaded(): Boolean = isLoaded

    override fun pause() {}
    override fun resume() {}
    override fun getAdSize(): com.google.android.gms.ads.AdSize? = null
}
