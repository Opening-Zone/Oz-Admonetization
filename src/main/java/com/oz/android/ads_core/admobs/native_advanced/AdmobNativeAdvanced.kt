package com.oz.android.ads_core.admobs.native_advanced

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class managing native advanced ads from AdMob
 * Provides 3 main methods: load, show, and loadThenShow
 */
class AdmobNativeAdvanced(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobNativeAdvanced>? = null
) : AdmobBase<AdmobNativeAdvanced>(context, adUnitId, listener) {

    private var currentNativeAd: NativeAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var pendingContainer: ViewGroup? = null
    private var pendingNativeAdView: NativeAdView? = null
    private var onAdLoadedCallback: ((NativeAd) -> Unit)? = null
    private var mediaAspectRatio: Int? = null

    fun setMediaRatio(ratio: Int) {
        this.mediaAspectRatio = ratio
    }

    companion object {
        private const val TAG = "AdmobNativeAdvanced"
    }

    /**
     * Load native advanced ad
     * The ad will be loaded but not shown yet
     */
    override fun load() {
        // Request a new ad if one isn't already loaded or loading
        if (adIsLoading || currentNativeAd != null) {
            Log.d(TAG, "Ad already loading or loaded")
            if (currentNativeAd != null) {
                listener?.onAdLoaded(this)
            }
            return
        }

        adIsLoading = true

        // It is recommended to call AdLoader.Builder on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            val builder = AdLoader.Builder(context, adUnitId)

            builder.forNativeAd { nativeAd ->
                // OnLoadedListener implementation
                // If this callback occurs after the activity is destroyed, you must call
                // destroy and return or you may get a memory leak
                var activityDestroyed = false
                if (context is AppCompatActivity) {
                    activityDestroyed = context.isDestroyed
                    if (activityDestroyed || context.isFinishing || context.isChangingConfigurations) {
                        nativeAd.destroy()
                        return@forNativeAd
                    }
                }

                // You must call destroy on old ads when you are done with them,
                // otherwise you will have a memory leak
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd
                isLoaded = true
                adIsLoading = false
                currentNativeAd?.setOnPaidEventListener(getOnPaidListener(currentNativeAd!!.responseInfo))

                Log.d(TAG, "Native ad loaded successfully")

                // Notify listener
                listener?.onAdLoaded(this@AdmobNativeAdvanced)

                // Call callback if provided
                onAdLoadedCallback?.invoke(nativeAd)

                // If a container and nativeAdView are waiting, show automatically
                pendingContainer?.let { container ->
                    pendingNativeAdView?.let { nativeAdView ->
                        show(container, nativeAdView)
                        pendingContainer = null
                        pendingNativeAdView = null
                    }
                }
            }

            val videoOptions = VideoOptions.Builder()
                .setStartMuted(true)
                .build()

            val adOptionsBuilder = NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)

            mediaAspectRatio?.let {
                adOptionsBuilder.setMediaAspectRatio(it)
            }

            val adOptions = adOptionsBuilder.build()

            builder.withNativeAdOptions(adOptions)

            val adLoader = builder
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            val error =
                                "domain: ${loadAdError.domain}, code: ${loadAdError.code}, message: ${loadAdError.message}"
                            Log.e(TAG, "Native ad failed to load: $error")
                            currentNativeAd = null
                            isLoaded = false
                            adIsLoading = false
                            pendingContainer = null
                            pendingNativeAdView = null

                            listener?.onAdFailedToLoad(loadAdError.toOzError())
                        }

                        override fun onAdClicked() {
                            Log.d(TAG, "Native ad was clicked")
                            listener?.onAdClicked()
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "Native ad recorded an impression")
                            listener?.onAdImpression()
                        }
                    }
                )
                .build()

            withContext(Dispatchers.Main){
                adLoader.loadAd(AdRequest.Builder().build())
            }
        }
    }

    /**
     * Show native advanced ad (implementation from interface)
     * Note: Native ad requires a container and a NativeAdView, use show(container, nativeAdView) instead of this method
     */
    override fun show() {
        Log.w(
            TAG,
            "show() called without container and NativeAdView. Use show(container: ViewGroup, nativeAdView: NativeAdView) for native ads"
        )
    }

    /**
     * Show native advanced ad in the container
     * @param container ViewGroup to contain the native ad view
     * @param nativeAdView NativeAdView set up with view components
     * @param populateCallback Callback to populate data into NativeAdView (optional)
     */
    fun show(
        container: ViewGroup,
        nativeAdView: NativeAdView,
        populateCallback: ((NativeAd, NativeAdView) -> Unit)? = null
    ) {
        val currentAd = currentNativeAd
        if (currentAd == null) {
            Log.w(TAG, "NativeAd is null. Call load() first")
            pendingContainer = container
            pendingNativeAdView = nativeAdView
            return
        }

        if (!isLoaded) {
            Log.w(TAG, "Ad not loaded yet. It will be shown automatically when loaded")
            pendingContainer = container
            pendingNativeAdView = nativeAdView
            return
        }

        // Populate native ad view
        if (populateCallback != null) {
            populateCallback.invoke(currentAd, nativeAdView)
        } else {
            populateNativeAdView(currentAd, nativeAdView)
        }

        // Clear old views in the container
        container.removeAllViews()

        // Add NativeAdView to the container
        // Ensure nativeAdView doesn't have a parent
        if (nativeAdView.parent != null) {
            (nativeAdView.parent as ViewGroup).removeView(nativeAdView)
        }
        container.addView(nativeAdView)
        Log.d(TAG, "Native ad displayed in container")
    }

    /**
     * Load and automatically show the ad when loading finishes (implementation from interface)
     * Note: Native ad requires a container and a NativeAdView, use loadThenShow(container, nativeAdView) instead of this method
     */
    override fun loadThenShow() {
        Log.w(
            TAG,
            "loadThenShow() called without container and NativeAdView. Use loadThenShow(container: ViewGroup, nativeAdView: NativeAdView) for native ads"
        )
    }

    /**
     * Load and automatically show the ad when loading finishes
     * @param container ViewGroup to contain the native ad view
     * @param nativeAdView NativeAdView set up with view components
     * @param populateCallback Callback to populate data into NativeAdView (optional)
     */
    fun loadThenShow(
        container: ViewGroup,
        nativeAdView: NativeAdView,
        populateCallback: ((NativeAd, NativeAdView) -> Unit)? = null
    ) {
        pendingContainer = container
        pendingNativeAdView = nativeAdView
        if (populateCallback != null) {
            // Store callback for later use
            onAdLoadedCallback = { ad ->
                populateCallback.invoke(ad, nativeAdView)
            }
        }
        load()
    }

    /**
     * Populate native ad view with data from native ad
     * This is a basic implementation, users can override it using populateCallback
     */
    private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
        // Set the media view if available
        nativeAdView.mediaView?.let { mediaView ->
            nativeAd.mediaContent?.let { mediaView.setMediaContent(it) }
        }

        // Set headline
        nativeAdView.headlineView?.let { headlineView ->
            (headlineView as? TextView)?.text = nativeAd.headline
        }

        // Set body
        nativeAdView.bodyView?.let { bodyView ->
            if (nativeAd.body == null) {
                bodyView.visibility = View.INVISIBLE
            } else {
                bodyView.visibility = View.VISIBLE
                (bodyView as? TextView)?.text = nativeAd.body
            }
        }

        // Set call to action
        nativeAdView.callToActionView?.let { ctaView ->
            if (nativeAd.callToAction == null) {
                ctaView.visibility = View.INVISIBLE
            } else {
                ctaView.visibility = View.VISIBLE
                (ctaView as? TextView)?.text = nativeAd.callToAction
            }
        }

        // Set icon
        nativeAdView.iconView?.let { iconView ->
            if (nativeAd.icon == null) {
                iconView.visibility = View.GONE
            } else {
                iconView.visibility = View.VISIBLE
                (iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            }
        }

        // Set price
        nativeAdView.priceView?.let { priceView ->
            if (nativeAd.price == null) {
                priceView.visibility = View.INVISIBLE
            } else {
                priceView.visibility = View.VISIBLE
                (priceView as? TextView)?.text = nativeAd.price
            }
        }

        // Set store
        nativeAdView.storeView?.let { storeView ->
            if (nativeAd.store == null) {
                storeView.visibility = View.INVISIBLE
            } else {
                storeView.visibility = View.VISIBLE
                (storeView as? TextView)?.text = nativeAd.store
            }
        }

        // Set star rating
        nativeAdView.starRatingView?.let { starRatingView ->
            if (nativeAd.starRating == null) {
                starRatingView.visibility = View.INVISIBLE
            } else {
                starRatingView.visibility = View.VISIBLE
                (starRatingView as? RatingBar)?.rating =
                    nativeAd.starRating!!.toFloat()
            }
        }

        // Set advertiser
        nativeAdView.advertiserView?.let { advertiserView ->
            if (nativeAd.advertiser == null) {
                advertiserView.visibility = View.INVISIBLE
            } else {
                advertiserView.visibility = View.VISIBLE
                (advertiserView as? TextView)?.text = nativeAd.advertiser
            }
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad
        nativeAdView.setNativeAd(nativeAd)
    }

    /**
     * Check if the ad has been loaded
     * @return true if the ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean {
        return isLoaded && currentNativeAd != null
    }

    /**
     * Get current NativeAd (if loaded)
     * @return NativeAd if loaded, null otherwise
     */
    fun getCurrentNativeAd(): NativeAd? {
        return currentNativeAd
    }


    /**
     * Destroy ad (call in onDestroy of Activity/Fragment)
     */
    fun destroy() {
        currentNativeAd?.destroy()
        currentNativeAd = null
        isLoaded = false
        adIsLoading = false
        pendingContainer = null
        pendingNativeAdView = null
        Log.d(TAG, "Native ad destroyed")
    }
}
