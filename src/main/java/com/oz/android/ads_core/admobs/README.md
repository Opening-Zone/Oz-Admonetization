# AdMob Integration Core (`admobs`)

This package forms the core engine of the OzAds library's AdMob integration, housing both the **Standard GMS AdMob SDK** and **GMA Next-Gen SDK** implementations behind clean, polymorphic interfaces.

---

## 📂 Package Reorganization & Structure

To keep the library clean and maintainable, all AdMob components are consolidated under this package and grouped by their respective ad formats:

```
admobs/
├── AdmobBase.kt                      # Abstract base class sharing common parameters/listeners
├── AdMobManager.kt                   # Core initializer for Standard GMS Mobile Ads
├── README.md                         # General package architecture documentation
├── README_NEXT_GEN.md                # General Next-Gen SDK setup guide
│
├── consent/
│   └── GoogleMobileAdsConsentManager.kt # Consent flow and GDPR compliance manager
│
├── app_open/
│   ├── AdmobAppOpen.kt               # Unified Interface Contract
│   ├── AdmobStandardAppOpen.kt       # GMS implementation
│   ├── AdmobNextAppOpen.kt           # Next-Gen implementation
│   └── README.md                     # App Open integration documentation
│
├── banner/
│   ├── AdmobBanner.kt                # Unified Interface Contract
│   ├── AdmobStandardBanner.kt        # GMS implementation
│   ├── AdmobNextBanner.kt            # Next-Gen implementation
│   └── README.md                     # Banner integration documentation
│
├── interstitial/
│   ├── AdmobInterstitial.kt          # Unified Interface Contract
│   ├── AdmobStandardInterstitial.kt  # GMS implementation
│   ├── AdmobNextInterstitial.kt      # Next-Gen implementation
│   └── README.md                     # Interstitial integration documentation
│
├── native_advanced/
│   ├── AdmobNativeAdvanced.kt        # Unified Interface Contract
│   ├── AdmobStandardNativeAdvanced.kt# GMS implementation
│   ├── AdmobNextNativeAdvanced.kt    # Next-Gen implementation
│   └── README.md                     # Native Advanced integration documentation
│
└── reward/
    ├── AdmobReward.kt                # Unified Interface Contract
    ├── AdmobStandardReward.kt        # GMS implementation
    ├── AdmobNextReward.kt            # Next-Gen implementation
    └── README.md                     # Rewarded integration documentation
```

---

## 🏗️ How it Works: Polymorphic Factory Pattern

Instead of relying on internal class-level delegation (which causes redundant allocations and boilerplate), the library employs a **Polymorphic Interface + Companion Factory** pattern:

1. **Unified Interface**: Each ad format exposes a public, network-agnostic interface (e.g. `AdmobBanner`).
2. **Companion Factory**: Every interface has a companion `create` factory method that resolves the implementation dynamically based on the configuration of `OzAdsConfig.adsCoreType`:

```kotlin
// Example Factory Pattern used in all 5 formats
companion object {
    fun create(
        context: Context,
        adUnitId: String,
        listener: OzAdListener<AdmobBanner>? = null
    ): AdmobBanner {
        return if (OzAdsManager.getInstance().config.adsCoreType == AdsCoreType.ADMOB_NEXT_GEN) {
            AdmobNextBanner(context, adUnitId, listener)
        } else {
            AdmobStandardBanner(context, adUnitId, listener)
        }
    }
}
```

This guarantees that the client app and core view wrappers only interact with the unified interface contracts, hiding all SDK-specific classes from the consumer app level.

---

## 🔄 GMS Standard vs. GMA Next-Gen SDK Comparison

The codebase compiles and supports both SDK architectures. Below are the key architectural differences:

| Feature | Standard GMS SDK (`AdmobStandard*`) | GMA Next-Gen SDK (`AdmobNext*`) |
|---|---|---|
| **Package Names** | `com.google.android.gms.ads.*` | `com.google.android.libraries.ads.mobile.sdk.*` |
| **Initialization Thread** | Main Thread | **Background Thread** (requires `CoroutineScope(Dispatchers.IO)`) |
| **Lifecycle Hooks** | Requires `pause()`, `resume()`, `destroy()` in Views | Only requires `destroy()` for resource cleanup |
| **View Hierarchies** | Heavy subclassing (e.g., `AdView`, `NativeAdView`) | Lightweight components |
| **Mediation** | Fully mature mediation adapter system | Next-gen unified bidding & adapters |
| **Native Ad Container** | Requires `<com.google.android.gms.ads.nativead.NativeAdView>` XML root | Requires `<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView>` wrapper |

### 🛠️ Decoupled Layouts & Dynamic Wrapping (Native Ads)
To prevent compile-time duplicate class errors (since `ads-mobile-sdk` bundles legacy API classes and conflicts with `play-services-ads`), the application module excludes `play-services-ads-api`. 
1. **XML Decoupling**: All native ad layout XML roots were changed to standard `<FrameLayout>` so the View/Data Binding compiler does not depend on SDK-specific classes.
2. **Runtime Wrapping**: Both `AdmobStandardNativeAdvanced` and `AdmobNextNativeAdvanced` dynamically create their respective `NativeAdView` classes, add the layout view, and register views to track impressions/clicks.

### 🪞 Reflective Helpers
Utility functions like debug menu options or test device IDs are only present in standard GMS. In `OzAdsManager`, these calls are isolated using reflection:
- `openDebugMenu(activity, adUnitId)`
- `setTestDeviceIds(deviceIds)`

This allows next-gen configurations to run cleanly without compiling against conflicting GMS configuration dependencies in client layouts or classes.
