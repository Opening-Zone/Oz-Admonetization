package com.oz.android.ads.oz_ads.ads_component

/**
 * Enum định nghĩa các state của ad
 */
enum class AdState {
    IDLE,      // Default state, chưa có action nào
    LOADING,   // Đang load ad từ mediation
    LOADED,    // Ad đã load thành công, sẵn sàng để show
    SHOWING    // Ad đang được hiển thị
}


