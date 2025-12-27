# App Resume Ads Manager

## Overview

This package provides two implementations for showing ads when users return to your app from the background:

1. **`AppResumeInterstitialManager`** - Uses Interstitial Ads
2. **`AppResumeAppOpenManager`** - Uses App Open Ads (Recommended)

Both managers automatically handle:
- ✅ Activity lifecycle tracking
- ✅ Process lifecycle observation
- ✅ Ad preloading and caching
- ✅ Preventing ads on specific screens (Splash, Payment, etc.)
- ✅ Preventing duplicate ads when users click on ads

---

## Which One to Use?

### App Open Ads (Recommended) ⭐
- **Faster** - Optimized for app launch/resume
- **Better UX** - Specifically designed for this use case
- **Higher Revenue** - Generally performs better
- **Expires after 4 hours** - Automatic freshness

### Interstitial Ads
- **More flexible** - Can be used anywhere
- **Good fallback** - If App Open isn't available
- **Familiar format** - Classic full-screen ad

---

## Setup

### 1. App Open Ads Implementation

#### In your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob SDK first
        MobileAds.initialize(this)
        
        // Setup App Resume Manager
        val resumeManager = AppResumeAppOpenManager.getInstance()
        resumeManager.init(this)
        resumeManager.setAdUnitId("ca-app-pub-3940256099942544/9257395921") // Your App Open Ad Unit ID
        
        // Optional: Configure activities to exclude
        resumeManager.setSplashActivity(SplashActivity::class.java)
        resumeManager.disableAppResumeWithActivity(PaymentActivity::class.java)
        resumeManager.disableAppResumeWithActivity(PurchaseActivity::class.java)
        
        // Optional: Preload first ad
        resumeManager.fetchAd()
    }
}
```

#### Optional: Add custom listener

```kotlin
resumeManager.setAdListener(object : OzAdListener<AdmobAppOpen>() {
    override fun onAdLoaded(ad: AdmobAppOpen) {
        Log.d("MyApp", "App Open ad loaded and ready")
    }
    
    override fun onAdFailedToLoad(error: OzAdError) {
        Log.e("MyApp", "Failed to load App Open ad: ${error.message}")
    }
    
    override fun onAdDismissedFullScreenContent() {
        Log.d("MyApp", "User dismissed the ad")
        // Continue with your app logic
    }
    
    override fun onAdClicked() {
        Log.d("MyApp", "User clicked on ad")
        // User will be taken to external browser/app
    }
})
```

---

### 2. Interstitial Ads Implementation

#### In your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob SDK first
        MobileAds.initialize(this)
        
        // Setup App Resume Manager
        val resumeManager = AppResumeInterstitialManager.getInstance()
        resumeManager.init(this)
        resumeManager.setAdUnitId("ca-app-pub-3940256099942544/1033173712") // Your Interstitial Ad Unit ID
        
        // Optional: Configure activities to exclude
        resumeManager.setSplashActivity(SplashActivity::class.java)
        resumeManager.disableAppResumeWithActivity(PaymentActivity::class.java)
        
        // Optional: Preload first ad
        resumeManager.fetchAd()
    }
}
```

#### Optional: Add custom listener

```kotlin
resumeManager.setAdListener(object : OzAdListener<AdmobInterstitial>() {
    override fun onAdLoaded(ad: AdmobInterstitial) {
        Log.d("MyApp", "Interstitial ad loaded and ready")
    }
    
    override fun onAdFailedToLoad(error: OzAdError) {
        Log.e("MyApp", "Failed to load Interstitial: ${error.message}")
    }
    
    override fun onAdDismissedFullScreenContent() {
        Log.d("MyApp", "User dismissed the ad")
    }
})
```

---

## Configuration Options

### Enable/Disable Resume Ads

```kotlin
// Disable globally
resumeManager.isAppResumeEnabled = false

// Enable again
resumeManager.isAppResumeEnabled = true
```

### Exclude Specific Activities

```kotlin
// Permanently disable on an activity
resumeManager.disableAppResumeWithActivity(GameActivity::class.java)

// Re-enable on an activity
resumeManager.enableAppResumeWithActivity(GameActivity::class.java)
```

### Manual Ad Display

```kotlin
// Show ad manually (not from lifecycle event)
resumeManager.showAdManually(currentActivity) {
    // Called when ad is dismissed or failed to show
    Log.d("MyApp", "Ad completed")
}
```

### Check Ad Status

```kotlin
// Check if ad is ready
if (resumeManager.isAdReady()) {
    Log.d("MyApp", "Ad is loaded and ready to show")
}

// Manually trigger ad load
resumeManager.fetchAd()
```

---

## How It Works

### Automatic Flow

1. **User minimizes app** → Manager detects background state
2. **Ad loads in background** → Ready for next app resume
3. **User returns to app** → `onMoveToForeground()` triggered
4. **Checks are performed:**
   - Is app resume enabled?
   - Is ad already showing?
   - Is current activity excluded?
   - Is this the splash screen?
   - Did user just click an ad?
