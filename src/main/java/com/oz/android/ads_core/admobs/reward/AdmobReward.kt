package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.oz.android.OzAdsManager
import com.oz.android.utils.config.AdsCoreType
import com.oz.android.utils.listener.OzAdListener

/**
 * Interface representing Rewarded ads.
 * Resolves to Standard or Next-Gen implementation via factory method [create].
 */
interface AdmobReward {
    fun load()
    fun show(activity: Activity, rewardCallback: OnUserEarnedRewardListener)
    fun loadThenShow(activity: Activity, rewardCallback: OnUserEarnedRewardListener)
    fun isAdLoaded(): Boolean

    companion object {
        fun create(
            context: Context,
            adUnitId: String,
            listener: OzAdListener<AdmobReward>? = null
        ): AdmobReward {
            return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
                AdmobNextReward(context, adUnitId, listener)
            } else {
                AdmobStandardReward(context, adUnitId, listener)
            }
        }
    }
}
