# OzAds Architecture

## Overview

The OzAds library provides a robust, scalable, and easy-to-use framework for integrating various ad networks into an Android application. It is designed with a clear separation of concerns, allowing developers to easily add new ad formats or networks with minimal effort.

The architecture is divided into three main layers:
1.  **Manager Layer (`OzAdsManager`)**: A singleton that orchestrates ad network initialization, centralizes ad object storage, and manages global ad states.
2.  **Business Logic Layer (`/oz_ads/ads_component`)**: Manages ad state, lifecycle, and presentation logic through base classes like `OzAds` and `InlineAds`.
3.  **Concrete Implementation Layer (`/oz_ads/ads_component/ads_inline/admob`)**: Connects a specific ad network (e.g., AdMob) to the business logic layer.
4.  **Network Layer (`/network`)**: Provides a thin wrapper around the ad network's SDK.

---

## Core Components

### 1. `OzAdsManager` (Singleton Manager)

The central hub of the library.
*   **Initialization**: Handles the initialization of all supported ad networks (e.g., AdMob) via `init()`.
*   **Ad Store**: Acts as a central repository, storing loaded ad objects globally using a key-based system.
*   **Global State**: Manages the global "enable/disable" state of ads.

### 2. `OzAds<AdType>` (Abstract Base Class)

This is the cornerstone of the ad business logic component. It's a generic abstract class extending `ViewGroup` responsible for:

*   **Orchestration**: Defines the core workflow for loading and showing ads through abstract methods (`createAd`, `onLoadAd`, `onShowAds`).
*   **Lifecycle**: Observes the global ad enabled state from `OzAdsManager`.

### 3. `InlineAds<AdType>` (Abstract `ViewGroup`)

`InlineAds` extends `OzAds` and is specialized for inline ads (Banner, Native) that sit within the UI content.

Key responsibilities include:
*   **Auto-Refresh**: Handles automatic refreshing of ads at configurable intervals.
*   **Lifecycle Awareness**: Automatically handles ad pausing, resuming, and destruction by observing Android's view lifecycle (`onAttachedToWindow`, `onDetachedFromWindow`, `onVisibilityChanged`).
*   **Visibility Management**: Pauses refresh logic when the view is not visible to save resources.

### 4. Concrete Implementations (e.g., `OzAdmobBannerAd`)

These classes provide the concrete implementation for a specific ad format and network.

*   It extends `InlineAds<AdmobBanner>` (or similar).
*   It implements the abstract methods to:
    *   **Create**: Instantiate the network-specific ad object and attach listeners.
    *   **Load**: Trigger the ad load on the network object.
    *   **Show**: Display the ad view within the `InlineAds` container.
    *   **Destroy**: Clean up the network object.

```kotlin
// Example: OzAdmobBannerAd
class OzAdmobBannerAd(context: Context) : InlineAds<AdmobBanner>(context) {

    // ...

    override fun createAd(key: String): AdmobBanner? {
        val adUnitId = getAdUnitId(key)
        // Create AdmobBanner with a listener that bridges back to OzAds events
        return AdmobBanner(context, adUnitId, object : OzAdmobListener<AdmobBanner>() {
            override fun onAdLoaded(ad: AdmobBanner) {
                this@OzAdmobBannerAd.onAdLoaded(key, ad)
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                this@OzAdmobBannerAd.onAdLoadFailed(key, error.message)
            }
        })
    }

    override fun onLoadAd(key: String, ad: AdmobBanner) {
        ad.load()
    }

    override fun onShowAds(key: String, ad: AdmobBanner) {
        ad.show(this) // 'this' is the ViewGroup
    }
}
```

---

## How to Use

1.  **Initialize SDK**: In your Application class or main Activity, initialize the `OzAdsManager`.

    ```kotlin
    lifecycleScope.launch {
        OzAdsManager.getInstance().init(this@MainActivity)
    }
    ```

2.  **Add to Layout**: Place the specific ad view (`OzAdmobBannerAd`) in your XML layout.

    ```xml
    <com.oz.android.ads.oz_ads.ads_component.ads_inline.admob.OzAdmobBannerAd
        android:id="@+id/my_banner_ad"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
    ```

3.  **Configure in Code**: In your Activity or Fragment, get a reference to the view and configure it.

    ```kotlin
    val bannerAd = findViewById<OzAdmobBannerAd>(R.id.my_banner_ad)
    
    // Set the Ad Unit ID for a specific placement (this also sets the preload key)
    bannerAd.setAdUnitId("my_placement_key", "ca-app-pub-...")
    
    // Optional: Set refresh time (default is 30s)
    bannerAd.setRefreshTime(60_000L)
    
    // The ad will automatically load and show based on view lifecycle
    // You can also manually load if needed, but it's usually automatic.
    ```

4.  **Lifecycle Management**: The `InlineAds` view automatically handles `pause`, `resume`, and `destroy` events based on the View's attachment state and visibility. No manual calls are typically needed in the Activity/Fragment lifecycle methods.
