package com.oz.android.ads.network.admobs.ads_component.banner

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.oz.android.ads.network.admobs.ads_component.IAdmobAds

/**
 * Class quản lý banner ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
class AdmobBanner(
    private val context: Context,
    private val adUnitId: String
) : IAdmobAds {
    private var adView: AdView? = null
    private var isLoaded = false
    private var pendingContainer: ViewGroup? = null

    companion object {
        private const val TAG = "AdmobBanner"
    }

    /**
     * Load quảng cáo banner
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    override fun load() {
        if (adView != null && isLoaded) {
            Log.d(TAG, "Ad already loaded")
            return
        }

        // Tạo AdView mới nếu chưa có
        if (adView == null) {
            adView = AdView(context).apply {
                this.adUnitId = this@AdmobBanner.adUnitId
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 360))
                
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isLoaded = true
                        Log.d(TAG, "Banner ad loaded successfully")
                        
                        // Nếu có container đang chờ, tự động hiển thị
                        pendingContainer?.let { container ->
                            show(container)
                            pendingContainer = null
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoaded = false
                        Log.e(TAG, "Banner ad failed to load: ${error.message}")
                        pendingContainer = null
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Banner ad was clicked")
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "Banner ad recorded an impression")
                    }
                }
            }
        }

        // Load quảng cáo
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading started")
    }

    /**
     * Hiển thị quảng cáo banner (implementation từ interface)
     * Lưu ý: Banner cần container, sử dụng show(container: ViewGroup) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without container. Use show(container: ViewGroup) for banner ads")
    }

    /**
     * Hiển thị quảng cáo banner vào container
     * @param container ViewGroup để chứa banner ad
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

        // Xóa các view cũ trong container
        container.removeAllViews()
        
        // Thêm AdView vào container
        container.addView(currentAdView)
        Log.d(TAG, "Banner ad displayed in container")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong (implementation từ interface)
     * Lưu ý: Banner cần container, sử dụng loadThenShow(container: ViewGroup) thay vì method này
     */
    override fun loadThenShow() {
        Log.w(TAG, "loadThenShow() called without container. Use loadThenShow(container: ViewGroup) for banner ads")
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param container ViewGroup để chứa banner ad
     */
    fun loadThenShow(container: ViewGroup) {
        pendingContainer = container
        load()
    }

    /**
     * Pause quảng cáo (gọi trong onPause của Activity/Fragment)
     */
    fun pause() {
        adView?.pause()
    }

    /**
     * Resume quảng cáo (gọi trong onResume của Activity/Fragment)
     */
    fun resume() {
        adView?.resume()
    }

    /**
     * Destroy quảng cáo (gọi trong onDestroy của Activity/Fragment)
     */
    fun destroy() {
        adView?.destroy()
        adView = null
        isLoaded = false
        pendingContainer = null
    }
}

