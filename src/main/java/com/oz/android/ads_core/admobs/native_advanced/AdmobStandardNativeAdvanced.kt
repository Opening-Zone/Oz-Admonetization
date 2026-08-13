package com.oz.android.ads_core.admobs.native_advanced

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
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
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.ads_core.R
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Proxy

/**
 * Standard SDK implementation of Native Advanced ads.
 */
class AdmobStandardNativeAdvanced(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobNativeAdvanced>? = null
) : AdmobBase<AdmobNativeAdvanced>(context, adUnitId, listener), AdmobNativeAdvanced {

    private var currentNativeAd: Any? = null
    private var pendingContainer: ViewGroup? = null
    private var pendingNativeAdView: View? = null
    private var onAdLoadedCallback: ((Any) -> Unit)? = null
    private var mediaAspectRatio: Int? = null

    override fun setMediaRatio(ratio: Int) {
        this.mediaAspectRatio = ratio
    }

    companion object {
        private const val TAG = "AdmobStandardNativeAdvanced"
    }

    override fun load() {
        if (currentNativeAd != null) {
            OzLog.d(TAG, "Ad already loaded")
            listener?.onAdLoaded(this)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val builder = AdLoader.Builder(context, adUnitId)

            try {
                val listenerClass = Class.forName("com.google.android.gms.ads.nativead.NativeAd\$OnNativeAdLoadedListener")
                val proxyListener = Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onNativeAdLoaded") {
                        val nativeAd = args[0]
                        Handler(Looper.getMainLooper()).post {
                            handleNativeAdLoaded(nativeAd)
                        }
                    }
                    null
                }
                
                val forNativeAdMethod = builder.javaClass.getMethod("forNativeAd", listenerClass)
                forNativeAdMethod.invoke(builder, proxyListener)
            } catch (e: Exception) {
                OzLog.e(TAG, "Failed to register GMS OnNativeAdLoadedListener reflectively", e)
            }

            var adOptions: Any? = null
            try {
                val videoOptionsClass = Class.forName("com.google.android.gms.ads.VideoOptions")
                val videoOptionsBuilderClass = Class.forName("com.google.android.gms.ads.VideoOptions\$Builder")
                val videoOptionsBuilder = videoOptionsBuilderClass.getDeclaredConstructor().newInstance()
                videoOptionsBuilderClass.getMethod("setStartMuted", Boolean::class.java).invoke(videoOptionsBuilder, true)
                val videoOptions = videoOptionsBuilderClass.getMethod("build").invoke(videoOptionsBuilder)
                
                val adOptionsBuilderClass = Class.forName("com.google.android.gms.ads.nativead.NativeAdOptions\$Builder")
                val adOptionsBuilder = adOptionsBuilderClass.getDeclaredConstructor().newInstance()
                adOptionsBuilderClass.getMethod("setVideoOptions", videoOptionsClass).invoke(adOptionsBuilder, videoOptions)
                
                mediaAspectRatio?.let { ratio ->
                    adOptionsBuilderClass.getMethod("setMediaAspectRatio", Int::class.java).invoke(adOptionsBuilder, ratio)
                }
                
                adOptions = adOptionsBuilderClass.getMethod("build").invoke(adOptionsBuilder)
            } catch (e: Exception) {
                OzLog.e(TAG, "Failed to build NativeAdOptions reflectively", e)
            }

            if (adOptions != null) {
                try {
                    val withNativeAdOptionsMethod = builder.javaClass.getMethod("withNativeAdOptions", Class.forName("com.google.android.gms.ads.nativead.NativeAdOptions"))
                    withNativeAdOptionsMethod.invoke(builder, adOptions)
                } catch (e: Exception) {
                    OzLog.e(TAG, "Failed to call withNativeAdOptions reflectively", e)
                }
            }

            val adLoader = builder
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            val error =
                                "domain: ${loadAdError.domain}, code: ${loadAdError.code}, message: ${loadAdError.message}"
                            OzLog.e(TAG, "Native ad failed to load: $error")
                            currentNativeAd = null
                            pendingContainer = null
                            pendingNativeAdView = null

                            listener?.onAdFailedToLoad(loadAdError.toOzError())
                        }

                        override fun onAdClicked() {
                            OzLog.d(TAG, "Native ad was clicked")
                            listener?.onAdClicked()
                        }

                        override fun onAdImpression() {
                            OzLog.d(TAG, "Native ad recorded an impression")
                            listener?.onAdImpression()
                        }
                    }
                )
                .build()

            withContext(Dispatchers.Main) {
                adLoader.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun handleNativeAdLoaded(nativeAd: Any) {
        var activityDestroyed = false
        if (context is AppCompatActivity) {
            activityDestroyed = context.isDestroyed
            if (activityDestroyed || context.isFinishing || context.isChangingConfigurations) {
                destroyAd(nativeAd)
                return
            }
        }

        destroyAd(currentNativeAd)
        currentNativeAd = nativeAd
        
        try {
            val responseInfo = nativeAd.javaClass.getMethod("getResponseInfo").invoke(nativeAd) as? com.google.android.gms.ads.ResponseInfo
            val paidListener = getOnPaidListener(responseInfo)
            nativeAd.javaClass.getMethod("setOnPaidEventListener", Class.forName("com.google.android.gms.ads.OnPaidEventListener"))
                .invoke(nativeAd, paidListener)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS PaidEventListener reflectively", e)
        }

        OzLog.d(TAG, "Native ad loaded successfully")
        listener?.onAdLoaded(this@AdmobStandardNativeAdvanced)
        onAdLoadedCallback?.invoke(nativeAd)

        pendingContainer?.let { container ->
            pendingNativeAdView?.let { nativeAdView ->
                show(container, nativeAdView)
                pendingContainer = null
                pendingNativeAdView = null
            }
        }
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
        val currentAd = currentNativeAd
        if (currentAd == null) {
            OzLog.w(TAG, "NativeAd is null. Call load() first")
            pendingContainer = container
            pendingNativeAdView = nativeAdView
            OzEventLogger.logAdSkip(context, adUnitId, "native", "ad_null")
            return
        }

        val gmsNativeAdView = try {
            val nativeAdViewClass = Class.forName("com.google.android.gms.ads.nativead.NativeAdView")
            nativeAdViewClass.getDeclaredConstructor(Context::class.java).newInstance(context) as ViewGroup
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to construct standard NativeAdView dynamically", e)
            null
        }

        if (gmsNativeAdView != null) {
            gmsNativeAdView.layoutParams = nativeAdView.layoutParams
            if (nativeAdView.parent != null) {
                (nativeAdView.parent as ViewGroup).removeView(nativeAdView)
            }
            gmsNativeAdView.addView(nativeAdView)
            
            bindGmsStandardViews(gmsNativeAdView)

            if (populateCallback != null) {
                populateCallback.invoke(currentAd, gmsNativeAdView)
            } else {
                populateNativeAdView(currentAd, gmsNativeAdView)
            }

            container.removeAllViews()
            container.addView(gmsNativeAdView)
        } else {
            // Fallback (should not happen)
            if (populateCallback != null) {
                populateCallback.invoke(currentAd, nativeAdView)
            } else {
                populateNativeAdView(currentAd, nativeAdView)
            }
            container.removeAllViews()
            if (nativeAdView.parent != null) {
                (nativeAdView.parent as ViewGroup).removeView(nativeAdView)
            }
            container.addView(nativeAdView)
        }
        OzLog.d(TAG, "Native ad displayed in container")
    }

    override fun loadThenShow(
        container: ViewGroup,
        nativeAdView: View,
        populateCallback: ((Any, View) -> Unit)?
    ) {
        pendingContainer = container
        pendingNativeAdView = nativeAdView
        if (populateCallback != null) {
            onAdLoadedCallback = { ad ->
                populateCallback.invoke(ad, nativeAdView)
            }
        }
        load()
    }

    private fun populateNativeAdView(
        nativeAd: Any,
        nativeAdView: View
    ) {
        try {
            val mediaContent = nativeAd.javaClass.getMethod("getMediaContent").invoke(nativeAd)
            if (mediaContent != null) {
                val getMediaViewMethod = nativeAdView.javaClass.getMethod("getMediaView")
                val mediaView = getMediaViewMethod.invoke(nativeAdView)
                if (mediaView != null) {
                    val setMediaContentMethod = mediaView.javaClass.getMethod("setMediaContent", Class.forName("com.google.android.gms.ads.MediaContent"))
                    setMediaContentMethod.invoke(mediaView, mediaContent)
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS MediaContent reflectively", e)
        }

        try {
            val getHeadlineViewMethod = nativeAdView.javaClass.getMethod("getHeadlineView")
            val headlineView = getHeadlineViewMethod.invoke(nativeAdView) as? View
            if (headlineView != null) {
                val headline = nativeAd.javaClass.getMethod("getHeadline").invoke(nativeAd) as? String
                (headlineView as? TextView)?.text = headline
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Headline reflectively", e)
        }

        try {
            val getBodyViewMethod = nativeAdView.javaClass.getMethod("getBodyView")
            val bodyView = getBodyViewMethod.invoke(nativeAdView) as? View
            if (bodyView != null) {
                val body = nativeAd.javaClass.getMethod("getBody").invoke(nativeAd) as? String
                if (body == null) {
                    bodyView.visibility = View.INVISIBLE
                } else {
                    bodyView.visibility = View.VISIBLE
                    (bodyView as? TextView)?.text = body
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Body reflectively", e)
        }

        try {
            val getCallToActionViewMethod = nativeAdView.javaClass.getMethod("getCallToActionView")
            val ctaView = getCallToActionViewMethod.invoke(nativeAdView) as? View
            if (ctaView != null) {
                val cta = nativeAd.javaClass.getMethod("getCallToAction").invoke(nativeAd) as? String
                if (cta == null) {
                    ctaView.visibility = View.INVISIBLE
                } else {
                    ctaView.visibility = View.VISIBLE
                    (ctaView as? TextView)?.text = cta
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS CallToAction reflectively", e)
        }

        try {
            val getIconViewMethod = nativeAdView.javaClass.getMethod("getIconView")
            val iconView = getIconViewMethod.invoke(nativeAdView) as? View
            if (iconView != null) {
                val icon = nativeAd.javaClass.getMethod("getIcon").invoke(nativeAd)
                if (icon == null) {
                    iconView.visibility = View.GONE
                } else {
                    iconView.visibility = View.VISIBLE
                    val getDrawableMethod = icon.javaClass.getMethod("getDrawable")
                    val drawable = getDrawableMethod.invoke(icon) as? android.graphics.drawable.Drawable
                    (iconView as? ImageView)?.setImageDrawable(drawable)
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Icon reflectively", e)
        }

        try {
            val getPriceViewMethod = nativeAdView.javaClass.getMethod("getPriceView")
            val priceView = getPriceViewMethod.invoke(nativeAdView) as? View
            if (priceView != null) {
                val price = nativeAd.javaClass.getMethod("getPrice").invoke(nativeAd) as? String
                if (price == null) {
                    priceView.visibility = View.INVISIBLE
                } else {
                    priceView.visibility = View.VISIBLE
                    (priceView as? TextView)?.text = price
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Price reflectively", e)
        }

        try {
            val getStoreViewMethod = nativeAdView.javaClass.getMethod("getStoreView")
            val storeView = getStoreViewMethod.invoke(nativeAdView) as? View
            if (storeView != null) {
                val store = nativeAd.javaClass.getMethod("getStore").invoke(nativeAd) as? String
                if (store == null) {
                    storeView.visibility = View.INVISIBLE
                } else {
                    storeView.visibility = View.VISIBLE
                    (storeView as? TextView)?.text = store
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Store reflectively", e)
        }

        try {
            val getStarRatingViewMethod = nativeAdView.javaClass.getMethod("getStarRatingView")
            val starRatingView = getStarRatingViewMethod.invoke(nativeAdView) as? View
            if (starRatingView != null) {
                val starRating = nativeAd.javaClass.getMethod("getStarRating").invoke(nativeAd) as? Double
                if (starRating == null) {
                    starRatingView.visibility = View.INVISIBLE
                } else {
                    starRatingView.visibility = View.VISIBLE
                    (starRatingView as? RatingBar)?.rating = starRating.toFloat()
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS StarRating reflectively", e)
        }

        try {
            val getAdvertiserViewMethod = nativeAdView.javaClass.getMethod("getAdvertiserView")
            val advertiserView = getAdvertiserViewMethod.invoke(nativeAdView) as? View
            if (advertiserView != null) {
                val advertiser = nativeAd.javaClass.getMethod("getAdvertiser").invoke(nativeAd) as? String
                if (advertiser == null) {
                    advertiserView.visibility = View.INVISIBLE
                } else {
                    advertiserView.visibility = View.VISIBLE
                    (advertiserView as? TextView)?.text = advertiser
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS Advertiser reflectively", e)
        }

        try {
            val setNativeAdMethod = nativeAdView.javaClass.getMethod("setNativeAd", Class.forName("com.google.android.gms.ads.nativead.NativeAd"))
            setNativeAdMethod.invoke(nativeAdView, nativeAd)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to call GMS setNativeAd reflectively", e)
        }
    }

    private fun bindGmsStandardViews(gmsNativeAdView: View) {
        try {
            val setHeadlineViewMethod = gmsNativeAdView.javaClass.getMethod("setHeadlineView", View::class.java)
            setHeadlineViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_headline))
            
            val setBodyViewMethod = gmsNativeAdView.javaClass.getMethod("setBodyView", View::class.java)
            setBodyViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_body))
            
            val setCallToActionViewMethod = gmsNativeAdView.javaClass.getMethod("setCallToActionView", View::class.java)
            setCallToActionViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_call_to_action))
            
            val setIconViewMethod = gmsNativeAdView.javaClass.getMethod("setIconView", View::class.java)
            setIconViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_app_icon))
            
            val setPriceViewMethod = gmsNativeAdView.javaClass.getMethod("setPriceView", View::class.java)
            setPriceViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_price))
            
            val setStarRatingViewMethod = gmsNativeAdView.javaClass.getMethod("setStarRatingView", View::class.java)
            setStarRatingViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_stars))
            
            val setStoreViewMethod = gmsNativeAdView.javaClass.getMethod("setStoreView", View::class.java)
            setStoreViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_store))
            
            val setAdvertiserViewMethod = gmsNativeAdView.javaClass.getMethod("setAdvertiserView", View::class.java)
            setAdvertiserViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_advertiser))
            
            val setMediaViewMethod = gmsNativeAdView.javaClass.getMethod("setMediaView", Class.forName("com.google.android.gms.ads.nativead.MediaView"))
            setMediaViewMethod.invoke(gmsNativeAdView, gmsNativeAdView.findViewById(R.id.ad_media))
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to bind GMS standard views reflectively", e)
        }
    }

    override fun isAdLoaded(): Boolean {
        return currentNativeAd != null
    }

    override fun getCurrentNativeAd(): Any? {
        return currentNativeAd
    }

    private fun destroyAd(ad: Any?) {
        if (ad == null) return
        try {
            ad.javaClass.getMethod("destroy").invoke(ad)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to destroy ad reflectively", e)
        }
    }

    override fun destroy() {
        destroyAd(currentNativeAd)
        currentNativeAd = null
        pendingContainer = null
        pendingNativeAdView = null
        OzLog.d(TAG, "Native ad destroyed")
    }
}