5. **If all checks pass** → Ad shows automatically
6. **After ad dismissal** → New ad preloads for next time

### Smart Ad Management

```kotlin
// When user clicks on ANY ad in your app
resumeManager.disableAdResumeByClickAction()
// This prevents resume ad from showing when user returns from browser
```

---

## Best Practices

### 1. Preload Early
```kotlin
// In Application.onCreate() or after splash screen
resumeManager.fetchAd()
```

### 2. Exclude Important Screens
```kotlin
// Don't interrupt critical user flows
resumeManager.disableAppResumeWithActivity(CheckoutActivity::class.java)
resumeManager.disableAppResumeWithActivity(VideoPlayerActivity::class.java)
```

### 3. Set Splash Activity
```kotlin
// Prevent ads from showing on app launch
resumeManager.setSplashActivity(SplashActivity::class.java)
```

### 4. Test with Test Ads
```kotlin
// App Open Test Ad Unit
"ca-app-pub-3940256099942544/9257395921"

// Interstitial Test Ad Unit
"ca-app-pub-3940256099942544/1033173712"
```

---

## Advanced Usage

### Combine with Other Ad Formats

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize both managers (choose one strategy)
        
        // Option 1: Use App Open for resume, Interstitial elsewhere
        AppResumeAppOpenManager.getInstance().apply {
            init(this@MyApplication)
            setAdUnitId("your-app-open-id")
        }
        
        // Option 2: Use Interstitial for everything
        AppResumeInterstitialManager.getInstance().apply {
            init(this@MyApplication)
            setAdUnitId("your-interstitial-id")
        }
    }
}
```

### Custom Logic Before Showing

```kotlin
resumeManager.setAdListener(object : OzAdListener<AdmobAppOpen>() {
    override fun onAdShowedFullScreenContent() {
        // Pause game, music, animations, etc.
        gameEngine.pause()
        musicPlayer.pause()
    }
    
    override fun onAdDismissedFullScreenContent() {
        // Resume your app logic
        gameEngine.resume()
        musicPlayer.resume()
    }
})
```

### Conditional Display

```kotlin
// In your Activity or Fragment
override fun onResume() {
    super.onResume()
    
    // Only show resume ads if user is premium
    if (!user.isPremium) {
        AppResumeAppOpenManager.getInstance().isAppResumeEnabled = true
    } else {
        AppResumeAppOpenManager.getInstance().isAppResumeEnabled = false
    }
}
```

---

## Troubleshooting

### Ads not showing?

1. **Check Ad Unit ID is set**
   ```kotlin
   resumeManager.setAdUnitId("your-ad-unit-id")
   ```

2. **Verify `init()` was called**
   ```kotlin
   resumeManager.init(application)
   ```

3. **Check if ads are enabled**
   ```kotlin
   Log.d("Debug", "Enabled: ${resumeManager.isAppResumeEnabled}")
   ```

4. **Check if ad is loaded**
   ```kotlin
   Log.d("Debug", "Ready: ${resumeManager.isAdReady()}")
   ```

5. **Look for logs**
   ```bash
   adb logcat | grep "AppResumeAppOpen\|AppResumeInterstitial"
   ```

### Ad shows too frequently?

App Open ads expire after 4 hours and only show once per app resume. If you feel they're too frequent, you can add custom logic:

```kotlin
class MyApplication : Application() {
    private var lastAdTime = 0L
    private val AD_COOLDOWN = 5 * 60 * 1000L // 5 minutes
    
    override fun onCreate() {
        super.onCreate()
        
        val resumeManager = AppResumeAppOpenManager.getInstance()
        resumeManager.init(this)
        resumeManager.setAdUnitId("your-ad-unit-id")
        
        resumeManager.setAdListener(object : OzAdListener<AdmobAppOpen>() {
            override fun onAdShowedFullScreenContent() {
                lastAdTime = System.currentTimeMillis()
            }
        })
        
        // In your lifecycle handling
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val timeSinceLastAd = System.currentTimeMillis() - lastAdTime
                resumeManager.isAppResumeEnabled = timeSinceLastAd >= AD_COOLDOWN
            }
        })
    }
}
```

---

## Architecture

Both managers extend `AppLifecycleAdManager<T>` which provides:

- Activity lifecycle tracking
- Process lifecycle observation  
- Automatic ad loading and caching
- Smart showing logic
- Activity exclusion lists

The generic design makes it easy to add more ad formats in the future!

---

## Summary

**For most apps, use `AppResumeAppOpenManager`** - it's optimized for app resume scenarios and provides the best user experience.

Use `AppResumeInterstitialManager` if:
- App Open ads aren't available in your region
- You want consistent ad format across your app
- You need more control over timing

Both are production-ready, well-tested, and follow Android best practices! 🚀


