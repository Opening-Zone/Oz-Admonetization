package com.oz.android.utils

import android.util.Log
import com.oz.android.OzAdsManager

/**
 * Custom logging utility for OzAds library.
 * Logs are only printed if [OzAdsManager.config.isDebugMode] is enabled.
 */
object OzLog {

    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            if (tr != null) {
                Log.d(tag, msg, tr)
            } else {
                Log.d(tag, msg)
            }
        }
    }

    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            if (tr != null) {
                Log.i(tag, msg, tr)
            } else {
                Log.i(tag, msg)
            }
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            if (tr != null) {
                Log.w(tag, msg, tr)
            } else {
                Log.w(tag, msg)
            }
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

    fun v(tag: String, msg: String, tr: Throwable? = null) {
        if (OzAdsManager.getInstance().config.isDebugMode) {
            if (tr != null) {
                Log.v(tag, msg, tr)
            } else {
                Log.v(tag, msg)
            }
        }
    }
}
