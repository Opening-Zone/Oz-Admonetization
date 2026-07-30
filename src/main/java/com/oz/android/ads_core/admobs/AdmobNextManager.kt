package com.oz.android.ads_core.admobs

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.oz.android.utils.OzLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager class for handling GMA Next-Gen SDK initialization and global settings.
 */
class AdmobNextManager private constructor() {

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    companion object {
        const val TAG = "AdmobNextManager"

        @Volatile
        private var instance: AdmobNextManager? = null

        /**
         * Get singleton instance. Thread-safe lazy initialization.
         */
        fun getInstance(): AdmobNextManager {
            return instance ?: synchronized(this) {
                instance ?: AdmobNextManager().also { instance = it }
            }
        }
    }

    /**
     * Initialize the Next-Gen Mobile Ads SDK.
     *
     * Per Google's official guidance, [MobileAds.initialize] must be called on a background thread.
     * The [onComplete] callback is invoked only after ALL mediation adapters have finished
     * initializing, ensuring full auction participation on the first ad request.
     *
     * @param appId   The AdMob App ID from the manifest
     * @param context Application or Activity context
     * @param onComplete Callback invoked when initialization (including all adapters) is complete
     */
    fun initializeMobileAdsSdk(
        appId: String,
        context: Context,
        onComplete: () -> Unit
    ) {
        // OzAdsManager already serializes concurrent callers via CompletableDeferred.
        // This guard only prevents a true duplicate SDK call (e.g., from code paths that bypass
        // OzAdsManager). We must NOT fire onComplete() early here — the SDK may still be mid-init
        // and firing it prematurely would resume the caller coroutine before adapters are ready.
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            OzLog.w(TAG, "initializeMobileAdsSdk called again while already in progress — ignoring duplicate call")
            return
        }

        val startTime = System.currentTimeMillis()
        com.oz.android.utils.event.OzEventLogger.logAdsSdkInitStart(context, "AdMob_NextGen")

        OzLog.d(TAG, "Initializing Next-Gen Mobile Ads SDK with App ID: $appId")

        // Per Google's official guidance, MobileAds.initialize must run on a background thread.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(
                    context,
                    InitializationConfig.Builder(appId).build()
                ) { initializationStatus ->
                    val duration = System.currentTimeMillis() - startTime
                    com.oz.android.utils.event.OzEventLogger.logAdsSdkInitComplete(context, duration)

                    // Log each adapter's status sorted by latency (slowest first) for easy diagnosis.
                    val sorted = initializationStatus.adapterStatusMap.entries
                        .sortedByDescending { it.value.latency }

                    OzLog.d(TAG, "✅ Next-Gen SDK initialized — adapter statuses (slowest first):")
                    for ((adapterName, adapterStatus) in sorted) {
                        val shortName = adapterName.substringAfterLast('.')
                        val icon = if (adapterStatus.initializationState.name.contains("READY", ignoreCase = true)) "✅" else "⚠️"
                        val desc = adapterStatus.description.let { if (it.isNotBlank()) " ($it)" else "" }
                        OzLog.d(TAG, "  $icon $shortName — ${adapterStatus.latency}ms — ${adapterStatus.initializationState}$desc")
                    }

                    onComplete()
                }
            } catch (e: Exception) {
                OzLog.e(TAG, "Failed to initialize Next-Gen Mobile Ads SDK", e)
                com.oz.android.utils.event.OzEventLogger.logAdsSdkInitException(context, e.message)
                onComplete()
            }
        }
    }

    /**
     * Open the Next-Gen Ad Inspector.
     */
    fun openAdInspector(context: Context) {
        try {
            MobileAds.openAdInspector { error ->
                if (error != null) {
                    OzLog.e(TAG, "Next-Gen Ad Inspector closed with error: ${error.message}")
                }
            }
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to open Next-Gen Ad Inspector", e)
        }
    }
}
