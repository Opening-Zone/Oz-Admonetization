# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.6] - 2026-08-13

### Changed
- **Next Gen Integration**: Integrated latest next gen ad features and refactored revenue logging to standard `AD_IMPRESSION`.

## [1.0.5] - 2026-08-02

### Added
- **Default L3 Consent Wiring (`P1`)**: `AdmobNextManager` and `AdMobManager` now automatically register `setConsentChecker { GoogleMobileAdsConsentManager.getInstance(context).canRequestAds }` during `initializeMobileAdsSdk()`. Guarantees consent state is checked out-of-the-box without requiring callers to pass a manual lambda.
- **Telemetry Event `ads_sdk_init_timeout` (`P4`)**: `OzAdsManager.awaitInitialization()` and `OzAdsManager.init()` now log `ads_sdk_init_timeout` (`timeout_ms`, `sdk_type`) if initialization exceeds timeout before completing.
- **Configurable Init Timeout (`P5`)**: Added `initTimeoutMs: Long = 5_000L` property to `OzAdsConfig` and enforced it in `OzAdsManager.init()` via `withTimeoutOrNull(config.initTimeoutMs)` so application callers can tune or A/B test SDK initialization timeout without risk of hanging forever.

### Fixed
- **Version Catalog Synchronization (`P7`)**: Updated `playServicesAds` version in `gradle/libs.versions.toml` to `25.4.0` to match `build.gradle.kts`.

### Architecture & Policy Notes
- **Omitted Error Retry (`P3 Policy Note`)**: Retrying ad requests immediately after a load failure / `NO_FILL` is strictly unrecommended by AdMob policies and best practices as it creates wasteful network traffic and potential policy violations. Error retry logic has been intentionally omitted from `InlineAds.kt` and `OzAdsConfig.kt`.
- **Mediation Adapter Placement (`P8 Clarification`)**: The core `android-ads` library deliberately omits third-party mediation adapter dependencies (Pangle, Vungle, Mintegral, AppLovin, Unity, Facebook). Mediation adapters are intentionally declared at the **application level** (`app/build.gradle.kts`) to allow each host app to customize its monetization stack and mediation strategy independently based on target audience and program policies (e.g. Designed for Families).

### Deferred / Skipped
- **Zone B Post-Timeout Retry (`P2`)**: Deferred post-timeout retry logic until telemetry data from `ads_sdk_init_timeout` (P4) quantifies production timeout frequency.

---

## [1.0.4] - 2026-08-01

### Added
- **Init Retry Queue (`REM-01`)**: Added fallback `ProcessLifecycleOwner` lifecycle scope and automatic retry queue in `OzAds.kt` when `OzAdsManager` is not yet initialized. Avoids permanent ad loss during app cold start. Safe cleanup on view destruction (`initRetryJob?.cancel()`).
- **Decoupled Consent Injection (`REM-02`)**: Added network-agnostic `consentChecker: () -> Boolean` property to `OzAdsManager` (injected via `init(activity, ..., consentChecker)`). Pre-checks consent state before `loadAd()` calls without breaking 4-layer clean architecture boundaries.
- **`app_resume` Instrumentation (`REM-07`)**: Added detailed tracking for App Resume/Open ads with 6 distinct `reason` values (`resume_disabled`, `already_showing`, `disabled_by_click_action`, `activity_disabled`, `splash_activity`, `lifecycle_not_started`).
- **Banner Refresh Analytics (`REM-06`)**: Added `ad_refreshed` and `ad_refresh_failed` events when banner ads perform auto-refresh.
- **Ad Expiration Tracking (`REM-09`)**: Added `logAdExpired` analytics for `state_loaded_store_empty`, `at_load_time`, and `at_show_time`.
- **`BuildConfig.LIB_VERSION` (`BLK-03`)**: Exposed library version field in `BuildConfig` for client analytics attribution.

### Fixed
- **Single-Layer Analytics (`FIX-01` - `FIX-08`)**: Centralized event logging to `OzAds.kt` orchestrator to eliminate duplicate event firing from network adapters. Updated format logging with correct `adUnitId` and `adFormat`.
- **AdMob Error Codes (`FIX-04`)**: Replaced invalid `.ordinal` mapping with raw GMS/GMA error code mapping (`LoadAdError.toOzError()`), fixing incorrect error bucket classification in GA4.
- **Impression-Based Banner Showing (`FIX-03`)**: Shifted banner showing confirmation from view attachment to `onAdImpression()` callback. Removed deprecated `ad_show_success` event in favor of `ad_show_called` + `ad_impression`.
- **Reserved Firebase Names (`FIX-05`)**: Removed manual `ad_click` logging (Firebase reserved name) in favor of `ad_clicked_custom`.
- **Manifest App ID Validation (`REM-08`)**: Removed silent fallback to public test App ID on release builds. Throws descriptive error in DEBUG and returns failure result in release.
- **Restored `show_blocked` Tracking (`REG-01`)**: Restored `logAdSkip` when fullscreen ads are blocked due to active cooldown or ongoing fullscreen ads. Normalized reasons to `fullscreen_busy` and `cooldown_active`.
- **Duplicate Revenue Events (`REM-04`)**: Removed duplicate/custom `app_event_impression` event in favor of standardized `ad_impression` (`FirebaseAnalytics.Event.AD_IMPRESSION`).

### Changed
- **Renamed Event (`REM-05`)**: Renamed `ad_opportunity` to `ad_load_attempt` to accurately reflect load request intent. Marked `logAdOpportunity` as `@Deprecated`.
- **Init Method Signature**: Adjusted `init()` parameters in `OzAdsManager` so `consentChecker` is optional and placed last to preserve backward compatibility for positional callers.

---

## [1.0.1] - 2026-05-26

### Fixed
- **Overlay Ads**: Resolved a race condition where multiple fullscreen overlay ads could render simultaneously by adding `OzAdsManager.getInstance().canShowFullScreenAd()`.
- **Banner Ads (Lifecycle & Refresh)**: Ensured loaded ad is only passed to parent when state is strictly `LOADING`. Added `detachFromParent()` before destruction.
- **Native Ads (R8/Proguard & Threading)**: Switched from dynamic resource ID resolution to compile-time static references (`R.id.*`).
- **Native Ads (Main-thread Dispatch)**: Enforced main-thread execution for `adLoader.loadAd(...)`.
- **Base Lifecycle Management**: Cleaned up pending ad display callbacks (`clearPendingShow(key)`) upon view destruction.
