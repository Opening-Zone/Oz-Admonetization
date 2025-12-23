package com.oz.android.ads.network.admobs.ads_component.native_advanced

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.ads.network.admobs.ads_component.IAdmobAds
import com.oz.android.ads.network.admobs.ads_component.OzAdmobListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Class quản lý native advanced ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
class AdmobNativeAdvanced(
    context: Context,
    adUnitId: String,
    listener: OzAdmobListener<AdmobNativeAdvanced>? = null
) : AdmobBase<AdmobNativeAdvanced>(context, adUnitId, listener), IAdmobAds {

    private var currentNativeAd: NativeAd? = null
    private var isLoaded = false
    private var adIsLoading = false
    private var pendingContainer: ViewGroup? = null
    private var pendingNativeAdView: NativeAdView? = null
    private var onAdLoadedCallback: ((NativeAd) -> Unit)? = null

    companion object {
        private const val TAG = "AdmobNativeAdvanced"
    }

    /**
     * Load quảng cáo native advanced
     * Quảng cáo sẽ được load nhưng chưa hiển thị
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        activityDestroyed = context.isDestroyed
                    }
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

                Log.d(TAG, "Native ad loaded successfully")

                // Notify listener
                listener?.onAdLoaded(this@AdmobNativeAdvanced)

                // Call callback if provided
                onAdLoadedCallback?.invoke(nativeAd)

                // Nếu có container và nativeAdView đang chờ, tự động hiển thị
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

            val adOptions = NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)
                .build()

            builder.withNativeAdOptions(adOptions)

            val adLoader = builder
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            val error = "domain: ${loadAdError.domain}, code: ${loadAdError.code}, message: ${loadAdError.message}"
                            Log.e(TAG, "Native ad failed to load: $error")
                            currentNativeAd = null
                            isLoaded = false
                            adIsLoading = false
                            pendingContainer = null
                            pendingNativeAdView = null

                            listener?.onAdFailedToLoad(loadAdError)
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

            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    /**
     * Hiển thị quảng cáo native advanced (implementation từ interface)
     * Lưu ý: Native ad cần container và NativeAdView, sử dụng show(container, nativeAdView) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without container and NativeAdView. Use show(container: ViewGroup, nativeAdView: NativeAdView) for native ads")
    }

    /**
     * Hiển thị quảng cáo native advanced vào container
     * @param container ViewGroup để chứa native ad view
     * @param nativeAdView NativeAdView đã được setup với các view components
     * @param populateCallback Callback để populate data vào NativeAdView (optional)
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

        // Xóa các view cũ trong container
        container.removeAllViews()

        // Thêm NativeAdView vào container
        // Ensure nativeAdView doesn't have a parent
        if (nativeAdView.parent != null) {
            (nativeAdView.parent as ViewGroup).removeView(nativeAdView)
        }
        container.addView(nativeAdView)
        Log.d(TAG, "Native ad displayed in container")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Native ad cần container và NativeAdView, sử dụng loadThenShow(container, nativeAdView) thay vì method này
     */
    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() called without container and NativeAdView. Use loadThenShow(container: ViewGroup, nativeAdView: NativeAdView) for native ads")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param container ViewGroup để chứa native ad view
     * @param nativeAdView NativeAdView đã được setup với các view components
     * @param populateCallback Callback để populate data vào NativeAdView (optional)
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
     * Populate native ad view với data từ native ad
     * Đây là implementation cơ bản, người dùng có thể override bằng populateCallback
     */
    private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
        // Set the media view if available
        nativeAdView.mediaView?.let { mediaView ->
            nativeAdView.mediaView = mediaView
            nativeAd.mediaContent?.let { mediaView.setMediaContent(it) }
        }

        // Set headline
        nativeAdView.headlineView?.let { headlineView ->
            (headlineView as? android.widget.TextView)?.text = nativeAd.headline
        }

        // Set body
        nativeAdView.bodyView?.let { bodyView ->
            if (nativeAd.body == null) {
                bodyView.visibility = android.view.View.INVISIBLE
            } else {
                bodyView.visibility = android.view.View.VISIBLE
                (bodyView as? android.widget.TextView)?.text = nativeAd.body
            }
        }

        // Set call to action
        nativeAdView.callToActionView?.let { ctaView ->
            if (nativeAd.callToAction == null) {
                ctaView.visibility = android.view.View.INVISIBLE
            } else {
                ctaView.visibility = android.view.View.VISIBLE
                (ctaView as? android.widget.TextView)?.text = nativeAd.callToAction
            }
        }

        // Set icon
        nativeAdView.iconView?.let { iconView ->
            if (nativeAd.icon == null) {
                iconView.visibility = android.view.View.GONE
            } else {
                iconView.visibility = android.view.View.VISIBLE
                (iconView as? android.widget.ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            }
        }

        // Set price
        nativeAdView.priceView?.let { priceView ->
            if (nativeAd.price == null) {
                priceView.visibility = android.view.View.INVISIBLE
            } else {
                priceView.visibility = android.view.View.VISIBLE
                (priceView as? android.widget.TextView)?.text = nativeAd.price
            }
        }

        // Set store
        nativeAdView.storeView?.let { storeView ->
            if (nativeAd.store == null) {
                storeView.visibility = android.view.View.INVISIBLE
            } else {
                storeView.visibility = android.view.View.VISIBLE
                (storeView as? android.widget.TextView)?.text = nativeAd.store
            }
        }

        // Set star rating
        nativeAdView.starRatingView?.let { starRatingView ->
            if (nativeAd.starRating == null) {
                starRatingView.visibility = android.view.View.INVISIBLE
            } else {
                starRatingView.visibility = android.view.View.VISIBLE
                (starRatingView as? android.widget.RatingBar)?.rating = nativeAd.starRating!!.toFloat()
            }
        }

        // Set advertiser
        nativeAdView.advertiserView?.let { advertiserView ->
            if (nativeAd.advertiser == null) {
                advertiserView.visibility = android.view.View.INVISIBLE
            } else {
                advertiserView.visibility = android.view.View.VISIBLE
                (advertiserView as? android.widget.TextView)?.text = nativeAd.advertiser
            }
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad
        nativeAdView.setNativeAd(nativeAd)
    }

    /**
     * Kiểm tra xem ad đã được load chưa
     * @return true nếu ad đã load, false nếu chưa
     */
    fun isAdLoaded(): Boolean {
        return isLoaded && currentNativeAd != null
    }

    /**
     * Lấy NativeAd hiện tại (nếu đã load)
     * @return NativeAd nếu đã load, null nếu chưa
     */
    fun getCurrentNativeAd(): NativeAd? {
        return currentNativeAd
    }


    /**
     * Destroy quảng cáo (gọi trong onDestroy của Activity/Fragment)
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
