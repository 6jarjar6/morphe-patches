package app.morphe.patches.bejeweled

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.Constants

/**
 * Telemetry-disabled patch for Bejeweled Classic.
 *
 * Stopping telemetry requires cutting MORE than the Nimble/Synergy gate — the
 * game bundles a far wider analytics / ad stack. This patch neutralises each
 * live channel at its cheapest, safest choke point:
 *
 *  1. Nimble/Synergy (the game's own tracker)
 *     - [IsAbleToPostEventFingerprint] — force `isAbleToPostEvent` to always return
 *       false; tracking events queue locally but never POST.
 *     - [OperationalTelemetryLogEventFingerprint] — drop the secondary operational stream.
 *  2. Firebase (Analytics + Crashlytics + ML + auth)
 *     - [CctTransportSendFingerprint] — the one DataTransport egress method; turned
 *       into a thrown IOException so every upload is "deferred" and never leaves the device.
 *  3. GLUAnalytics (the central EA analytics hub)
 *     - `init` / `logEvent` / `doLogEvent` (both overloads) — early-return so the hub
 *       neither starts nor accumulates/dispatches behaviour events.
 *  4. Facebook Audience Network — `AudienceNetworkAds.initialize` early-return; the SDK
 *     never boots.
 *  5. Unity Ads — all four `UnityAds.initialize` overloads early-return; the SDK never
 *     boots (cuts its mediation fan-out to Meta/Instagram/Amazon/Kinesis).
 *
 * Each kill is a tracking-only neutralisation: the game keeps running, and no
 * gameplay-dependent path (store catalog, purchases, age-gate) is touched.
 */
@Suppress("SpellCheckingInspection")
val bejeweledTelemetryDisabled = bytecodePatch(
    name = "Telemetry-disabled",
    description = "Stops Nimble/Synergy, Firebase, GLUAnalytics, Facebook and Unity telemetry.",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        // ------------------------------------------------------------------
        // 1. Nimble/Synergy — never POST a tracking event.
        // ------------------------------------------------------------------
        IsAbleToPostEventFingerprint.method.returnEarly(false)

        // ------------------------------------------------------------------
        // 2. Operational telemetry (internal metrics) — drop the event.
        // ------------------------------------------------------------------
        OperationalTelemetryLogEventFingerprint.method.returnEarly()

        // ------------------------------------------------------------------
        // 3. Firebase — swallow the DataTransport egress. Every destination
        //    (Analytics, Crashlytics, ML Kit, auth) funnels through CctTransport
        //    Backend.send; making it return a junk reference fails every upload
        //    (transport retries indefinitely) and nothing leaves the device.
        // ------------------------------------------------------------------
        CctTransportSendFingerprint.method.returnNullAtStart()

        // ------------------------------------------------------------------
        // 4. GLUAnalytics hub — never start, record or dispatch.
        // ------------------------------------------------------------------
        GluAnalyticsInitFingerprint.method.returnEarly()
        GluAnalyticsLogEventFingerprint.method.returnEarly()
        gluAnalyticsDoLogOverloads.forEach { it.method.returnEarly() }

        // ------------------------------------------------------------------
        // 5. Facebook Audience Network — SDK never boots.
        // ------------------------------------------------------------------
        AudienceNetworkAdsInitFingerprint.method.returnEarly()

        // ------------------------------------------------------------------
        // 6. Unity Ads — SDK never boots (cuts the mediation fan-out).
        // ------------------------------------------------------------------
        unityAdsInitOverloads.forEach { it.method.returnEarly() }

        // ------------------------------------------------------------------
        // PHASE 3 — the residual channels seen after Phase-2.
        // ------------------------------------------------------------------

        // AWS SDK — Kinesis / Cognito / STS (the single HTTP egress).
        AmazonHttpClientExecuteFingerprint.method.returnNullAtStart()

        // Unity Ads SDK internals (facade-kill misses the backing layers).
        UnityAdsImplementationInitFingerprint.method.returnEarly()
        UnityAdsSDKInitializeFingerprint.method.returnNullAtStart()
        UnityAdsSDKInitializeDefaultFingerprint.method.returnNullAtStart()
        CommonGatewayClientExecuteRequestFingerprint.method.returnNullAtStart()
        CommonGatewayClientExecuteWithRetryFingerprint.method.returnNullAtStart()

        // Nimble tracking — event intake + post path + reachability pings.
        TrackingWranglerLogEventFingerprint.method.returnEarly()
        NimbleTrackingSynergyImplPostFingerprint.method.returnEarly()
        NetworkImplDetectFingerprint.method.returnEarly()

        // Firebase Crashlytics — bootstrap fetch + settings URL.
        RemoteSettingsFetcherDoConfigFetchFingerprint.method.returnNullAtStart()
        RemoteSettingsFetcherSettingsUrlFingerprint.method.returnNullAtStart()

        // GLUAnalytics hub transports — geo publish, session-id, CCS putData.
        CacheServicePutDataFingerprint.method.returnEarly()
        GluAnalyticsUnsyncLogGeoFingerprint.method.returnEarly()
        GluAnalyticsUnsyncSessionIDFingerprint.method.returnEarly()
        GluAnalyticsSendDataToCacheServiceFingerprint.method.returnEarly()
    }
}

/**
 * Prepends a "return null" preamble at the top of [m]'s body:
 *   const/4 vN, 0        (put zero bits = null into a free register)
 *   return-object vN     (return that null reference)
 *
 * Valid for any reference-returning method. `return-null` is not a representable
 * opcode in the Morphe smali fork, so this two-instruction form is used instead.
 * Allocates one free register.
 */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.returnNullAtStart() {
    val reg = this.findFreeRegister(0)
    this.addInstruction(0, "const/4 v$reg, 0")
    this.addInstruction(1, "return-object v$reg")
}
