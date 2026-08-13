package com.oz.android.utils.event

import android.content.Context
import android.os.Bundle
import com.oz.android.utils.OzLog
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics

object OzEventLogger {

    private const val TAG = "OzAdsEventLogger"

    private fun logSimpleEvent(context: Context, eventName: String, params: Bundle? = null) {
        try {
            OzLog.d(TAG, "Logging event '$eventName' params: ${params ?: "none"}")
            FirebaseAnalytics.getInstance(context).logEvent(eventName, params ?: Bundle())
        } catch (e: Exception) {
            OzLog.e(TAG, "Error logging event '$eventName' to Firebase Analytics", e)
        }
    }

    private fun buildAdParams(
        adUnitId: String?,
        adFormat: String?,
        key: String?,
        errorCode: Int? = null,
        errorMessage: String? = null,
        reason: String? = null
    ): Bundle {
        return Bundle().apply {
            adUnitId?.let { putString("ad_unit_id", it) }
            adFormat?.let { putString("ad_format", it) }
            key?.let { putString("ad_key", it) }
            errorCode?.let { putInt("error_code", it) }
            errorMessage?.let { putString("error_message", it) }
            reason?.let { putString("reason", it) }
        }
    }

    // --- Consent Events ---

    fun logConsentCheckStart(context: Context) {
        logSimpleEvent(context, "consent_check_start")
    }

    fun logConsentCheckSuccess(context: Context) {
        logSimpleEvent(context, "consent_check_success")
    }

    fun logConsentCheckFailed(context: Context, errorCode: Int? = null, errorMessage: String? = null) {
        val params = Bundle().apply {
            errorCode?.let { putInt("error_code", it) }
            errorMessage?.let { putString("error_message", it) }
        }
        logSimpleEvent(context, "consent_check_failed", params)
    }

    fun logConsentFormShow(context: Context) {
        logSimpleEvent(context, "consent_form_show")
    }

    fun logConsentFormClosed(context: Context, errorMessage: String? = null) {
        val params = Bundle().apply {
            errorMessage?.let { putString("error_message", it) }
        }
        logSimpleEvent(context, "consent_form_closed", params)
    }

    fun logConsentAdsReady(context: Context) {
        logSimpleEvent(context, "consent_ads_ready")
    }

    fun logConsentAdsBlocked(context: Context, reason: String? = null) {
        val params = Bundle().apply {
            reason?.let { putString("reason", it) }
        }
        logSimpleEvent(context, "consent_ads_blocked", params)
    }

    // --- Ads SDK Initialization Events ---

    fun logAdsSdkInitStart(context: Context, sdkType: String = "AdMob") {
        val params = Bundle().apply { putString("sdk_type", sdkType) }
        logSimpleEvent(context, "ads_sdk_init_start", params)
    }

    fun logAdsSdkInitComplete(context: Context, durationMs: Long? = null) {
        val params = Bundle().apply { durationMs?.let { putLong("duration_ms", it) } }
        logSimpleEvent(context, "ads_sdk_init_complete", params)
    }

    fun logAdsSdkInitException(context: Context, errorMessage: String? = null) {
        val params = Bundle().apply { errorMessage?.let { putString("error_message", it) } }
        logSimpleEvent(context, "ads_sdk_init_exception", params)
    }

    /**
     * SDK init exceeded budget from within init(). Indicates MobileAds.initialize() was genuinely slow.
     */
    fun logAdsSdkInitTimeoutInit(context: Context, timeoutMs: Long, sdkType: String = "AdMob") {
        val params = Bundle().apply {
            putLong("timeout_ms", timeoutMs)
            putString("sdk_type", sdkType)
        }
        logSimpleEvent(context, "ads_sdk_init_timeout_init", params)
    }

    /**
     * awaitInitialization() timed out waiting for init to finish.
     * Includes init_started flag to distinguish whether loadAd() ran before init() started,
     * or init() started but took longer than budget.
     */
    fun logAdsSdkInitTimeoutAwait(context: Context, timeoutMs: Long, sdkType: String = "AdMob") {
        val params = Bundle().apply {
            putLong("timeout_ms", timeoutMs)
            putString("sdk_type", sdkType)
            putBoolean("init_started", com.oz.android.OzAdsManager.getInstance().isInitStarted())
        }
        logSimpleEvent(context, "ads_sdk_init_timeout_await", params)
    }

    @Deprecated("Replaced by logAdsSdkInitTimeoutInit and logAdsSdkInitTimeoutAwait to separate root causes.")
    fun logAdsSdkInitTimeout(context: Context, timeoutMs: Long, sdkType: String = "AdMob") {
        logAdsSdkInitTimeoutInit(context, timeoutMs, sdkType)
    }

    // --- Ad Flow & Lifecycle Events ---

    fun logAdLoadAttempt(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_load_attempt", buildAdParams(adUnitId, adFormat, key))
    }

