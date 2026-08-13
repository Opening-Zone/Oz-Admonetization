package com.oz.android.ads_core.admobs.app_open

import android.app.Activity
import android.content.Context
import com.oz.android.OzAdsManager
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Interface representing App Open ads.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
interface AdmobAppOpen {
    fun load()
    fun show(activity: Activity)
    fun loadThenShow(activity: Activity)
    fun isAdAvailable(): Boolean
    fun isAdLoaded(): Boolean

    companion object {
        fun create(
            context: Context,
            adUnitId: String,
            listener: OzAdListener<AdmobAppOpen>? = null
        ): AdmobAppOpen {
            return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                AdmobNextAppOpen(context, adUnitId, listener)
            } else {
                AdmobStandardAppOpen(context, adUnitId, listener)
            }
        }
    }
}
