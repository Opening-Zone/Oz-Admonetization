package com.oz.android.ads_core.admobs.banner

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.oz.android.OzAdsManager
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Abstract class representing Banner ads.
 * Extends [AdmobBase] and provides common logic for collapsible banners.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
abstract class AdmobBanner(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobBanner>? = null
) : AdmobBase<AdmobBanner>(context, adUnitId, listener) {

    var collapsiblePosition: String? = null
        private set

    fun setCollapsible(position: String?) {
        if (position != null && position != "top" && position != "bottom") {
            Log.w("AdmobBanner", "Invalid collapsible position: $position. Use 'top' or 'bottom'")
            return
        }
        collapsiblePosition = position
    }

    abstract fun load(container: ViewGroup?)
    abstract fun show(container: ViewGroup)
    abstract fun loadThenShow(container: ViewGroup)
    abstract fun detachFromParent()
    abstract fun destroy()
    abstract fun getAdSize(): AdSize?
    abstract fun pause()
    abstract fun resume()

    companion object {
        fun create(
            context: Context,
            adUnitId: String,
            listener: OzAdListener<AdmobBanner>? = null
        ): AdmobBanner {
            return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                AdmobNextBanner(context, adUnitId, listener)
            } else {
                AdmobStandardBanner(context, adUnitId, listener)
            }
        }
    }
}
