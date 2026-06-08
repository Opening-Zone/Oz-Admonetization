package com.oz.android.utils.event

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics

object OzEventLogger {

    private const val TAG = "OzAdsEventLogger"

    fun logPaidAdImpression(
        context: Context,
        adValue: AdValue,
        adUnitId: String,
        responseInfo: ResponseInfo?
    ) {
        val adapterClassName = responseInfo?.mediationAdapterClassName ?: "unknown"

        logEventWithAds(
            context,
            adValue.valueMicros.toFloat(),
            adValue.precisionType,
            adUnitId,
            adapterClassName
        )
    }

    private fun logEventWithAds(
        context: Context,
        revenueMicros: Float,
        precision: Int,
        adUnitId: String,
        network: String
    ) {
        val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val revenueUsd = revenueMicros / 1_000_000.0

        // 1. Debug Log to Console
        Log.d(
            TAG,
            "Paid event of value %.6f USD (precision %s) for ad unit %s from network %s".format(
                revenueUsd,
                precision,
                adUnitId,
                network
            )
        )

        // 2. Log Standard Revenue Event (ad_impression)
        // This is the standard event for Ad Revenue in Firebase (ROAS)
        val revenueParams = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, revenueUsd)
            putString(FirebaseAnalytics.Param.CURRENCY, "USD")
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString(FirebaseAnalytics.Param.AD_SOURCE, network)
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "AdMob") // or use mediation logic if needed
            putInt("precision", precision) // Custom param for precision
        }
        firebaseAnalytics.logEvent("app_event_impression", revenueParams)
    }

    fun logClickAdsEvent(context: Context, adUnitId: String) {
        Log.d(TAG, "User click ad for ad unit $adUnitId.")

        val params = Bundle().apply {
            putString("ad_unit_id", adUnitId)
        }

        // Using "ad_click" which is a standard concept, though often auto-collected.
        // Logging manually ensures it appears if auto-collection is off.
        FirebaseAnalytics.getInstance(context).logEvent("ad_click", params)
    }
}