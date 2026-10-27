package app.morphe.patches.bejeweled

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.anyInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.ClassDef
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

/* ========================================================================================
 * Broad telemetry kill — the SDKs the Nimble/Synergy gate does NOT cover.
 *
 * The game ships a far wider analytics/ad stack than Nimble/Synergy. The residual
 * channels observed in the user's DNS log (Firebase, Facebook Audience Network,
 * Unity Ads, GLUAnalytics) each have their own network layer. We neutralise each
 * at its cheapest, safest choke point:
 *
 *  - CctTransportBackend.send          -> throw (the single Firebase DataTransport
 *                                       egress for Analytics + Crashlytics + ML + auth)
 *  - GluAnalytics Analytics.logEvent   -> return (central EA analytics hub recorder)
 *  - GluAnalytics Analytics.doLogEvent -> return (hub dispatch, 2 overloads)
 *  - GluAnalytics Analytics.init       -> return (prevent the hub from starting)
 *  - AudienceNetworkAds.initialize     -> return (Facebook ads SDK never boots)
 *  - UnityAds.initialize (4 overloads) -> return (Unity Ads SDK never boots)
 *
 * Each match is discriminated by the exact signature (class + name + parameters
 * + return type) anchored to the guaranteed final return instruction — no reliance
 * on body introspection.
 */

/**
 * CctTransportBackend.send(BackendRequest) -> BackendResponse
 *
 * The one method every Firebase DataTransport destination (Analytics, Crashlytics,
 * ML Kit, auth) flows through to reach the wire. We replace it with a thrown
 * IOException, so every upload is marked "deferred/failed" and never leaves the
 * device. The transport's retry machinery handles the exception gracefully.
 */
object CctTransportSendFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/datatransport/cct/CctTransportBackend;",
    name = "send",
    isStatic = false,
    returnType = "Lcom/google/android/datatransport/runtime/backends/BackendResponse;",
    parameters = listOf("Lcom/google/android/datatransport/runtime/backends/BackendRequest;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_OBJECT))),
)

/**
 * GLUAnalytics hub — Analytics.logEvent (7-arg: String, String, String, String,
 * Long, Long, Map) -> void
 *
 * The central EA analytics hub records events here. Early-returning stops the
 * hub from accumulating/dispatching behaviour events downstream.
 */
object GluAnalyticsLogEventFingerprint : Fingerprint(
    definingClass = "Lcom/glu/plugins/gluanalytics/Analytics;",
    name = "logEvent",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Long;", "Ljava/lang/Long;", "Ljava/util/Map;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * GLUAnalytics hub — Analytics.doLogEvent (Long-overload) -> void.
 * Dispatch side of the hub.
 */
object GluAnalyticsDoLogEventLongFingerprint : Fingerprint(
    definingClass = "Lcom/glu/plugins/gluanalytics/Analytics;",
    name = "doLogEvent",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Long;", "Ljava/lang/Long;", "Ljava/util/Map;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * GLUAnalytics hub — Analytics.doLogEvent (BigDecimal-overload) -> void.
 */
object GluAnalyticsDoLogEventBigDecimalFingerprint : Fingerprint(
    definingClass = "Lcom/glu/plugins/gluanalytics/Analytics;",
    name = "doLogEvent",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/math/BigDecimal;", "Ljava/lang/Long;", "Ljava/util/Map;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * GLUAnalytics hub — Analytics.init(IAnalyticsCallback) -> void.
 * Preventing the hub from initializing cuts the pipeline at the root.
 */
object GluAnalyticsInitFingerprint : Fingerprint(
    definingClass = "Lcom/glu/plugins/gluanalytics/Analytics;",
    name = "init",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Lcom/glu/plugins/gluanalytics/IAnalyticsCallback;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * AudienceNetworkAds.initialize(Context) -> void  (static)
 *
 * Booting the Facebook Audience Network SDK. Early-returning means the SDK
 * never initializes, so it never polls facebook/instagram CDN.
 */
object AudienceNetworkAdsInitFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/AudienceNetworkAds;",
    name = "initialize",
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * UnityAds.initialize(Context, String) -> void  (static)
 * Primary overload.
 */
object UnityAdsInit2Fingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/ads/UnityAds;",
    name = "initialize",
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * UnityAds.initialize(Context, String, IUnityAdsInitializationListener) -> void  (static)
 */
object UnityAdsInitListenerFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/ads/UnityAds;",
    name = "initialize",
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Lcom/unity3d/ads/IUnityAdsInitializationListener;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * UnityAds.initialize(Context, String, boolean) -> void  (static)
 */
object UnityAdsInitDebugFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/ads/UnityAds;",
    name = "initialize",
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Z"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * UnityAds.initialize(Context, String, boolean, IUnityAdsInitializationListener) -> void  (static)
 */
object UnityAdsInitDebugListenerFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/ads/UnityAds;",
    name = "initialize",
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Z", "Lcom/unity3d/ads/IUnityAdsInitializationListener;"),
    filters = listOf(anyInstruction(opcode(Opcode.RETURN_VOID))),
)

/**
 * The set of UnityAds.initialize overloads, for bulk neutralisation.
 */
val unityAdsInitOverloads: List<Fingerprint> = listOf(
    UnityAdsInit2Fingerprint,
    UnityAdsInitListenerFingerprint,
    UnityAdsInitDebugFingerprint,
    UnityAdsInitDebugListenerFingerprint
)

/**
 * The set of GLUAnalytics hub dispatch overloads (doLogEvent).
 */
val gluAnalyticsDoLogOverloads: List<Fingerprint> = listOf(
    GluAnalyticsDoLogEventLongFingerprint,
    GluAnalyticsDoLogEventBigDecimalFingerprint
)

/**
 * Crashing-funnel fingerprint.
 *
 * com.glu.plugins.gluanalytics.GluAnalytics is the wrapper (extends Handler) that
 * pumps analytics lifecycle/session/user events off the worker thread. Several of its
 * `handleMessage` branches dereference `this.mCallback` (an IAnalyticsCallback) to
 * forward onInit / onUpdateSessionId / onUserIdChanged / onDestroy.
 *
 * The callback is set only by the legacy `GluAnalytics.init(IAnalyticsCallback)`,
 * which is NEVER called — the game's factory wires the modern `enablePinpoint`
 * path instead. So `mCallback` is structurally always null, and any of these
 * branches NPEs as soon as its message arrives. This is a latent vendor bug,
 * exposed once the earlier startup crash (ad funnel) was fixed and the app began
 * surviving long enough to pump these messages.
 */
/**
 * GluAnalytics.handleMessage(Message) -> void
 *
 * Central dispatcher. We null-guard every `mCallback` dereference here so a
 * message that would NPE is dropped harmlessly instead of crashing the process.
 */
object GluAnalyticsHandleMessageFingerprint : Fingerprint(
    definingClass = "Lcom/glu/plugins/gluanalytics/GluAnalytics;",
    name = "handleMessage",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Landroid/os/Message;"),
    filters = listOf(
        string("GLUANALYTICS.MESSAGE_START_SESSION")
    )
)
