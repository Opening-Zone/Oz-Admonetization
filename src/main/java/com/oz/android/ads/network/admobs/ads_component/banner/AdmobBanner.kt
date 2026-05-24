package com.oz.android.ads.network.admobs.ads_component.banner

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.ads.mediation.admob.AdMobAdapter
import com.oz.android.ads.network.admobs.ads_component.AdmobBase
import com.oz.android.ads.network.admobs.ads_component.toOzError
import com.oz.android.utils.listener.OzAdListener

/**
 * Class quản lý banner ads từ AdMob
 * Cung cấp 3 phương thức chính: load, show, và loadThenShow
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
     * Load quảng cáo banner
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    override fun load() {
        load(null)
    }

    /**
     * Load quảng cáo banner với container để tính toán size
     * @param container ViewGroup container để tính toán kích thước ad
     */
    fun load(container: ViewGroup?) {
        if (adView != null && isLoaded) {
            Log.d(TAG, "Ad already loaded")
            return
        }

        containerForSizeCalculation = container

        // Nếu container được cung cấp và chưa được measured, đợi measure xong
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
     * Tạo và load AdView
     */
    private fun createAndLoadAdView() {
        // Tạo AdView mới nếu chưa có
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

                        // Nếu có container đang chờ, tự động hiển thị
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

        // Load quảng cáo
        val adRequest = buildAdRequest()
        adView?.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading started${if (collapsiblePosition != null) " (collapsible: $collapsiblePosition)" else ""}")
    }

    /**
     * Hiển thị quảng cáo banner (implementation từ interface)
     * Lưu ý: Banner cần container, sử dụng show(container: ViewGroup) thay vì method này
     */
    override fun show() {
        Log.w(TAG, "show() called without container. Use show(container: ViewGroup) for banner ads")
        pendingContainer?.let { show(it) }
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

        // Remove parent from the AdView if it has one
        val parent = currentAdView.parent
        if (parent is ViewGroup) {
            parent.removeView(currentAdView)
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
        if (pendingContainer != null) {
            val container = pendingContainer!!
            loadThenShow(container)
        }
    }

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * @param container ViewGroup để chứa banner ad
     */
    fun loadThenShow(container: ViewGroup) {
        pendingContainer = container
        load(container)
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
     * Tách adView khỏi parent ViewGroup hiện tại (nếu có)
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
     * Destroy quảng cáo (gọi trong onDestroy của Activity/Fragment)
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
     * Tính toán kích thước ad dựa trên container thực tế
     * Nếu container được cung cấp, sử dụng kích thước của container
     * Nếu không, fallback về screen width
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

        // Use anchored adaptive banner - it automatically determines the best height
        // that fits within the container while maximizing ad performance
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        
        Log.d(TAG, "AdSize calculated: ${adSize.width}dp x ${adSize.height}dp (${adSize.getHeightInPixels(context)}px)")
        
        return adSize
    }

}

