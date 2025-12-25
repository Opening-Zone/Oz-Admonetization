package com.oz.android.ads.network.admobs.ads_component

import android.content.Context
import androidx.annotation.RestrictTo
import com.google.android.gms.ads.AdError
import com.oz.android.wrapper.OzAdError
import com.oz.android.wrapper.OzAdListener

/**
 * @param AdType The type of the Ad Object (AdView, InterstitialAd, NativeAd, etc.)
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class AdmobBase<AdType>(
    val context: Context,
    var adUnitId: String,
    var listener: OzAdListener<AdType>? = null // Default to null = Optional
) {

    /**
     * Standard Load method
     */
    abstract fun load()

    /**
     * Abstract Show method.
     * Note: Subclasses can overload this (e.g., show(container)).
     * Hiển thị quảng cáo
     * Tùy loại ad mà có thể cần tham số khác nhau:
     * - Banner: show(container: ViewGroup)
     * - Interstitial: show() hoặc show(activity: Activity)
     * - Reward: show(activity: Activity, callback)
     * - Native: show(container: ViewGroup)
     */
    abstract fun show()

    /**
     * Common logic for "Load then immediately Show".
     * This relies on the listener being set.
     */
    abstract fun loadThenShow()
}

fun AdError.toOzError(): OzAdError {
    return OzAdError(
        code = code,
        message = message,
        domain = domain,
    )
}