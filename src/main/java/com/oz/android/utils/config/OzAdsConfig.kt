package com.oz.android.utils.config

/**
 * Configuration Entity for OzAds.
 *
 * @property isAdEnabled Master toggle for ads.
 * @property overlayAdGapTimeMs Time in milliseconds between overlay ads.
 * @property testDeviceIds List of device IDs for AdMob testing.
 * @property isDebugMode Toggle for verbose logging.
 * @property offAdsOnPause When activity is onPause(), off the inline ads.
 */
data class OzAdsConfig(
    val isAdEnabled: Boolean = false,
    val overlayAdGapTimeMs: Long = 30_000L,
    val testDeviceIds: List<String> = emptyList(),
    val isDebugMode: Boolean = false,
    val offAdsOnPause: Boolean = true,
    val adsCoreType: AdsCoreType = AdsCoreType.ADMOB_NEXT_GEN
)