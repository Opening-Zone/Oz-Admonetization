package com.oz.android.utils

import android.util.Log
import com.oz.android.OzAdsManager

/**
 * Custom logging utility for OzAds library.
 * Logs are only printed if [OzAdsManager.config.isDebugMode] is enabled.
 */
object OzLog {

    fun d(tag: String, msg: String) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            Log.d(tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            Log.i(tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            if (tr != null) {
                Log.e(tag, msg, tr)
            } else {
                Log.e(tag, msg)
            }
        }
    }
}
