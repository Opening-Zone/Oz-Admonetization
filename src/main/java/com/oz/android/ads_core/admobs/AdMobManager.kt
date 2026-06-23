package com.oz.android.ads_core.admobs

import android.content.Context
import android.util.Log
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

    fun initializeMobileAdsSdk(
        testDeviceList: List<String>,
        context: Context,
        onComplete: () -> Unit
    ) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        try {
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
            val listenerClass = Class.forName("com.google.android.gms.ads.initialization.OnInitializationCompleteListener")
            
            val proxyListener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, _ ->
                if (method.name == "onInitializationComplete") {
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
            Log.e(TAG, "Failed to initialize GMS MobileAds reflectively", e)
            onComplete()
        }

        try {
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
            Log.e(TAG, "Failed to set GMS RequestConfiguration reflectively", e)
        }
    }

    fun isInitialized(): Boolean {
        return isMobileAdsInitializeCalled.get()
    }
}
