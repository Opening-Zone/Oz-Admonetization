![logo](logo.png)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
![Github Actions](https://github.com/InsertKoinIO/koin/actions/workflows/build.yml/badge.svg)
[![Apache 2 License](https://img.shields.io/github/license/InsertKoinIO/koin)](https://github.com/InsertKoinIO/koin/blob/main/LICENSE.txt)

# OzAds Wrapper

## Overview
This module provides easy-to-use wrappers for integrating AdMob ads into your Android application. It supports Banner, Native, Interstitial, and App Open ads, compatible with both Android View System (XML) and Jetpack Compose.

## Setup

### 1. Configure AdMob App ID
If you are using AdMob, you **must** update your `AndroidManifest.xml` with your AdMob App ID. Failure to add this tag may result in a crash on app launch.

Add the `<meta-data>` tag inside your `<application>` element:

```xml
<manifest>
    <application>
        <!-- ... other configuration ... -->

        <!-- Replace "YOUR_ADMOB_APP_ID" with your actual AdMob App ID -->
        <!-- Example Test ID: ca-app-pub-3940256099942544~3347511713 -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="YOUR_ADMOB_APP_ID"/>
            
    </application>
</manifest>
```

### 2. Configure OzAdsManager
Initialize the `OzAdsManager` in your Application class or main Activity before using any ad components.

```kotlin
import com.oz.android.wrapper.OzAdsManager

// In onCreate of Application or Activity
OzAdsManager.getInstance().init(context)
```

---

## Components

### 1. Banner Ad (`OzAdmobBannerAd`)
A wrapper for displaying banner ads.

#### XML Usage
```xml
<com.oz.android.wrapper.OzAdmobBannerAd
    android:id="@+id/banner_ad"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
val bannerAd = findViewById<OzAdmobBannerAd>(R.id.banner_ad)
bannerAd.setAdUnitId("banner_home", "ca-app-pub-3940256099942544/6300978111")
bannerAd.loadThenShow()
// Optional: Auto refresh every 30 seconds
bannerAd.setRefreshTime(30000) 
```

#### Jetpack Compose Usage
Use `AndroidView` to embed the banner.

```kotlin
AndroidView(
    factory = { context ->
        OzAdmobBannerAd(context).apply {
            setAdUnitId("banner_compose", "ca-app-pub-3940256099942544/6300978111")
            loadThenShow()
        }
    },
    modifier = Modifier.fillMaxWidth()
)
```

---

### 2. Native Ad (`OzAdmobNativeAd`)
A wrapper for native ads, allowing custom layouts.

#### XML Usage
**Prerequisite**: Create a custom native ad layout (e.g., `layout_native_ad.xml`).

```xml
<com.oz.android.wrapper.OzAdmobNativeAd
    android:id="@+id/native_ad"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
val nativeAd = findViewById<OzAdmobNativeAd>(R.id.native_ad)
nativeAd.setAdUnitId("native_home", "ca-app-pub-3940256099942544/2247696110")
nativeAd.setLayoutId(R.layout.layout_native_ad) // Set your custom layout
nativeAd.loadThenShow()
```

#### Jetpack Compose Usage

```kotlin
AndroidView(
    factory = { context ->
        OzAdmobNativeAd(context).apply {
            setAdUnitId("native_compose", "ca-app-pub-3940256099942544/2247696110")
            setLayoutId(R.layout.layout_native_large)
            loadThenShow()
        }
    },
    modifier = Modifier
        .fillMaxWidth()
        .height(300.dp) // Adjust height as needed
)
```

---

### 3. Interstitial Ad (`OzAdmobIntersAd`)
A wrapper for full-screen interstitial ads.

#### Usage (Common for XML & Compose)

```kotlin
val intersAd = OzAdmobIntersAd(context)
intersAd.setAdUnitId("inter_level_complete", "ca-app-pub-3940256099942544/1033173712")

// Load and show when ready
intersAd.loadThenShow(activity)

// OR Load separately
intersAd.loadAd()
// ... later ...
intersAd.show(activity)
```

**With Listener:**
```kotlin
intersAd.listener = object : OzAdListener<AdmobInterstitial>() {
    override fun onAdShowedFullScreenContent() {
        // Navigate or perform action when ad closes
    }
}
```

---

### 4. App Open Ad (`OzAdmobOpenAd`)
A wrapper for App Open ads, typically shown on app launch or resume.

#### Usage (Common for XML & Compose)

```kotlin
val appOpenAd = OzAdmobOpenAd(context)
appOpenAd.setAdUnitId("app_open", "ca-app-pub-3940256099942544/9257395921")

// Load and show immediately
appOpenAd.loadThenShow(activity)

// Check cooldown (optional)
if (appOpenAd.getRemainingCooldownTime() == 0L) {
    appOpenAd.show(activity)
}
```
## License

Copyright 2025 Opening Zone

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
