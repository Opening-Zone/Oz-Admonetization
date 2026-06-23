package com.oz.android.ads_core.admobs

import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.oz.android.utils.event.OzEventLogger
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.listener.OzAdListener

/**
 * @param AdType The type of the Ad Object (AdView, InterstitialAd, NativeAd, etc.)
 */
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
     * Shows the ad
     * Different ad types may require different parameters:
     * - Banner: show(container: ViewGroup)
     * - Interstitial: show() or show(activity: Activity)
     * - Reward: show(activity: Activity, callback)
     * - Native: show(container: ViewGroup)
     */
    abstract fun show()

    /**
     * Common logic for "Load then immediately Show".
     * This relies on the listener being set.
     */
    abstract fun loadThenShow()

    /**
     * Paid admob event
     */
     fun getOnPaidListener(response: ResponseInfo?): OnPaidEventListener {
        return OnPaidEventListener { adValue ->
            OzEventLogger.logPaidAdImpression(
                context,
                adValue,
                adUnitId,
                response
            )
        }
     }
}

fun AdError.toOzError(): OzAdError {
    return OzAdError(
        code = code,
        message = message,
        domain = domain,
    )
}

fun LoadAdError.toOzError(): OzAdError {
    val intCode = when (this.code) {
        LoadAdError.ErrorCode.INTERNAL_ERROR -> 0
        LoadAdError.ErrorCode.INVALID_REQUEST -> 1
        LoadAdError.ErrorCode.NETWORK_ERROR -> 2
        LoadAdError.ErrorCode.NO_FILL -> 3
        LoadAdError.ErrorCode.TIMEOUT -> 4
        LoadAdError.ErrorCode.APP_ID_MISSING -> 8
        LoadAdError.ErrorCode.CANCELLED -> 9
        else -> 0
    }
    return OzAdError(
        code = intCode,
        message = this.message,
        domain = "GMA_NEXT_GEN"
    )
}

fun FullScreenContentError.toOzError(): OzAdError {
    val intCode = when (this.code) {
        FullScreenContentError.ErrorCode.INTERNAL_ERROR -> 0
        FullScreenContentError.ErrorCode.AD_REUSED -> 1
        FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND -> 3
        else -> 0
    }
    return OzAdError(
        code = intCode,
        message = this.message,
        domain = "GMA_NEXT_GEN"
    )
}