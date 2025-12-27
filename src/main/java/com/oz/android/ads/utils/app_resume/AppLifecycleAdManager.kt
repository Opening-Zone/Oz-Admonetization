package com.oz.android.ads.utils.app_resume

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
    protected var currentActivity: Activity? = null

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
        
        if (!isAppResumeEnabled || isShowingAd) return

        if (disableAdResumeByClickAction) {
            disableAdResumeByClickAction = false
            return
        }

        currentActivity?.let { activity ->
            // Check if current activity is in the disabled list
            for (disabledClass in disabledAppOpenList) {
                if (disabledClass.name == activity.javaClass.name) return
            }

            // Check if current activity is Splash
            if (splashActivityClass != null && splashActivityClass!!.name == activity.javaClass.name) {
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
            return
        }

        if (!isShowingAd) {
            Log.d(TAG, "Will show ad on activity: ${activity.javaClass.simpleName}")
            isShowingAd = true

            showAd(activity) {
                // On Complete (Dismissed or Failed)
                isShowingAd = false
                currentAd = null
                fetchAd() // Preload the next one
            }
        } else {
            Log.d(TAG, "Ad not ready or already showing.")
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
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    // </editor-fold>
}