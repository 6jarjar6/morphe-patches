package app.morphe.patches.bejeweled

import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants

/**
 * Privacy-disabled patch for Bejeweled Classic.
 *
 * GluAnalytics (com.glu.plugins.gluanalytics) reads two privacy preferences from
 * SharedPreferences, both defaulting to TRUE (i.e. opt-out):
 *
 *  - "usageSharing"  -> mUsageSharing. When true, EVERY analytics event is sent to the
 *    server (GluAnalytics line 290: `if (mUsageSharing || critical) send()`).
 *  - "internalTargetedAds" -> mInternalTargetedAds. When true, the ad SDK is configured
 *    for personalized / targeted advertising.
 *
 * Both are read via `SharedPreferences.getBoolean("usageSharing"/"internalTargetedAds",
 * true)` in the constructor and in getCustomProperties(). We replace each of those reads
 * with `const/4 <result>, 0x0`, forcing both flags OFF regardless of what the device
 * preferences or the server config say. The game keeps running; only privacy-relevant
 * telemetry (usage sharing) and ad personalization are disabled.
 *
 * Only the two methods that read the privacy keys are patched (constructor +
 * getCustomProperties()). Other GluAnalytics methods also call SharedPreferences.getBoolean
 * for unrelated keys (RevID, app-start, custom-properties), so they are filtered out by name.
 */
@Suppress("SpellCheckingInspection")
val bejeweledPrivacyDisabled = bytecodePatch(
    name = "Privacy-disabled",
    description = "Forces the internal 'usage sharing' and 'targeted ads' privacy flags off.",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        val gb = methodCall("Landroid/content/SharedPreferences;", "getBoolean")

        // Collect (method, index) for every SharedPreferences.getBoolean read, then keep
        // only the two methods that read the privacy keys.
        val sites = mutableListOf<Pair<MutableMethod, Int>>()
        gb.matchAllMethodIndicesForEach(false) { idx ->
            if (this.name == "<init>" || this.name == "getCustomProperties") {
                sites.add(this to idx)
            }
        }

        if (sites.isEmpty()) {
            error("No SharedPreferences.getBoolean privacy reads found in GluAnalytics")
        }

        // Replace each `invoke getBoolean` + following `move-result vX` with
        // `const/4 vX, 0x0`, so the stored/reported value is false. Process in
        // DESCENDING index order (highest first) so that removing instructions at
        // later sites never shifts the captured indices of earlier sites.
        for ((method, idx) in sites.sortedByDescending { it.second }) {
            val resultReg = moveResultRegister(method, idx)
            method.replaceInstruction(idx, "const/4 v$resultReg, 0x0")
            method.removeInstruction(idx + 1)
        }
    }
}

/**
 * The register that receives a SharedPreferences.getBoolean result: the operand of the
 * `move-result` instruction immediately following the invoke.
 */
private fun moveResultRegister(method: MutableMethod, invokeIndex: Int): Int {
    val mr = method.getInstruction(invokeIndex + 1)
    for (md in (mr as Any).javaClass.declaredMethods) {
        if (md.name == "getRegisterA" && md.parameterCount == 0) {
            md.isAccessible = true
            return md.invoke(mr) as Int
        }
    }
    throw IllegalStateException("move-result at ${invokeIndex + 1} has no register: ${mr.javaClass}")
}
