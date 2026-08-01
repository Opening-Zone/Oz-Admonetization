package com.oz.android.ads_impl.app_resume

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.oz.android.utils.OzLog
import com.oz.android.utils.event.OzEventLogger
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference
import java.util.Date

/**
 * A generic Lifecycle-aware Ad Manager.
 * T: The type of the Ad object (e.g., InterstitialAd, AppOpenAd).
 */
abstract class AppLifecycleAdManager<T : Any> : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    protected val TAG = "AppLifecycleAdManager"

    // The current loaded ad
    protected var currentAd: T? = null

    // Time when the ad was loaded
    private var adLoadTime: Long = 0

    // Reference to the current top activity
    private var currentActivityRef: WeakReference<Activity>? = null

    protected val currentActivity: Activity?
        get() = currentActivityRef?.get()

    // Application reference
    protected lateinit var myApplication: Application

    // State flags
    var isShowingAd = false
        protected set
    var isAppResumeEnabled = true
    private var isInitialized = false
    private var disableAdResumeByClickAction = false

    // List of activities where App Open Ads should NOT show
    private val disabledAppOpenList: MutableList<Class<*>> = ArrayList()

    // The class of the SplashActivity (to avoid showing ads on top of splash)
    private var splashActivityClass: Class<*>? = null

    /**
     * Abstract method: Implement the logic to load the specific ad type.
     */
    abstract fun loadAd(context: Context)

    /**
     * Abstract method: Implement the logic to show the ad.
     * @param activity The activity context to show the ad.
     * @param onShowComplete Callback when the ad is dismissed or failed to show.
     */
    abstract fun showAd(activity: Activity, onShowComplete: () -> Unit)

    /**
     * Initialize the manager. Must be called in Application class.
     */
    fun init(application: Application) {
        if (isInitialized) return
        this.myApplication = application
        this.myApplication.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        isInitialized = true
    }

    /**
     * Sets the Splash Activity class. Ads will not be shown on this screen.
     */
    fun setSplashActivity(activityClass: Class<*>) {
        this.splashActivityClass = activityClass
    }

    /**
     * Disable app resume ads for a specific activity.
     */
    fun disableAppResumeWithActivity(activityClass: Class<*>) {
        disabledAppOpenList.add(activityClass)
    }

    fun enableAppResumeWithActivity(activityClass: Class<*>) {
        disabledAppOpenList.remove(activityClass)
    }

    /**
     * Temporarily disable resume ads (e.g., when clicking an ad inside the app).
     */
    fun disableAdResumeByClickAction() {
        disableAdResumeByClickAction = true
    }

    /**
     * Lifecycle Event: Called when the App moves to foreground.
     * Overrides DefaultLifecycleObserver.onStart()
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        
        if (!isAppResumeEnabled) {
            currentActivity?.let { OzEventLogger.logAppResumeSkip(it, reason = "resume_disabled") }
            return
        }
        if (isShowingAd) {
            currentActivity?.let { OzEventLogger.logAppResumeSkip(it, reason = "already_showing") }
            return
        }

        if (disableAdResumeByClickAction) {
            disableAdResumeByClickAction = false
            currentActivity?.let { OzEventLogger.logAppResumeSkip(it, reason = "disabled_by_click_action") }
            return
        }

        currentActivity?.let { activity ->
            // Check if current activity is in the disabled list
            for (disabledClass in disabledAppOpenList) {
                if (disabledClass.name == activity.javaClass.name) {
                    OzEventLogger.logAppResumeSkip(activity, reason = "activity_disabled")
                    return
                }
            }

            // Check if current activity is Splash
            if (splashActivityClass != null && splashActivityClass!!.name == activity.javaClass.name) {
                OzEventLogger.logAppResumeSkip(activity, reason = "splash_activity")
                return
            }

            // Attempt to show ad
            showAdIfAvailable(activity)
        }
    }

    /**
     * Core logic to check availability and show ad.
     */
    fun showAdIfAvailable(activity: Activity) {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            OzEventLogger.logAppResumeSkip(activity, reason = "lifecycle_not_started")
            return
        }

        if (!isShowingAd) {
            OzLog.d(TAG, "Will show ad on activity: ${activity.javaClass.simpleName}")
            isShowingAd = true
            OzEventLogger.logAppResumeShow(activity)

            showAd(activity) {
                // On Complete (Dismissed or Failed)
                isShowingAd = false
                currentAd = null
                fetchAd() // Preload the next one
            }
        } else {
            OzLog.d(TAG, "Ad not ready or already showing.")
            fetchAd() // Try to load if not ready
        }
    }

    /**
     * Trigger a load request.
     */
    fun fetchAd() {
        loadAd(myApplication)
    }

    /**
     * Helper to set the loaded ad and timestamp (Call this from subclass when load succeeds).
     */
    protected fun onAdLoadedSuccess(ad: T) {
        this.currentAd = ad
        this.adLoadTime = Date().time
    }

    // <editor-fold desc="ActivityLifecycleCallbacks Implementation">
    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivityRef?.clear()
            currentActivityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        OzLog.d(TAG, "onActivityCreated: ${activity.javaClass.simpleName}")
    }
    override fun onActivityPaused(activity: Activity) {
        OzLog.d(TAG, "onActivityPaused: ${activity.javaClass.simpleName}")
    }
    override fun onActivityStopped(activity: Activity) {
        OzLog.d(TAG, "onActivityStopped: ${activity.javaClass.simpleName}")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        OzLog.d(TAG, "onActivitySaveInstanceState: ${activity.javaClass.simpleName}")
    }
    // </editor-fold>
}