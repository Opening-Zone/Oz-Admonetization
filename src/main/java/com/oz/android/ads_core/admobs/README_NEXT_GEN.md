Set up GMA Next-Gen SDK



Select platform: AndroidNew-selected Android iOS Unity Flutter


Integrating GMA Next-Gen SDK into an app is the first step toward displaying ads and earning revenue. Once you've integrated the SDK, you can choose an ad format (such as native or rewarded video) and follow the steps to implement it.

Before you begin
To prepare your app, complete the steps in the following sections.

App prerequisites
Make sure that your app's build file uses the following values:

Minimum SDK version of 24 or higher
Compile SDK version of 35 or higher
For Kotlin apps, use the minimum Kotlin version 1.9.
Set up your app in your AdMob account
Register your app as an AdMob app by completing the following steps:

Sign in to or sign up for an AdMob account.

Register your app with AdMob. This step creates an AdMob app with a unique AdMob App ID that is needed later in this guide.

Configure your app
In your Gradle settings file, include the Google's Maven repository and Maven central repository:

Kotlin
Groovy

pluginManagement {
repositories {
google()
mavenCentral()
gradlePluginPortal()
}
}

dependencyResolutionManagement {
repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
repositories {
google()
mavenCentral()
}
}

rootProject.name = "My Application"
include(":app")
Add the dependencies for GMA Next-Gen SDK to your app-level build file:

Kotlin
Groovy

dependencies {
implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1")
}
Click Sync Now. For details on syncing, see Sync projects with Gradle files.

Initialize the GMA Next-Gen SDK
Key Point: You must initialize GMA Next-Gen SDK before loading ads and interacting with other MobileAds methods, unless explicitly noted in API reference docs (see getVersion()). Otherwise, an UninitializedPropertyAccessException may be thrown.
Call MobileAds.initialize() to initialize GMA Next-Gen SDK. This must be called on a background thread, failure to do so may cause an "Application Not Responding" (ANR) error.

Kotlin
Java

import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
setContentView(R.layout.activity_main)

    val backgroundScope = CoroutineScope(Dispatchers.IO)
    backgroundScope.launch {
      // Initialize GMA Next-Gen SDK on a background thread.
      MobileAds.initialize(
        this@MainActivity,
        // Sample AdMob app ID: ca-app-pub-3940256099942544~3347511713
        InitializationConfig.Builder("SAMPLE_APP_ID").build()
      ) {
        // Adapter initialization is complete.
      }
      // SDK initialization is complete. If you don't want to wait for bidding adapters to finish
      // initializing, start loading ads now.
    }
}
}
This method initializes the SDK and calls a completion listener once both GMA Next-Gen SDK and adapter initializations have completed, or after a 30-second timeout. This needs to be done only once, ideally at app launch.

If you're using AdMob Mediation, wait until the completion handler is called before loading ads. This ensures that all mediation adapters are initialized.

Note: Google User Messaging Platform (UMP) SDK requires the app ID in your app's AndroidManifest.xml file. For details, see Add the application ID.
Ads may be preloaded by GMA Next-Gen SDK or mediation partner SDKs upon initialization. If you need to obtain consent from users in the European Economic Area (EEA), set any request-specific flags, such as RequestConfiguration.TagForChildDirectedTreatment or RequestConfiguration.TagForUnderAgeOfConsent, or otherwise take action before loading ads, ensure you do so before initializing GMA Next-Gen SDK.

