package com.oz.android.ads_core.admobs.interstitial

import android.app.Activity
import android.content.Context
import com.oz.android.OzAdsManager
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Interface representing Interstitial ads.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
interface AdmobInterstitial {
    fun load()
    fun show(activity: Activity)
    fun loadThenShow(activity: Activity, showOverlay: Boolean = false)
    fun isAdLoaded(): Boolean

    companion object {
        fun create(
            context: Context,
            adUnitId: String,
            listener: OzAdListener<AdmobInterstitial>? = null
        ): AdmobInterstitial {
            return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                AdmobNextInterstitial(context, adUnitId, listener)
            } else {
                AdmobStandardInterstitial(context, adUnitId, listener)
            }
        }
    }
}
