package com.oz.android.ads.network.admobs.ads_component

/**
 * Interface chung cho tất cả các loại AdMob ads
 * Đảm bảo tất cả ads component đều có 3 phương thức chính: load, show, và loadThenShow
 */
interface IAdmobAds {
    /**
     * Load quảng cáo
     * Quảng cáo sẽ được load nhưng chưa hiển thị
     */
    fun load()

    /**
     * Hiển thị quảng cáo
     * Tùy loại ad mà có thể cần tham số khác nhau:
     * - Banner: show(container: ViewGroup)
     * - Interstitial: show() hoặc show(activity: Activity)
     * - Reward: show(activity: Activity, callback)
     * - Native: show(container: ViewGroup)
     */
    fun show()

    /**
     * Load quảng cáo và tự động hiển thị khi load xong
     * Tùy loại ad mà có thể cần tham số khác nhau
     */
    fun loadThenShow()
}


