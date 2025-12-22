package com.oz.android.ads.oz_ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.oz.android.ads.network.admobs.AdMobManager
import com.oz.android.ads.network.admobs.AdMobResult
import com.oz.android.ads.network.admobs.IAdMobManager
import com.oz.android.ads.oz_ads.ads_component.AdState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Business layer manager for handling ads
 * This layer coordinates between different ad networks
 * Following Dependency Inversion Principle - depends on abstraction (IAdMobManager)
 * Following Single Responsibility Principle - orchestrates ad network initialization
 */
class OzAdsManager private constructor(
    private val adMobManager: IAdMobManager
) {

    @Volatile
    private var initialized = false

    private val _enableAd = MutableStateFlow(false)
    val enableAd = _enableAd.asStateFlow()

    // Ads state management (key -> state)
    private val adStates = ConcurrentHashMap<String, AdState>()

    // Ad store (key -> ad object)
    private val adStore = ConcurrentHashMap<String, Any>()

    fun setEnableAd(shouldShow: Boolean) {
        _enableAd.value = shouldShow
    }

    /**
     * Get state of an ad
     */
    fun getAdState(key: String): AdState {
        return adStates.getOrDefault(key, AdState.IDLE)
    }

    /**
     * Set state of an ad
     */
    fun setAdState(key: String, state: AdState) {
        adStates[key] = state
    }

    /**
     * Set state of an ad if absent
     */
    fun putAdStateIfAbsent(key: String, state: AdState) {
        adStates.putIfAbsent(key, state)
    }

    /**
     * Get ad object from store
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getAd(key: String): T? {
        return adStore[key] as? T
    }

    /**
     * Save ad object to store
     */
    fun setAd(key: String, ad: Any) {
        adStore[key] = ad
    }

    /**
     * Remove ad object from store
     */
    fun removeAd(key: String): Any? {
        return adStore.remove(key)
    }

    companion object {
        @Volatile
        private var instance: OzAdsManager? = null

        /**
         * Get singleton instance with default dependencies
         */
        fun getInstance(adMobManager: IAdMobManager = AdMobManager.getInstance()): OzAdsManager {
            return instance ?: synchronized(this) {
                instance ?: OzAdsManager(adMobManager).also { instance = it }
            }
        }

        /**
         * Reset instance (useful for testing)
         */
        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }

    /**
     * Initialize all ad networks
     * Currently initializes AdMob only
     *
     * @param activity The activity context for initialization
     * @param adMobAppId Optional AdMob app ID (will use manifest value if null)
     * @return Result indicating success or failure
     */
    suspend fun init(
        activity: Activity,
        adMobAppId: String? = null
    ): OzAdsResult<Unit> = suspendCancellableCoroutine { continuation ->
        MobileAds.initialize(activity) {
            val scope = CoroutineScope(continuation.context)
            scope.launch {
                val result = adMobManager.initialize(activity, adMobAppId)
                val ozResult = when (result) {
                    is AdMobResult.Success -> {
                        initialized = true
                        OzAdsResult.Success(Unit)
                    }
                    is AdMobResult.Error -> {
                        initialized = false
                        OzAdsResult.Error(result.exception)
                    }
                }
                if (continuation.isActive) {
                    continuation.resume(ozResult)
                }
            }
        }
    }

    /**
     * Check if all ad networks are initialized
     */
    fun isInitialized(): Boolean {
        return initialized && adMobManager.isInitialized()
    }
}

/**
 * Sealed class for OzAds operation results
 * Following Clean Architecture error handling pattern
 */
sealed class OzAdsResult<out T> {
    data class Success<T>(val data: T) : OzAdsResult<T>()
    data class Error(val exception: Throwable) : OzAdsResult<Nothing>()
}