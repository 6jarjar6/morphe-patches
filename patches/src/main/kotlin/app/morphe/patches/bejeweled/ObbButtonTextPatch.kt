package app.morphe.patches.bejeweled

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants

/**
 * Cross-promo button-text patch for Bejeweled Classic.
 *
 * The "GET STARS!" button label lives in the Nimble OBB, not in any DEX:
 *   assets/main.com.ea.gp.bej3.obb  (ZIP, stored)
 *     -> iphone/main.rsb           (Nimble RSB, magic "1bsr")
 *        -> <String id="13194">GET STARS!</String>   (plain text in the XML blob)
 *
 * It is absent from every classesN.dex, res/, and native .so, and renders
 * offline / cache-cleared / first-load (the OBB is bundled in base.apk,
 * not a Play download), so it is a Nimble resource string, not server-pushed
 * and not a DEX string.
 *
 * Patch: SAME-LENGTH byte replacement inside the OBB.
 *   "GET STARS!" (10 bytes) -> "  GitHub  " (10 bytes)
 * Keeping the byte count identical preserves the RSB header, offset table,
 * and the OBB file size byte-for-byte, so nothing downstream desyncs.
 *
 * The OBB must stay uncompressed (it is stored for mmap); rawResourcePatch
 * keeps the entry in doNotCompress.
 */
val bejeweledObbButtonText = rawResourcePatch(
    name = "[Experimental] Cross-promo-button-text",
    description = "Replaces the in-game \"GET STARS!\" cross-promo button label (a Nimble OBB string) with \"  GitHub  \" (same length, no size change).",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        val obb = get("assets/main.com.ea.gp.bej3.obb")

        val original = "GET STARS!".toByteArray()          // 10 bytes
        val replacement = "  GitHub  ".toByteArray()       // 10 bytes
        check(original.size == replacement.size) {
            "Replacement must be same length as original: ${original.size} vs ${replacement.size}"
        }

        val data = obb.readBytes()
        val count = replaceInPlace(data, original, replacement)
        if (count > 0) {
            obb.writeBytes(data)
        }
    }
}

/** Same-length byte replacement (no shift, size preserved). Returns match count. */
private fun replaceInPlace(src: ByteArray, find: ByteArray, repl: ByteArray): Int {
    check(find.size == repl.size) { "find/repl must be same length" }
    var idx = 0
    var count = 0
    while (idx <= src.size - find.size) {
        var match = true
        for (i in find.indices) {
            if (src[idx + i] != find[i]) { match = false; break }
        }
        if (match) {
            for (i in repl.indices) src[idx + i] = repl[i]
            count++
            idx += find.size
        } else {
            idx++
        }
    }
    return count
}