    @Deprecated("Replaced by logAdLoadAttempt to correctly reflect load attempt semantics.", ReplaceWith("logAdLoadAttempt(context, adUnitId, adFormat, key)"))
    fun logAdOpportunity(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_opportunity", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdSkip(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        reason: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_skip", buildAdParams(adUnitId, adFormat, key, reason = reason))
    }

    fun logAdExpired(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        reason: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_expired", buildAdParams(adUnitId, adFormat, key, reason = reason))
    }

    fun logAdRefreshed(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_refreshed", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdRefreshFailed(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        errorCode: Int? = null,
        errorMessage: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(
            context,
            "ad_refresh_failed",
            buildAdParams(adUnitId, adFormat, key, errorCode = errorCode, errorMessage = errorMessage)
        )
    }

    fun logAppResumeShow(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "app_resume_show", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAppResumeSkip(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        reason: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "app_resume_skip", buildAdParams(adUnitId, adFormat, key, reason = reason))
    }

    fun logAdRequest(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_request", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdLoadSuccess(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_load_success", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdLoadFailed(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        errorCode: Int? = null,
        errorMessage: String? = null,
        key: String? = null
    ) {
        val eventName = when (errorCode) {
            3 -> "ad_load_failed_no_fill"
            2 -> "ad_load_failed_network"
            1 -> "ad_load_failed_invalid_request"
            0 -> "ad_load_failed_internal"
            else -> "ad_load_failed_other"
        }
        logSimpleEvent(
            context,
            eventName,
            buildAdParams(adUnitId, adFormat, key, errorCode = errorCode, errorMessage = errorMessage)
        )
    }

    fun logAdShowCalled(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_show_called", buildAdParams(adUnitId, adFormat, key))
    }

    @Deprecated("Removed — use ad_show_called + ad_impression instead")
    fun logAdShowSuccess(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        // ad_show_success is removed — ad_impression tracks impressions
    }

    fun logAdShowFailed(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        errorCode: Int? = null,
        errorMessage: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(
            context,
            "ad_show_failed",
            buildAdParams(adUnitId, adFormat, key, errorCode = errorCode, errorMessage = errorMessage)
        )
    }

    fun logAdClickedCustom(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_clicked_custom", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdDismissed(
        context: Context,
        adUnitId: String? = null,
        adFormat: String? = null,
        key: String? = null
    ) {
        logSimpleEvent(context, "ad_dismissed", buildAdParams(adUnitId, adFormat, key))
    }

    fun logAdRewardEarned(
        context: Context,
        adUnitId: String? = null,
        rewardType: String? = null,
        rewardAmount: Int? = null,
        key: String? = null
    ) {
        val params = buildAdParams(adUnitId, "reward", key).apply {
            rewardType?.let { putString("reward_type", it) }
            rewardAmount?.let { putInt("reward_amount", it) }
        }
        logSimpleEvent(context, "ad_reward_earned", params)
    }

    fun logPaidAdImpression(
        context: Context,
        adValue: AdValue,
        adUnitId: String,
        responseInfo: ResponseInfo?
    ) {
        val adapterClassName = responseInfo?.loadedAdapterResponseInfo?.adapterClassName
            ?: responseInfo?.mediationAdapterClassName
            ?: "unknown"

        logEventWithAds(
            context,
            adValue.valueMicros,
            adValue.currencyCode,
            adValue.precisionType,
            adUnitId,
            adapterClassName
        )
    }

    private fun logEventWithAds(
        context: Context,
        revenueMicros: Long,
        currencyCode: String,
        precision: Int,
        adUnitId: String,
        network: String
    ) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

            val revenueInCurrency = revenueMicros / 1_000_000.0

            OzLog.d(
                TAG,
                "Paid event of value %.6f %s (precision %s) for ad unit %s from network %s".format(
                    revenueInCurrency,
                    currencyCode,
                    precision,
                    adUnitId,
                    network
                )
            )

            val revenueParams = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, revenueInCurrency)
                putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
                putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
                putString(FirebaseAnalytics.Param.AD_SOURCE, network)
                putString(FirebaseAnalytics.Param.AD_PLATFORM, "AdMob")
                putInt("precision", precision)
            }
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, revenueParams)
        } catch (e: Exception) {
            OzLog.e(TAG, "Error logging paid ad impression event", e)
        }
    }

    // Note: logClickAdsEvent() was removed as "ad_click" is a reserved Firebase event name.

    fun logPaidAdImpressionNextGen(
        context: Context,
        valueMicros: Long,
        currencyCode: String,
        adUnitId: String,
        adapterClassName: String
    ) {
        // Next-Gen doesn't expose precision, so we pass 0 (UNKNOWN)
        logEventWithAds(
            context,
            valueMicros,
            currencyCode,
            0,
            adUnitId,
            adapterClassName
        )
    }
}