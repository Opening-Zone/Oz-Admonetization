/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oz.android.ads_core.admobs.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.oz.android.ads_core.BuildConfig
import com.oz.android.ads_core.admobs.AdMobManager
import com.oz.android.utils.event.OzEventLogger

/**
 * Manages User Messaging Platform (UMP) consent flows for Google Mobile Ads (GMA) SDK.
 *
 * Handles GDPR, CCPA, and regional user consent gathering, consent state updates,
 * analytics tracking, and presentation of privacy option forms.
 */
class GoogleMobileAdsConsentManager private constructor(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * Functional interface invoked when the consent gathering flow (info update + form display) completes.
     */
    fun interface OnConsentGatheringCompleteListener {
        /**
         * Triggered when consent gathering is finished.
         *
         * @param error [FormError] if an error occurred during request or form presentation, or null if successful.
         */
        fun consentGatheringComplete(error: FormError?)
    }

    /**
     * Indicates whether the current consent status allows sending ad requests.
     *
     * Delegates to [ConsentInformation.canRequestAds].
     */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /**
     * Indicates whether the user is required to have a privacy options entry point (e.g. Settings menu)
     * based on their geographic region (e.g. EEA/UK).
     */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Initiates the full consent gathering pipeline:
     * 1. Requests consent information updates from UMP SDK.
     * 2. Shows the consent form if required by law/region.
     * 3. Logs relevant consent events to [OzEventLogger].
     * 4. Triggers [onConsentGatheringCompleteListener] upon completion.
     *
     * @param activity The current foreground [Activity].
     * @param onConsentGatheringCompleteListener Callback fired when consent gathering completes.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener,
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .addTestDeviceHashedId(AdMobManager.TEST_DEVICE_HASHED_ID)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }
        val params = paramsBuilder.build()

        OzEventLogger.logConsentCheckStart(activity)

        // Request updated consent information on each app launch
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                OzEventLogger.logConsentCheckSuccess(activity)
                loadAndShowConsentFormIfRequired(activity, onConsentGatheringCompleteListener)
            },
            { requestConsentError ->
                OzEventLogger.logConsentCheckFailed(
                    activity,
                    requestConsentError.errorCode,
                    requestConsentError.message
                )
                // Attempt showing form if required despite update error, or finalize status
                loadAndShowConsentFormIfRequired(activity, onConsentGatheringCompleteListener)
            },
        )
    }

    /**
     * Loads and displays the UMP consent form if required by the current consent status.
     */
    private fun loadAndShowConsentFormIfRequired(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener,
    ) {
        // Capture whether a form will actually be shown BEFORE calling UMP.
        // loadAndShowConsentFormIfRequired fires its callback immediately for non-EEA users
        // (NOT_REQUIRED) and users with a cached decision (OBTAINED) without displaying
        // any form. Logging form_show/form_closed for those cases inflates event counts.
        val formWasShown = consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
        if (formWasShown) {
            OzEventLogger.logConsentFormShow(activity)
        }

        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formWasShown) {
                OzEventLogger.logConsentFormClosed(activity, formError?.message)
            }
            if (canRequestAds) {
                OzEventLogger.logConsentAdsReady(activity)
            } else {
                OzEventLogger.logConsentAdsBlocked(activity, formError?.message)
            }
            onConsentGatheringCompleteListener.consentGatheringComplete(formError)
        }
    }

    /**
     * Presents the privacy options form allowing users to modify their consent choices at any time.
     *
     * @param activity The current [Activity].
     * @param onConsentFormDismissedListener Callback triggered when the privacy form is dismissed.
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener,
    ) {
        OzEventLogger.logConsentFormShow(activity)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            OzEventLogger.logConsentFormClosed(activity, formError?.message)
            onConsentFormDismissedListener.onConsentFormDismissed(formError)
        }
    }

    companion object {
        @Volatile
        private var instance: GoogleMobileAdsConsentManager? = null

        /**
         * Returns the thread-safe singleton instance of [GoogleMobileAdsConsentManager].
         *
         * @param context Application or Activity context.
         */
        fun getInstance(context: Context): GoogleMobileAdsConsentManager =
            instance ?: synchronized(this) {
                instance ?: GoogleMobileAdsConsentManager(context.applicationContext).also { instance = it }
            }
    }
}