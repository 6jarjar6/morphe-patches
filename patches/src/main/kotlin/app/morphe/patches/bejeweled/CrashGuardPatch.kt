package app.morphe.patches.bejeweled

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.Constants

/**
 * Crash-guard patch for Bejeweled Classic.
 *
 * Latent vendor bug, exposed now that the app survives past the former startup
 * crash: [app.morphe.patches.bejeweled.GluAnalyticsHandleMessageFingerprint.method]
 * (`com.glu.plugins.gluanalytics.GluAnalytics.handleMessage`) dereferences
 * `this.mCallback` (an IAnalyticsCallback) in several branches to forward
 * onInit / onUpdateSessionId / onUserIdChanged / onDestroy.
 *
 * `mCallback` is set only by the legacy `GluAnalytics.init(IAnalyticsCallback)`,
 * which the game never calls — its factory wires the modern `enablePinpoint`
 * path instead. So `mCallback` is structurally always null, and every one of
 * these branches NPEs when its message arrives (observed in the wild on the
 * `glu-analytics` worker thread when `destroy()` posts MESSAGE_USERID_CHANGED).
 *
 * Fix: null-guard each `mCallback` dereference. When the callback is null we
 * drop only the notification (state writes like `mut_userId = ...` still
 * happen); a non-null callback is forwarded exactly as before.
 */
@Suppress("SpellCheckingInspection")
val bejeweledCrashGuard = bytecodePatch(
    name = "Crash-guard",
    description = "Null-guards the GLUAnalytics handler callback (fixes a latent NPE).",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        val method = GluAnalyticsHandleMessageFingerprint.method
        val impl = method.getImplementation()!!
        val snap = impl.getInstructions()
        val n = snap.size

        // Structural helpers (dexlib2-builder instructions expose references via
        // getReference(); opcodes via getOpcode()).
        fun opcodeAt(i: Int): String =
            (snap[i].javaClass.methods.first { it.name == "getOpcode" }.invoke(snap[i])
                as com.android.tools.smali.dexlib2.Opcode).name

        fun refAt(i: Int): Any? =
            snap[i].javaClass.methods.first { it.name == "getReference" }.invoke(snap[i])

        fun fieldNameAt(i: Int): Any? =
            refAt(i)?.javaClass?.methods?.first { it.name == "getName" }?.let { it.invoke(refAt(i)!!) }

        fun refClassAt(i: Int): Any? =
            refAt(i)?.javaClass?.methods?.first { it.name == "getDefiningClass" }
                ?.let { it.invoke(refAt(i)!!) }

        fun isMCallbackLoad(i: Int): Boolean =
            opcodeAt(i) == "iget-object" &&
                    fieldNameAt(i) == "mCallback" &&
                    refClassAt(i) == "Lcom/glu/plugins/gluanalytics/GluAnalytics;"

        data class Site(val loadIdx: Int, val reg: String, val callIdx: Int)
        val sites = mutableListOf<Site>()

        for (i in 0 until n) {
            if (!isMCallbackLoad(i)) continue
            val regVal = snap[i].javaClass.methods.first { it.name == "getRegisterA" }.invoke(snap[i])
            val reg = "v${regVal as Int}"
            // Bound the search window at the NEXT mCallback load (exclusive): a
            // callback call farther than that belongs to the next site.
            val bound = (i + 1 until n).firstOrNull { isMCallbackLoad(it) } ?: n
            val callIdx = (i + 1 until bound).firstOrNull {
                val op = opcodeAt(it)
                op.startsWith("invoke-interface") &&
                        refClassAt(it) == "Lcom/glu/plugins/gluanalytics/IAnalyticsCallback;"
            }
            if (callIdx != null) sites += Site(i, reg, callIdx)
        }

        check(sites.isNotEmpty()) {
            "Expected mCallback callback sites in handleMessage, found none"
        }

        // Insert guards bottom-up so higher insertions never shift lower-site
        // indices. The label target is fetched from the LIVE list right before
        // each insertion (its identity is stable).
        val orderedSites = sites.sortedByDescending { it.loadIdx }
        var t = 0
        for (s in orderedSites) {
            val live = impl.getInstructions()
            val target = live[s.callIdx + 1]
            val label = "skipCbNPE$t"
            method.addInstructionsWithLabels(
                s.loadIdx + 1,
                "if-eqz ${s.reg}, :$label",
                ExternalLabel(label, target)
            )
            t++
        }
    }
}
