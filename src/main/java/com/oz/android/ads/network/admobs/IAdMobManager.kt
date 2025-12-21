package com.oz.android.ads.network.admobs

import android.app.Activity

/**
 * Interface for AdMob operations
 * Following Interface Segregation Principle
 */
interface IAdMobManager {
    /**
     * Initialize AdMob SDK with the given activity
     * @param activity The activity context for initialization
     * @param appId The AdMob app ID (optional, can be from manifest)
     */
    suspend fun initialize(activity: Activity, appId: String? = null): AdMobResult<Unit>

    /**
     * Check if AdMob is initialized
     */
    fun isInitialized(): Boolean
}

/**
 * Sealed class for AdMob operation results
 * Following Clean Architecture error handling pattern
 */
sealed class AdMobResult<out T> {
    data class Success<T>(val data: T) : AdMobResult<T>()
    data class Error(val exception: Throwable) : AdMobResult<Nothing>()
}




