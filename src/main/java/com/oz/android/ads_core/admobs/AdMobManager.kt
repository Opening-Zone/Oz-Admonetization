package com.oz.android.ads_core.admobs

import android.content.Context
import com.oz.android.utils.OzLog
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.reflect.Proxy

/**
 * Manager class for handling AdMob ads (Standard GMS SDK)
 */
class AdMobManager private constructor() {

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    companion object {
        const val TEST_DEVICE_HASHED_ID = "test"
        const val TAG = "AdMobManager"

        @Volatile
        private var instance: AdMobManager? = null

        /**
         * Get singleton instance
         * Thread-safe lazy initialization
         */
        fun getInstance(): AdMobManager {
            return instance ?: synchronized(this) {
                instance ?: AdMobManager().also { instance = it }
            }
        }
    }

    /**
     * Initialize the Standard GMS Mobile Ads SDK and configure test devices.
     * 
     * NOTE: We must use reflection (Class.forName) here instead of direct GMS imports.
     * This is because the Next-Gen GMA SDK (com.google.android.libraries.ads.mobile.sdk) 
     * bundles a stub/compatibility version of 'com.google.android.gms.ads.MobileAds' 
     * which lacks these standard initialization APIs and takes precedence in the compiler's classpath.
     * Using reflection bypasses this compile-time conflict cleanly.
     * 
     * @param testDeviceList List of test device IDs
     * @param context Application/Activity context
     * @param onComplete Callback triggered once initialization is completed
     */
    fun initializeMobileAdsSdk(
        testDeviceList: List<String>,
        context: Context,
        onComplete: () -> Unit
    ) {
        // OzAdsManager already serializes concurrent callers via CompletableDeferred.
        // Do NOT fire onComplete() early here — the SDK may still be mid-init and premature
        // resumption would cause ads to preload before all mediation adapters are ready.
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            OzLog.w(TAG, "initializeMobileAdsSdk called again while already in progress — ignoring duplicate call")
            return
        }

        val startTime = System.currentTimeMillis()
        com.oz.android.utils.event.OzEventLogger.logAdsSdkInitStart(context, "AdMob_Standard")

