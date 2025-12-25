package com.oz.android.ads.network.admobs

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager class for handling AdMob ads
 * Following Single Responsibility Principle - only handles AdMob initialization
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

    public fun initializeMobileAdsSdk(
        testDeviceList: List<String>,
        context: Context,
        onComplete: () -> Unit
    ) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        MobileAds.initialize(context) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            statusMap.forEach { (adapterClass, status) ->
                Log.d(
                    TAG,
                    "Adapter name: $adapterClass, Description: ${status.description}, Latency: ${status.latency}"
                )
            }
            onComplete()
        }

        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceList)
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)
    }

    fun isInitialized(): Boolean {
        return isMobileAdsInitializeCalled.get()
    }
}
