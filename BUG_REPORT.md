# Bug Investigation & Resolution Report

This document compiles the analysis and resolution details for the critical bugs addressed in the recent library stabilization commits (from `v1.0.0` to `v1.0.1`).

---

## 1. Fullscreen Overlay Ads Overlapping
- **Affected Components**: `OverlayAds.kt`, `OzAdsManager.kt`
- **Severity**: High
- **Description**:
  When multiple full-screen overlay ads (such as Interstitial ads and App Open ads) were requested in quick succession, they could overlap and cover each other on screen, causing visual glitches, policy violations, and poor user experience.
- **Root Cause**:
  `OverlayAds.showAds()` lacked a global check to verify if another fullscreen ad was currently active before rendering.
- **Resolution**:
  - Implemented `canShowFullScreenAd()` in the singleton `OzAdsManager` to check if `_isFullScreenAdShowing` is active.
  - Added a guard condition in `OverlayAds.showAds()` to immediately abort and trigger `onAdShowFailed` if a fullscreen ad is already active.

---

## 2. WebView Crash and Disappearing Banner Ads
- **Affected Components**: `AdmobBanner.kt`, `OzAdmobBannerAd.kt`
- **Severity**: Medium
- **Description**:
  - Banner ads could suddenly disappear or render blank spaces when automatic refresh occurred.
  - A crash could occur during View destruction with the warning: `WebView.destroy() called while WebView is still attached`.
- **Root Cause**:
  - The banner's auto-refresh trigger bypassed state checks and pushed newly loaded banners regardless of the current active lifecycle state, leading to rendering conflicts.
  - Banners were destroyed directly while still attached to their parent `ViewGroup`.
- **Resolution**:
  - Added a state-validation check in `OzAdmobBannerAd.onAdLoaded`: the loaded ad is only committed to the parent layout if the key's state is strictly `AdState.LOADING` (ignoring out-of-order refresh callbacks).
  - Implemented `detachFromParent()` in `AdmobBanner` to cleanly decouple the `AdView` from its parent before executing `destroy()`.

---

## 3. R8 / Proguard Resource Shrinking Binding Failures in Native Ads
- **Affected Components**: `OzAdmobNativeAd.kt`, `ids.xml`
- **Severity**: High
- **Description**:
  When building the application in Release mode with resource shrinking enabled (`shrinkResources = true`), all standard subviews in `OzAdmobNativeAd` (headline, body, media view, call-to-action) failed to bind and render, resulting in empty or broken native ads.
- **Root Cause**:
  `OzAdmobNativeAd` was binding standard view IDs dynamically using reflection via `context.resources.getIdentifier(idName, "id", packageName)`. Because no static references to these layout IDs existed in the code, the R8 compiler optimized them out or renamed them, causing `getIdentifier` to return `0` (not found).
- **Resolution**:
  - Discarded the reflection-based `findIdAndSet` mechanism.
  - Created a static resources file `src/main/res/values/ids.xml` containing common ad component IDs (`ad_headline`, `ad_body`, `ad_media`, etc.).
  - Replaced reflection-based lookup with direct static references: `nativeAdView.findViewById(R.id.ad_headline)`.

---

## 4. Main-Thread Violations in Native Ad Loading
- **Affected Components**: `AdmobNativeAdvanced.kt`
- **Severity**: Medium
- **Description**:
  In certain background-preloading scenarios, the library triggered AdMob Native loader from background worker threads, causing exceptions or warnings from the Google Mobile Ads SDK which mandates UI thread execution.
- **Root Cause**:
  `adLoader.loadAd(...)` was called inside a coroutine context without enforcing the dispatcher.
- **Resolution**:
  - Enforced main-thread thread-safety by wrapping the `adLoader.loadAd` invocation with `withContext(Dispatchers.Main)`.

---

## 5. Memory Leaks and Crashes via Pending Activities
- **Affected Components**: `AdmobAppOpen.kt`, `AdmobInterstitial.kt`, `AdmobReward.kt`, `AdmobRewardedInterstitial.kt`
- **Severity**: High
- **Description**:
  If a full-screen ad was loading while the user rotated the screen or navigated away, the library kept a hard reference to the original `Activity` instance in a `pendingActivity` field to display it automatically upon load completion. This caused:
  1. A major memory leak (retaining the destroyed activity).
  2. A crash if the loading completed and the library attempted to display the ad using the destroyed activity context.
- **Root Cause**:
  Base ad classes were directly managing the activity-binding state flow.
- **Resolution**:
  - Removed all `pendingActivity` and `pendingRewardCallback` fields from base AdMob components.
  - Cleaned up automated `loadThenShow()` logic in lower-level classes. High-level wrapper components (such as `OzAdmobIntersAd` or `OzAdmobOpenAd`) now handle proper lifecycle-safe activity attachments.
