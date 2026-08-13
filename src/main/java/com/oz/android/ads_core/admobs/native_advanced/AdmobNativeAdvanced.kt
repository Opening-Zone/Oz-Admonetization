package com.oz.android.ads_core.admobs.native_advanced

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.oz.android.OzAdsManager
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Interface representing Native Advanced ads.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
interface AdmobNativeAdvanced {
    fun setMediaRatio(ratio: Int)
    fun load()
    fun show()
    fun show(
        container: ViewGroup,
        nativeAdView: View,
        populateCallback: ((Any, View) -> Unit)? = null
    )
    fun loadThenShow()
    fun loadThenShow(
        container: ViewGroup,
        nativeAdView: View,
        populateCallback: ((Any, View) -> Unit)? = null
    )
    fun isAdLoaded(): Boolean
    fun getCurrentNativeAd(): Any?
    fun destroy()

    companion object {
        fun create(
            context: Context,
            adUnitId: String,
            listener: OzAdListener<AdmobNativeAdvanced>? = null
        ): AdmobNativeAdvanced {
            return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                AdmobNextNativeAdvanced(context, adUnitId, listener)
            } else {
                AdmobStandardNativeAdvanced(context, adUnitId, listener)
            }
        }
    }
}
