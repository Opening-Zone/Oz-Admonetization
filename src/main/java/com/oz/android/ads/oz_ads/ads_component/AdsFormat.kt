package com.oz.android.ads.oz_ads.ads_component

/**
 * Enum định nghĩa các format ads có sẵn
 * 
 * Inline formats: hiển thị cùng với content (banner, native)
 * Overlay formats: hiển thị fullscreen hoặc overlay (interstitial, open, native fullscreen, reward)
 */
enum class AdsFormat {
    // Inline formats - hiển thị cùng với content
    BANNER,      // Banner ad inline
    NATIVE,      // Native ad inline
    
    // Overlay formats - hiển thị fullscreen/overlay
    INTERSTITIAL, // Interstitial ad (fullscreen)
    OPEN,         // Open ad (app open ad)
    NATIVE_FULLSCREEN, // Native ad fullscreen
    REWARD,       // Rewarded ad
    REWARD_INTERSTITIAL; // Rewarded interstitial ad

    /**
     * Kiểm tra xem format này có phải là inline format không
     * @return true nếu là inline format, false nếu là overlay format
     */
    fun isInline(): Boolean {
        return this == BANNER || this == NATIVE
    }

    /**
     * Kiểm tra xem format này có phải là overlay format không
     * @return true nếu là overlay format, false nếu là inline format
     */
    fun isOverlay(): Boolean {
        return !isInline()
    }

    companion object {
        /**
         * Get tất cả inline formats
         */
        fun getInlineFormats(): List<AdsFormat> {
            return values().filter { it.isInline() }
        }

        /**
         * Get tất cả overlay formats
         */
        fun getOverlayFormats(): List<AdsFormat> {
            return values().filter { it.isOverlay() }
        }
    }
}


