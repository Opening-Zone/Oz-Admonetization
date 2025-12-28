# OzAdsManager

## Overview

`OzAdsManager` is a business layer manager that orchestrates ad configuration, initialization, and state management for the Oz Ads SDK. It provides a centralized singleton instance for managing all ad-related operations across your Android application.

## Key Features

- **Centralized Configuration**: Single source of truth for all ad settings
- **Reactive State Management**: Observable StateFlows for ad state changes
- **Fullscreen Ad Tracking**: Monitor when overlay ads (Interstitial, App Open) are showing
- **Thread-Safe**: Built with concurrency in mind using ConcurrentHashMap and volatile fields
- **Singleton Pattern**: Ensures consistent state across the entire application
- **Kotlin Coroutines Support**: Async initialization with suspend functions

## Architecture

```
OzAdsManager (Business Layer)
    ├── Configuration Management
    ├── Reactive State Flows
    ├── Ad State Management
    ├── Ad Storage
    └── Network Manager Integration (AdMobManager)
```

## Installation & Setup

### 1. Get Instance

```kotlin
val adsManager = OzAdsManager.getInstance()
```

### 2. Configure

```kotlin
// Option 1: Set complete configuration
val config = OzAdsConfig(
    isAdEnabled = true,
    testDeviceIds = listOf("YOUR_TEST_DEVICE_ID")
)
adsManager.setConfig(config)

// Option 2: Update specific fields using DSL
adsManager.updateConfig { 
    copy(isAdEnabled = true) 
}

// Option 3: Legacy method to toggle ads
adsManager.setEnableAd(true)
```

### 3. Initialize

```kotlin
class MainActivity : AppCompatActivity() {
    private val adsManager = OzAdsManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            adsManager.init(
                activity = this@MainActivity,
                onSuccess = {
                    Log.d("Ads", "Ads initialized successfully")
                },
                onError = { error ->
                    Log.e("Ads", "Failed to initialize ads", error)
                }
            )
        }
    }
}
```

## Reactive State Management

### Observing Ad Enable State

```kotlin
lifecycleScope.launch {
    adsManager.enableAd.collect { isEnabled ->
        if (isEnabled) {
            // Ads are enabled
        } else {
            // Ads are disabled
        }
    }
}
```

### Observing Fullscreen Ad State

Monitor when fullscreen overlay ads (Interstitial, App Open) are displayed:

```kotlin
lifecycleScope.launch {
    adsManager.isFullScreenAdShowing.collect { isShowing ->
        if (isShowing) {
            // Fullscreen ad is showing
            // Pause game, stop music, etc.
            pauseGameplay()
            stopBackgroundMusic()
        } else {
            // Fullscreen ad dismissed
            // Resume game, play music, etc.
            resumeGameplay()
            startBackgroundMusic()
        }
    }
}
```

## Fullscreen Ad State Control

### Automatic Updates

The SDK automatically updates the fullscreen ad state when overlay ads (Interstitial, App Open) are shown or dismissed through the `OverlayAds` class.

### Manual Control

> **⚠️ NOTICE**: You can manually mimic fullscreen ad showing/closing by calling these methods directly. This is useful for testing, debugging, or coordinating with external ad networks not integrated with the SDK.

```kotlin
// Manually trigger fullscreen ad showing state
adsManager.onAdsFullScreenShowing()

// Manually trigger fullscreen ad dismissed state
adsManager.onAdsFullScreenDismissed()
```

**Use Cases for Manual Control:**
- Testing UI behavior during ads without loading real ads
- Integrating with external ad networks (Unity Ads, Vungle, etc.)
- Debugging ad-related issues
- Coordinating with custom ad implementations

**Example: Testing Ad Behavior**

```kotlin
class AdTester {
    fun simulateInterstitialAd() {
        val adsManager = OzAdsManager.getInstance()
        
        // Simulate ad showing
        adsManager.onAdsFullScreenShowing()
        
        // Simulate ad dismissed after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            adsManager.onAdsFullScreenDismissed()
        }, 5000)
    }
}
```

**Example: External Ad Network Integration**

```kotlin
class UnityAdsWrapper {
    private val adsManager = OzAdsManager.getInstance()
    
    fun showInterstitial() {
        UnityAds.show(activity, "interstitial", object : IUnityAdsShowListener {
            override fun onUnityAdsShowStart(placementId: String) {
                // Notify OzAdsManager that fullscreen ad is showing
                adsManager.onAdsFullScreenShowing()
            }
            
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                // Notify OzAdsManager that fullscreen ad is dismissed
                adsManager.onAdsFullScreenDismissed()
            }
        })
    }
}
```

## Ad State & Storage Management

### Ad State Operations

```kotlin
// Get ad state
val state = adsManager.getAdState("banner_home")

// Set ad state
adsManager.setAdState("banner_home", AdState.LOADED)

// Set state only if absent
adsManager.putAdStateIfAbsent("banner_home", AdState.IDLE)
```

### Ad Storage Operations

```kotlin
// Store ad object
adsManager.setAd("banner_home", adObject)

// Retrieve ad object
val ad = adsManager.getAd<BannerAd>("banner_home")

// Remove ad object
adsManager.removeAd("banner_home")
```

## Configuration Properties

### OzAdsConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `isAdEnabled` | Boolean | `true` | Global flag to enable/disable all ads |
| `testDeviceIds` | List<String> | `emptyList()` | Test device IDs for ad testing |

## API Reference

### Configuration Methods

| Method | Description |
|--------|-------------|
| `setConfig(OzAdsConfig)` | Set complete configuration object |
| `updateConfig(block)` | Update specific fields using Kotlin DSL |
| `setEnableAd(Boolean)` | Legacy method to toggle ads on/off |

### Initialization Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `suspend fun init(Activity, onSuccess?, onError?)` | `OzAdsResult<Unit>` | Initialize ad SDK with callbacks |
| `isAdInitialized()` | `Boolean` | Check if ads are initialized |

### Fullscreen Ad State Methods

| Method | Description |
|--------|-------------|
| `onAdsFullScreenShowing()` | Called when fullscreen ad starts showing |
| `onAdsFullScreenDismissed()` | Called when fullscreen ad is dismissed |

### Reactive State Properties

| Property | Type | Description |
|----------|------|-------------|
| `enableAd` | `StateFlow<Boolean>` | Observable flag for ad enabled state |
| `isFullScreenAdShowing` | `StateFlow<Boolean>` | Observable flag for fullscreen ad state |
| `config` | `OzAdsConfig` | Current configuration (read-only) |

### Ad Management Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getAdState(String)` | `AdState` | Get state of ad by key |
| `setAdState(String, AdState)` | `Unit` | Set state of ad |
| `putAdStateIfAbsent(String, AdState)` | `Unit` | Set state only if not present |
| `getAd<T>(String)` | `T?` | Retrieve ad object by key |
| `setAd(String, Any)` | `Unit` | Store ad object |
| `removeAd(String)` | `Any?` | Remove and return ad object |

### Singleton Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstance(AdMobManager?)` | `OzAdsManager` | Get singleton instance |
| `resetInstance()` | `Unit` | Reset singleton (testing only) |

## Best Practices

### 1. Initialize Early

Initialize ads as early as possible, preferably in your Application class or main Activity:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        lifecycleScope.launch {
            OzAdsManager.getInstance().init(/* ... */)
        }
    }
}
```

### 2. Use Reactive Flows

Always observe state changes using StateFlow collectors rather than polling:

```kotlin
// ✅ Good: Reactive
lifecycleScope.launch {
    adsManager.isFullScreenAdShowing.collect { isShowing ->
        handleAdState(isShowing)
    }
}

// ❌ Bad: Polling
Timer().scheduleAtFixedRate(object : TimerTask() {
    override fun run() {
        val isShowing = adsManager.isFullScreenAdShowing.value
        handleAdState(isShowing)
    }
}, 0, 100)
```

### 3. Lifecycle Awareness

Use lifecycle-aware coroutine scopes to prevent memory leaks:

```kotlin
// In Activity/Fragment
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        adsManager.isFullScreenAdShowing.collect { /* ... */ }
    }
}
```

### 4. Configuration Updates

Use `updateConfig` for partial updates to avoid recreating the entire config:

```kotlin
// ✅ Good: Partial update
adsManager.updateConfig { copy(isAdEnabled = false) }

// ❌ Less efficient: Full replacement
val newConfig = adsManager.config.copy(isAdEnabled = false)
adsManager.setConfig(newConfig)
```

## Integration with Inline Ads

The `InlineAds` class (Banner, Native) automatically observes the fullscreen ad state and pauses when overlay ads are showing. No additional code is required.

```kotlin
// InlineAds automatically:
// 1. Pauses when fullscreen ad shows
// 2. Resumes when fullscreen ad dismisses
// 3. Stops refresh timers during pause
```

## Thread Safety

`OzAdsManager` is designed to be thread-safe:

- Uses `@Volatile` for singleton instance and configuration
- Uses `ConcurrentHashMap` for ad state and storage
- Uses `MutableStateFlow` for reactive state management
- All state updates are synchronized

## Testing

### Reset Instance (for Unit Tests)

```kotlin
@After
fun tearDown() {
    OzAdsManager.resetInstance()
}
```

### Mock Ad Behavior

```kotlin
@Test
fun testFullscreenAdBehavior() {
    val adsManager = OzAdsManager.getInstance()
    
    // Simulate ad showing
    adsManager.onAdsFullScreenShowing()
    assertEquals(true, adsManager.isFullScreenAdShowing.value)
    
    // Simulate ad dismissed
    adsManager.onAdsFullScreenDismissed()
    assertEquals(false, adsManager.isFullScreenAdShowing.value)
}
```

## Troubleshooting

### Ads Not Showing

1. Check if ads are enabled:
   ```kotlin
   if (!adsManager.enableAd.value) {
       // Ads are disabled
   }
   ```

2. Verify initialization:
   ```kotlin
   if (!adsManager.isAdInitialized()) {
       // SDK not initialized yet
   }
   ```

### State Not Updating

Ensure you're collecting flows in a coroutine scope:

```kotlin
lifecycleScope.launch {
    adsManager.isFullScreenAdShowing.collect { isShowing ->
        // This will be called on every state change
    }
}
```

## License

Copyright (c) 2024 Oz Android. All rights reserved.

## Support

For issues, questions, or contributions, please contact the Oz Android team.
