package com.oz.android.wrapper

// No imports from com.google.android.gms.ads.*

/**
 * A custom error class to keep the listener independent of Google's SDK.
 * The implementation layer (AdmobInterstitial/AdmobAppOpen) is responsible
 * for mapping the SDK specific error to this class.
 */
data class OzAdError(
    val code: Int,
    val message: String,
    val domain: String,
)

abstract class OzAdListener<AdType> {
    open fun onAdLoaded(ad: AdType) {}

    // Changed LoadAdError to OzAdError
    open fun onAdFailedToLoad(error: OzAdError) {}

    open fun onAdClicked() {}
    open fun onAdImpression() {}

    // Fullscreen content callbacks (for Interstitial, Rewarded, App Open ads)
    open fun onAdShowedFullScreenContent() {}
    open fun onAdDismissedFullScreenContent() {}

    // Changed AdError to OzAdError
    open fun onAdFailedToShowFullScreenContent(error: OzAdError) {}

    /**
     * Operator overloading to combine two listeners.
     */
     fun merge(other: OzAdListener<AdType>?): OzAdListener<AdType> {
        // If the other listener is null, just return this one (no merge needed)
        if (other == null) return this

        val first = this
        val second = other

        // Return a new anonymous listener that calls BOTH
        return object : OzAdListener<AdType>() {
            override fun onAdLoaded(ad: AdType) {
                first.onAdLoaded(ad)
                second.onAdLoaded(ad)
            }

            override fun onAdFailedToLoad(error: OzAdError) {
                first.onAdFailedToLoad(error)
                second.onAdFailedToLoad(error)
            }

            override fun onAdClicked() {
                first.onAdClicked()
                second.onAdClicked()
            }

            override fun onAdImpression() {
                first.onAdImpression()
                second.onAdImpression()
            }

            override fun onAdShowedFullScreenContent() {
                first.onAdShowedFullScreenContent()
                second.onAdShowedFullScreenContent()
            }

            override fun onAdDismissedFullScreenContent() {
                first.onAdDismissedFullScreenContent()
                second.onAdDismissedFullScreenContent()
            }

            override fun onAdFailedToShowFullScreenContent(error: OzAdError) {
                first.onAdFailedToShowFullScreenContent(error)
                second.onAdFailedToShowFullScreenContent(error)
            }
        }
    }
}