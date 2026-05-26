# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-05-26

### Fixed
- **Overlay Ads**: Resolved a race condition where multiple fullscreen overlay ads (such as Interstitial and App Open ads) could overlap and render simultaneously by adding a guard clause that checks the global fullscreen ad showing state (`OzAdsManager.getInstance().canShowFullScreenAd()`).
- **Banner Ads (Lifecycle & Refresh)**: Fixed banner ad disappearing issues by ensuring that the loaded ad is only passed to the parent when the state is strictly `LOADING`. Added `detachFromParent()` to `AdmobBanner` before destroying the ad, preventing the Android WebView warning/crash (`WebView.destroy() called while WebView is still attached`).
- **Native Ads (R8/Proguard & Threading)**: Switched from dynamic resource ID resolution (`resources.getIdentifier`) to compile-time static references (`R.id.*`) in `OzAdmobNativeAd` to prevent layout binding failures when Proguard resource shrinking is enabled. Explicitly defined ad view IDs in `src/main/res/values/ids.xml`.
- **Native Ads (Main-thread Dispatch)**: Enforced main-thread execution for `adLoader.loadAd(...)` inside `AdmobNativeAdvanced` using `withContext(Dispatchers.Main)` to guarantee thread safety.
- **Base Lifecycle Management**: Fixed a potential crash by cleaning up the pending ad display callbacks (`clearPendingShow(key)`) upon view destruction. Corrected state preservation issues to ensure destroyed view instances can be reloaded properly.
- **Memory Management & App Stability**: Removed obsolete `pendingActivity` and `pendingRewardCallback` fields from base AdMob components (`AdmobAppOpen`, `AdmobInterstitial`, `AdmobReward`, `AdmobRewardedInterstitial`) to prevent memory leaks and crashes when activity context changes, delegating load-then-show behaviors to higher-level orchestrator classes.
