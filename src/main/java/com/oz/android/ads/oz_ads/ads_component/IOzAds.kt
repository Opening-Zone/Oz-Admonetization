package com.oz.android.ads.oz_ads.ads_component

/**
 * Interface chung cho tất cả các loại OzAds
 * Đảm bảo tất cả ads component đều có các phương thức chung
 */
interface IOzAds {
    /**
     * Kiểm tra xem có nên hiển thị ad không
     * @return true nếu nên hiển thị, false nếu không
     */
    fun isAdEnable(): Boolean

    /**
     * Set preload key để preload ad
     * @param key Key để identify ad cần preload
     */
    fun setPreloadKey(key: String)

    /**
     * Load quảng cáo
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    fun loadAd()

    /**
     * Hiển thị quảng cáo
     * @param key Key để identify ad cần show (đại diện cho placement)
     */
    fun showAds(key: String)

    fun loadThenShow(key:String)
}

