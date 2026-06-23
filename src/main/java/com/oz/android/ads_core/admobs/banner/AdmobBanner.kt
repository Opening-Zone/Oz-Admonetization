package com.oz.android.ads_core.admobs.banner

import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.oz.android.OzAdsManager
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Interface representing Banner ads.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
interface AdmobBanner {
    val collapsiblePosition: String?
    fun setCollapsible(position: String?)
    fun setCollapsibleTop()
    fun setCollapsibleBottom()
    fun load()
    fun load(container: ViewGroup?)
    fun show()
    fun show(container: ViewGroup)
    fun loadThenShow()
    fun loadThenShow(container: ViewGroup)
    fun pause()
    fun resume()
    fun detachFromParent()
    fun destroy()
    fun getAdSize(): AdSize?

    companion object {
        const val COLLAPSIBLE_TOP = "top"
        const val COLLAPSIBLE_BOTTOM = "bottom"

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
