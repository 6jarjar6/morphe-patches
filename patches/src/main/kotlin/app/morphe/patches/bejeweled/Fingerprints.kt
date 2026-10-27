package app.morphe.patches.bejeweled

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fingerprints for the Bejeweled Classic ad drivers.
 *
 * The game's logic is largely native (Nimble, libBejeweled3.so); the Java layer is
 * the glue that renders ads. We neutralise the Java ad drivers:
 *
 *  - GluAds funnel (csdk.gluads.impl.AdvertisingImpl) — the central ad dispatcher
 *    for interstitial / rewarded / offer-wall / banner ads.
 *  - Google IMA (com.popcap.pcsp.marketing.GoogleImaDriver) — interactive video ads.
 *  - Draper (com.ea.game.bejeweled3.DraperImageDriver) — image ads.
 */

/**
 * AdvertisingImpl.show(String adType, String placement, Map extras)
 *
 * The single method every GluAds ad type funnels through. We replace its body with
 * a synthetic completion: emit showStarted, grant the configured reward for rewarded
 * ads, then emit showFinished, so the native side advances as if the ad played.
 */
object AdvertisingImplShowFingerprint : Fingerprint(
    definingClass = "Lcsdk/gluads/impl/AdvertisingImpl;",
    name = "show",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/util/Map;"),
    filters = listOf(
        string("placement can't be null."),
        methodCall(definingClass = "Lcsdk/gluads/util/Common;", name = "require"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Ljava/util/Map;"),
        methodCall(definingClass = "Lcsdk/gluads/IAdvertising;", name = "show")
    )
)

/**
 * GoogleImaDriver.ShowAd() -> Boolean
 *
 * Returns true if the interactive video ad can be shown. We force it to return false
 * so the ad never plays.
 */
object GoogleImaDriverShowAdFingerprint : Fingerprint(
    definingClass = "Lcom/popcap/pcsp/marketing/GoogleImaDriver;",
    name = "ShowAd",
    isStatic = false,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("Starting interactive ad"),
        methodCall(definingClass = "Landroid/util/Log;", name = "d")
    )
)

/**
 * DraperImageDriver.ShowAd() -> Boolean
 *
 * Launches the Draper ad activity. We force it to return false so the ad never shows.
 */
object DraperImageDriverShowAdFingerprint : Fingerprint(
    definingClass = "Lcom/ea/game/bejeweled3/DraperImageDriver;",
    name = "ShowAd",
    isStatic = false,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("DraperImageDriver Showing ad."),
        methodCall(definingClass = "Landroid/app/Activity;", name = "startActivity")
    )
)

/**
 * Telemetry fingerprints.
 *
 * The game's analytics stack is Nimble/Synergy. Every user-behavior event (session
 * start/end, level-up, MTX, push notifications, …) is fanned out by TrackingWrangler
 * to the single tracking implementation, NimbleTrackingSynergyImpl, whose sends are
 * gated by NimbleTrackingImplBase.isAbleToPostEvent(). Neutralising that gate stops all
 * telemetry from leaving the device; the game keeps running and events still queue
 * locally — they simply never POST.
 */

/**
 * NimbleTrackingImplBase.isAbleToPostEvent(boolean) -> boolean
 *
 * The definitive send gate: `if (!m_enable || m_isFeatureDisabledFromServer || !m_postEnable)
 * return false`. We force it to always return false so no tracking event is ever POSTed.
 */
object IsAbleToPostEventFingerprint : Fingerprint(
    definingClass = "Lcom/ea/nimble/tracking/NimbleTrackingImplBase;",
    name = "isAbleToPostEvent",
    isStatic = false,
    returnType = "Z",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(definingClass = "Lcom/ea/nimble/ApplicationEnvironment;", name = "isMainApplicationActive"),
        methodCall(definingClass = "Lcom/ea/nimble/ISynergyEnvironment;", name = "isDataAvailable")
    )
)

/**
 * OperationalTelemetryDispatchImpl.logEvent(String eventType, String eventDict) -> void
 *
 * Internal operational-telemetry logger (network metrics + Synergy payload mirror).
 * Neutralising it stops that secondary logging stream too.
 */
object OperationalTelemetryLogEventFingerprint : Fingerprint(
    definingClass = "Lcom/ea/nimble/OperationalTelemetryDispatchImpl;",
    name = "logEvent",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        string("com.ea.nimble.network")
    )
)
