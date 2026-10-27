package app.morphe.patches.bejeweled

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.Constants

/**
 * Telemetry-disabled patch for Bejeweled Classic.
 *
 * The game's analytics stack is Nimble/Synergy. Every user-behavior event (session
 * start/end, level-up, MTX, push notifications, …) is fanned out by TrackingWrangler to
 * the single tracking implementation, NimbleTrackingSynergyImpl, whose network sends are
 * gated by NimbleTrackingImplBase.isAbleToPostEvent().
 *
 * We neutralise that gate so no telemetry ever POSTs to the server:
 *
 *  1. [IsAbleToPostEventFingerprint] — force `isAbleToPostEvent` to always return false.
 *     This is the definitive gate for all Synergy user-behavior telemetry; the game keeps
 *     running and events still queue locally, they just never leave the device.
 *  2. [OperationalTelemetryLogEventFingerprint] — force the internal operational-telemetry
 *     logger `logEvent` to return early, stopping that secondary stream too.
 *
 * Unlike neutralising SynergyNetwork.sendRequest (which the in-store catalog, purchase
 * verification and age-gate also use), this patch is tracking-only and does not affect
 * gameplay or the store.
 */
@Suppress("SpellCheckingInspection")
val bejeweledTelemetryDisabled = bytecodePatch(
    name = "Telemetry-disabled",
    description = "Stops Nimble/Synergy telemetry from being posted to the server.",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        // ------------------------------------------------------------------
        // 1. Definitive tracking gate — never POST a tracking event.
        // ------------------------------------------------------------------
        IsAbleToPostEventFingerprint.method.returnEarly(false)

        // ------------------------------------------------------------------
        // 2. Operational telemetry (internal metrics) — drop the event.
        // ------------------------------------------------------------------
        OperationalTelemetryLogEventFingerprint.method.returnEarly()
    }
}