        // Initialize standard GMS MobileAds reflectively
        try {
            OzLog.d(TAG, "Initializing GMS Mobile Ads SDK reflectively...")
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
            val listenerClass = Class.forName("com.google.android.gms.ads.initialization.OnInitializationCompleteListener")
            
            val proxyListener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onInitializationComplete") {
                    val duration = System.currentTimeMillis() - startTime
                    OzLog.d(TAG, "✅ Standard GMS Mobile Ads SDK initialized — logging adapter statuses:")
                    com.oz.android.utils.event.OzEventLogger.logAdsSdkInitComplete(context, duration)
                    // args[0] is an InitializationStatus object with getAdapterStatusMap()
                    logAdapterStatuses(args?.getOrNull(0))
                    onComplete()
                }
                null
            }

            val initializeMethod = mobileAdsClass.getMethod(
                "initialize",
                Context::class.java,
                listenerClass
            )
            initializeMethod.invoke(null, context, proxyListener)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to initialize GMS MobileAds reflectively", e)
            com.oz.android.utils.event.OzEventLogger.logAdsSdkInitException(context, e.message)
            onComplete()
        }

        // Configure GMS RequestConfiguration reflectively
        try {
            OzLog.d(TAG, "Configuring GMS test devices reflectively: $testDeviceList")
            val builderClass = Class.forName("com.google.android.gms.ads.RequestConfiguration\$Builder")
            val builderInstance = builderClass.getDeclaredConstructor().newInstance()
            
            val setTestDeviceIdsMethod = builderClass.getMethod("setTestDeviceIds", List::class.java)
            setTestDeviceIdsMethod.invoke(builderInstance, testDeviceList)
            
            val buildMethod = builderClass.getMethod("build")
            val requestConfiguration = buildMethod.invoke(builderInstance)
            
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
            val setRequestConfigurationMethod = mobileAdsClass.getMethod(
                "setRequestConfiguration",
                Class.forName("com.google.android.gms.ads.RequestConfiguration")
            )
            setRequestConfigurationMethod.invoke(null, requestConfiguration)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to set GMS RequestConfiguration reflectively", e)
        }
    }

    /**
     * Open the standard GMS AdMob Ad Inspector.
     * 
     * NOTE: Reflection is used to prevent compile-time classpath conflicts with the next-gen SDK stub.
     */
    fun openAdInspector(context: Context) {
        try {
            OzLog.d(TAG, "Opening standard GMS Ad Inspector reflectively...")
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
            val listenerClass = Class.forName("com.google.android.gms.ads.OnAdInspectorClosedListener")
            
            val proxyListener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onAdInspectorClosed") {
                    val error = args[0]
                    if (error != null) {
                        try {
                            val getMessageMethod = error.javaClass.getMethod("getMessage")
                            val message = getMessageMethod.invoke(error) as? String
                            OzLog.e(TAG, "Standard Ad Inspector closed with error: $message")
                        } catch (ex: Exception) {
                            OzLog.e(TAG, "Standard Ad Inspector closed with error")
                        }
                    }
                }
                null
            }
            
            val openAdInspectorMethod = mobileAdsClass.getMethod(
                "openAdInspector",
                Context::class.java,
                listenerClass
            )
            openAdInspectorMethod.invoke(null, context, proxyListener)
        } catch (e: Exception) {
            OzLog.e(TAG, "Failed to open GMS Ad Inspector reflectively", e)
        }
    }

    /**
     * Logs each adapter's name, readiness state, and latency from an InitializationStatus object.
     * Sorted by latency descending so the slowest adapter appears first.
     * Uses reflection because direct GMS imports conflict with the Next-Gen SDK classpath.
     */
    private fun logAdapterStatuses(initializationStatus: Any?) {
        if (initializationStatus == null) {
            OzLog.w(TAG, "  InitializationStatus is null — cannot log adapter details")
            return
        }
        try {
            val statusMap = initializationStatus.javaClass
                .getMethod("getAdapterStatusMap")
                .invoke(initializationStatus) as? Map<*, *>

            if (statusMap.isNullOrEmpty()) {
                OzLog.d(TAG, "  No adapters reported in InitializationStatus")
                return
            }

            // Sort by latency descending so the slowest adapter is always first
            val sorted = statusMap.entries.sortedByDescending { entry ->
                try {
                    (entry.value!!.javaClass.getMethod("getLatency").invoke(entry.value) as? Int) ?: 0
                } catch (e: Exception) { 0 }
            }

            for (entry in sorted) {
                val adapterName = (entry.key as? String)?.substringAfterLast('.') ?: entry.key.toString()
                val status = entry.value ?: continue
                val latency = runCatching { status.javaClass.getMethod("getLatency").invoke(status) as? Int ?: -1 }.getOrDefault(-1)
                val state   = runCatching { status.javaClass.getMethod("getInitializationState").invoke(status)?.toString() ?: "UNKNOWN" }.getOrDefault("UNKNOWN")
                val desc    = runCatching { status.javaClass.getMethod("getDescription").invoke(status) as? String ?: "" }.getOrDefault("")
                val icon    = if (state.contains("READY", ignoreCase = true)) "✅" else "⚠️"
                val descStr = if (desc.isNotBlank()) " ($desc)" else ""
                OzLog.d(TAG, "  $icon $adapterName — ${latency}ms — $state$descStr")
            }
        } catch (e: Exception) {
            OzLog.w(TAG, "  Could not read adapter statuses: ${e.message}")
        }
    }

    fun isInitialized(): Boolean {
        return isMobileAdsInitializeCalled.get()
    }
}
