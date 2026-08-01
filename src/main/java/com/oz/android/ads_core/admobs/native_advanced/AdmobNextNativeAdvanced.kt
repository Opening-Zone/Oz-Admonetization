package com.oz.android.ads_core.admobs.native_advanced

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger
import com.oz.android.ads_core.R

class AdmobNextNativeAdvanced(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobNativeAdvanced>? = null
) : AdmobBase<AdmobNativeAdvanced>(context, adUnitId, listener), AdmobNativeAdvanced {

    @Volatile private var nextGenNativeAd: NativeAd? = null
    @Volatile private var pendingContainer: ViewGroup? = null
    @Volatile private var pendingNativeAdView: View? = null
    // Custom populate callback stored when using loadThenShow(container, view, callback).
    // Passed to show() when the pending show is triggered after load completes.
    @Volatile private var pendingPopulateCallback: ((Any, View) -> Unit)? = null
    private var mediaAspectRatio: Int? = null

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setMediaRatio(ratio: Int) {
        this.mediaAspectRatio = ratio
    }

    companion object {
        private const val TAG = "AdmobNextNativeAdvanced"

        // Named constants for Media Aspect Ratio (matching GMS Standard values)
        const val ASPECT_RATIO_ANY = 1
        const val ASPECT_RATIO_LANDSCAPE = 2
        const val ASPECT_RATIO_PORTRAIT = 3
        const val ASPECT_RATIO_SQUARE = 4
    }

    override fun load() {
        if (nextGenNativeAd != null) {
            OzLog.d(TAG, "Ad already loaded (Next-Gen)")
            listener?.onAdLoaded(this)
            return
        }

        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()

        val builder = NativeAdRequest.Builder(
            adUnitId,
            listOf(NativeAd.NativeAdType.NATIVE)
        ).setVideoOptions(videoOptions)
        
        mediaAspectRatio?.let { ratio ->
            // GMS Standard integer values: ANY=1, LANDSCAPE=2, PORTRAIT=3, SQUARE=4
            // Map to equivalent Next-Gen enum constants.
            val nextGenRatio = when (ratio) {
                1 -> NativeAd.NativeMediaAspectRatio.ANY
                2 -> NativeAd.NativeMediaAspectRatio.LANDSCAPE
                3 -> NativeAd.NativeMediaAspectRatio.PORTRAIT
                4 -> NativeAd.NativeMediaAspectRatio.SQUARE
                else -> NativeAd.NativeMediaAspectRatio.ANY
            }
            builder.setMediaAspectRatio(nextGenRatio)
        }
        
        val adRequest = builder.build()

        val adCallback = object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                // ── Runs on GMA background thread ──
                // Activity lifecycle field reads are safe from any thread.
                if (context is Activity) {
                    if (context.isDestroyed || context.isFinishing || context.isChangingConfigurations) {
                        nativeAd.destroy()
                        clearPendingState()
                        return
                    }
                }

                // State updates: safe on BG, no UI involved.
                nextGenNativeAd?.destroy()
                nextGenNativeAd = nativeAd

                OzLog.d(TAG, "Native ad loaded successfully (Next-Gen)")
                setupNextGenNativeEventCallback(nativeAd)

                // Listener callbacks: called on GMA BG thread — callers decide their own thread.
                listener?.onAdLoaded(this@AdmobNextNativeAdvanced)

                // show() has its own main-thread guard — safe to call from BG.
                pendingContainer?.let { container ->
                    pendingNativeAdView?.let { nativeAdView ->
                        // Pass the stored populateCallback so show() doesn't fall back to default populate.
                        show(container, nativeAdView, pendingPopulateCallback)
                        pendingContainer = null
                        pendingNativeAdView = null
                        pendingPopulateCallback = null
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // ── Runs on GMA background thread ──
                OzLog.e(TAG, "Native ad failed to load: ${error.message} (Next-Gen)")

                // State cleanup: no UI, stays on BG.
                nextGenNativeAd = null
                clearPendingState()

                val ozError = error.toOzError()

                // Listener callback: called on GMA BG thread — caller decides thread.
                listener?.onAdFailedToLoad(ozError)
            }
        }

        NativeAdLoader.load(adRequest, adCallback)
    }

    override fun show() {
        OzLog.w(
            TAG,
            "show() called without container and NativeAdView. Use show(container: ViewGroup, nativeAdView: View) for native ads"
        )
    }

    override fun loadThenShow() {
        OzLog.w(
            TAG,
            "loadThenShow() called without container and NativeAdView. Use loadThenShow(container: ViewGroup, nativeAdView: View) for native ads"
        )
    }

    override fun show(
        container: ViewGroup,
        nativeAdView: View,
        populateCallback: ((Any, View) -> Unit)?
    ) {
        val showRunnable = Runnable {
            val currentAd = nextGenNativeAd
            if (currentAd == null) {
                OzLog.w(TAG, "NativeAd is null (Next-Gen). Call load() first")
                pendingContainer = container
                pendingNativeAdView = nativeAdView
                pendingPopulateCallback = populateCallback
                OzEventLogger.logAdSkip(context, adUnitId, "native_nextgen", "ad_null")
                return@Runnable
            }

            checkAndSwapMediaView(nativeAdView)

            val nextGenAdView = NativeAdView(context)
            nextGenAdView.layoutParams = nativeAdView.layoutParams
            
            if (nativeAdView.parent != null) {
                (nativeAdView.parent as ViewGroup).removeView(nativeAdView)
            }
            nextGenAdView.addView(nativeAdView)

            if (populateCallback != null) {
                populateCallback.invoke(currentAd, nextGenAdView)
            } else {
                populateNextGenNativeAdView(currentAd, nextGenAdView, nativeAdView)
            }

            container.removeAllViews()
            container.addView(nextGenAdView)
            OzLog.d(TAG, "Native ad displayed in container (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow(
        container: ViewGroup,
        nativeAdView: View,
        populateCallback: ((Any, View) -> Unit)?
    ) {
        pendingContainer = container
        pendingNativeAdView = nativeAdView
        // Store the custom callback so onNativeAdLoaded can pass it to show(),
        // preventing show() from falling back to populateNextGenNativeAdView() (double-population).
        pendingPopulateCallback = populateCallback
        load()
    }

    private fun populateNextGenNativeAdView(
        nativeAd: NativeAd,
        nextGenAdView: NativeAdView,
        nativeAdView: View
    ) {
        nextGenAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
        nextGenAdView.headlineView?.let { (it as? TextView)?.text = nativeAd.headline }

        nextGenAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
        nextGenAdView.bodyView?.let { bodyView ->
            if (nativeAd.body == null) {
                bodyView.visibility = View.INVISIBLE
            } else {
                bodyView.visibility = View.VISIBLE
                (bodyView as? TextView)?.text = nativeAd.body
            }
        }

        nextGenAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
        nextGenAdView.callToActionView?.let { ctaView ->
            if (nativeAd.callToAction == null) {
                ctaView.visibility = View.INVISIBLE
            } else {
                ctaView.visibility = View.VISIBLE
                (ctaView as? TextView)?.text = nativeAd.callToAction
            }
        }

        nextGenAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
        nextGenAdView.iconView?.let { iconView ->
            if (nativeAd.icon == null) {
                iconView.visibility = View.GONE
            } else {
                iconView.visibility = View.VISIBLE
                (iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            }
        }

        nextGenAdView.priceView = nativeAdView.findViewById(R.id.ad_price)
        nextGenAdView.priceView?.let { priceView ->
            if (nativeAd.price == null) {
                priceView.visibility = View.INVISIBLE
            } else {
                priceView.visibility = View.VISIBLE
                (priceView as? TextView)?.text = nativeAd.price
            }
        }

        nextGenAdView.storeView = nativeAdView.findViewById(R.id.ad_store)
        nextGenAdView.storeView?.let { storeView ->
            if (nativeAd.store == null) {
                storeView.visibility = View.INVISIBLE
            } else {
                storeView.visibility = View.VISIBLE
                (storeView as? TextView)?.text = nativeAd.store
            }
        }

        nextGenAdView.starRatingView = nativeAdView.findViewById(R.id.ad_stars)
        nextGenAdView.starRatingView?.let { starRatingView ->
            if (nativeAd.starRating == null) {
                starRatingView.visibility = View.INVISIBLE
            } else {
                starRatingView.visibility = View.VISIBLE
                (starRatingView as? RatingBar)?.rating = nativeAd.starRating!!.toFloat()
            }
        }

        nextGenAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)
        nextGenAdView.advertiserView?.let { advertiserView ->
            if (nativeAd.advertiser == null) {
                advertiserView.visibility = View.INVISIBLE
            } else {
                advertiserView.visibility = View.VISIBLE
                (advertiserView as? TextView)?.text = nativeAd.advertiser
            }
        }

        val mediaView: MediaView? = nativeAdView.findViewById(R.id.ad_media)
        nextGenAdView.registerNativeAd(nativeAd, mediaView)
    }

    private fun setupNextGenNativeEventCallback(ad: NativeAd) {
        ad.adEventCallback = object : NativeAdEventCallback {
            // All callbacks fire on GMA BG thread — callers decide their own threading.
            override fun onAdShowedFullScreenContent() {
                listener?.onAdShowedFullScreenContent()
            }

            override fun onAdDismissedFullScreenContent() {
                OzEventLogger.logAdDismissed(context, adUnitId, "native_nextgen")
                listener?.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                val ozError = error.toOzError()
                OzEventLogger.logAdShowFailed(context, adUnitId, "native_nextgen", ozError.code, ozError.message)
                listener?.onAdFailedToShowFullScreenContent(ozError)
            }

            override fun onAdImpression() {
                listener?.onAdImpression()
            }

            override fun onAdClicked() {
                OzEventLogger.logAdClickedCustom(context, adUnitId, "native_nextgen")
                listener?.onAdClicked()
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
    }

    override fun isAdLoaded(): Boolean {
        return nextGenNativeAd != null
    }

    override fun getCurrentNativeAd(): Any? {
        return nextGenNativeAd
    }

    fun getNextGenNativeAd(): NativeAd? {
        return nextGenNativeAd
    }

    override fun destroy() {
        nextGenNativeAd?.destroy()
        nextGenNativeAd = null
        clearPendingState()
        OzLog.d(TAG, "Native ad destroyed")
    }

    private fun clearPendingState() {
        pendingContainer = null
        pendingNativeAdView = null
        pendingPopulateCallback = null
    }

    private fun checkAndSwapMediaView(rootView: View) {
        val oldMediaView = rootView.findViewById<View>(R.id.ad_media)
        if (oldMediaView != null && oldMediaView.javaClass.name == "com.google.android.gms.ads.nativead.MediaView") {
            val parent = oldMediaView.parent as? ViewGroup
            if (parent != null) {
                val index = parent.indexOfChild(oldMediaView)
                val layoutParams = oldMediaView.layoutParams
                parent.removeView(oldMediaView)

                val nextGenMediaView = MediaView(context)
                nextGenMediaView.id = R.id.ad_media
                nextGenMediaView.layoutParams = layoutParams
                parent.addView(nextGenMediaView, index)
                OzLog.d(TAG, "Successfully swapped GMS MediaView with Next-Gen MediaView in layout")
            }
        }
    }
}
