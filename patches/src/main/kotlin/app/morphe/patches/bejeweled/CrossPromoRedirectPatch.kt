package app.morphe.patches.bejeweled

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.function.Supplier

/**
 * Cross-promo redirect patch for Bejeweled Classic.
 *
 * The game's "GET STARS!" cross-promotion button advertises a sibling EA game
 * (Bejeweled Stars). The native cross-promo (GluMarketing, libBejeweed3.so)
 * decides at runtime — from server config — which target to open (app-scheme
 * when the promoted app is installed, Play-Store when it isn't, dynamic-link
 * otherwise), and resolves that target through the Java glue layer via
 * `AndroidPlatform.openURL(String)` (the single ACTION_VIEW intent builder,
 * called exclusively from native) and `AndroidPlatform.showWebView(J,
 * Activity, String, String)` (the in-app webview, the dynamic-link variant).
 *
 * This patch injects a rewrite at the top of both seams: the URL is fed through
 * the `CrossPromoSilo.rewrite` helper (an extension class, bundled here as a
 * base64-encoded dex). If the URL is a cross-promo target (the Bejeweled
 * family / Play-Store / GLU deep links) it is swapped for the GitHub repo;
 * every other URL (consent, CMP, age-gate, real ads) passes through untouched.
 *
 * Net effect: pressing the "GET STARS!" button opens
 * https://github.com/6jarjar6/morphe-patches instead of promoting EA's game.
 */
@Suppress("SpellCheckingInspection")
private const val CROSSPROMO_HELPER_DEX_BASE64 = """
ZGV4CjAzNQDRwqGGpKbjsiqYpn+vdMRjqnrZ+8cTD22ABAAAcAAAAHhWNBIAAAAAAAAAAMgDAAAVAAAAcAAAAAYAAADEAAAABAAAANwAAAABAAAADAEAAAUAAAAUAQAAAQAAADwBAAAkAwAAXAEAAPoBAAACAgAAFwIAAB8CAAAiAgAAJgIAAE4CAABoAgAAfAIAAJACAACTAgAAlgIAAJoCAACkAgAArgIAALcCAADjAgAA7gIAAPsCAAAEAwAAEQMAAAUAAAAGAAAABwAAAAgAAAAJAAAACgAAAAMAAAADAAAAAAAAAAQAAAADAAAA7AEAAAkAAAAEAAAAAAAAAAsAAAAFAAAA9AEAAAAAAwACAAAAAAACAAAAAAAAAAEAEgAAAAIAAgAAAAAAAwADAA0AAAADAAAAEwAAAAAAAAARAAAAAgAAAAAAAAABAAAAAAAAALADAADAAwAAAwABAAIAAADcAQAALAAAADkCAwARAm4QBAACAAwAGgEMAG4gAwAQAAoBOQEcABoBEABuIAMAEAAKATkBFAAaAREAbiADABAACgE5AQwAGgEOAG4gAwAQAAoAOAADACgCEQIaAg8AEQIBAAEAAQAAAOgBAAAEAAAAcBACAAAADgAQAQAOPEv/ARERGwANAA4AAQAAAAMAAAABAAAAAQAGPGluaXQ+ABNDcm9zc1Byb21vU2lsby5qYXZhAAZHSVRIVUIAAUwAAkxMACZMYXBwL21vcnBoZS9jcm9zc3Byb21vL0Nyb3NzUHJvbW9TaWxvOwAYTGphdmEvbGFuZy9DaGFyU2VxdWVuY2U7ABJMamF2YS9sYW5nL09iamVjdDsAEkxqYXZhL2xhbmcvU3RyaW5nOwABVgABWgACWkwACGJlamV3ZWVkAAhjb250YWlucwAHZ2x1bGl2ZQAqaHR0cHM6Ly9naXRodWIuY29tLzZqYXJqYXI2L21vcnBoZS1wYXRjaGVzAAltYXJrZXQ6Ly8AC3BsYXkuZ29vZ2xlAAdyZXdyaXRlAAt0b0xvd2VyQ2FzZQCcAX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwibWluLWFwaSI6MSwic2hhLTEiOiJhN2FkMThhNzA0NjBiNzk5ZDA0ODJlNDk3YzEwOWE3NWJmN2Y5MWRlIiwidmVyc2lvbiI6IjguMTAuOS1kZXYifQABAAIAABoAgoAExAMBCdwCARcPAAAAAAAPAAAAAAAAAAEAAAAAAAAAAQAAABUAAABwAAAAAgAAAAYAAADEAAAAAwAAAAQAAADcAAAABAAAAAEAAAAMAQAABQAAAAUAAAAUAQAABgAAAAEAAAA8AQAAASAAAAIAAABcAQAAAyAAAAIAAADcAQAAARAAAAIAAADsAQAAAiAAABUAAAD6AQAAACAAAAEAAACwAwAABSAAAAEAAADAAwAAAxAAAAEAAADEAwAAABAAAAEAAADIAwAA
"""

private val CrossPromoSiloDexSupplier: Supplier<InputStream> =
    Supplier { ByteArrayInputStream(Base64.getDecoder().decode(CROSSPROMO_HELPER_DEX_BASE64.trim())) }

@Suppress("SpellCheckingInspection")
val bejeweledCrossPromoRedirect = bytecodePatch(
    name = "[Experimental] Cross-promo-redirect",
    description = "Redirects the GET STARS! cross-promo button to the GitHub repo (instead of promoting EA’s game).",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)
    extendWith(CrossPromoSiloDexSupplier)

    execute {
        // --------------------------------------------------------------
        // 1. openURL(String) — the definitive ACTION_VIEW intent builder.
        //    The URL parameter is the last register (p1); register layout
        //    is 3 regs / 2 params. Rewrite the register before the native
        //    target is handed to the intent.
        // --------------------------------------------------------------
        OpenUrlFingerprint.method.addInstruction(
            0,
            "invoke-static {p1}, Lapp/morphe/crosspromo/CrossPromoSilo;->rewrite(Ljava/lang/String;)Ljava/lang/String;"
        )
        OpenUrlFingerprint.method.addInstruction(1, "move-result-object p1")

        // --------------------------------------------------------------
        // 2. showWebView(J, Activity, String url, String config) — the
        //    in-app webview (dynamic-link variant). The URL parameter is
        //    p4 (layout 13 regs / 6 params); config is p5. Rewrite p4.
        // --------------------------------------------------------------
        ShowWebViewFingerprint.method.addInstruction(
            0,
            "invoke-static {p4}, Lapp/morphe/crosspromo/CrossPromoSilo;->rewrite(Ljava/lang/String;)Ljava/lang/String;"
        )
        ShowWebViewFingerprint.method.addInstruction(1, "move-result-object p4")

        // --------------------------------------------------------------
        // 3. com.ea.rs.xpromo.Item.launch:()Z — the nimble-Java cross-promo
        //    panel (the actual "GET STARS!" banner). It builds its own
        //    ACTION_VIEW intent and startActivity()'s DIRECTLY, so it never
        //    reaches openURL/showWebView. Two branches, both drawing their
        //    URL from m_appLinks:
        //      launch -> m_appLinks.get("appLaunch") -> getInstalledAppPackageName
        //      store  -> m_appLinks.get("store")    -> Uri.parse -> startActivity
        //    Rewrite both URLs at the point each is fetched (v1 / v4).
        //    Inserted from the largest index down so lower indices are stable.
        // --------------------------------------------------------------
        val launch = XpromoItemLaunchFingerprint.method
        // IMPORTANT: the substitution must sit AFTER the original check-cast, so it
        // writes to a verified String register (Map.get yields Object; the store
        // branch's Uri.parse likewise wants a String). Identity offset is confirmed
        // (request N -> lands at N).
        // CRITICAL: unlike the openURL/showWebView seams (shared paths — consent,
        // real ads — hence the substring-filtered rewrite()), Item.launch() is a
        // DEDICATED cross-promo method: its only callers are the Get-Stars views
        // and its m_appLinks urls are server-chosen, so a filter can miss them
        // (e.g. a store URL without bejeweed/market:///play.google/glulive slips
        // through). Overwrite the url register UNCONDITIONALLY with the GitHub URL.
        // launch branch: orig idx 6 = check-cast v1 (appLaunch url).
        // Insert at idx 7: overwrite v1. getInstalledAppPackageName(https url)
        // returns null -> control naturally falls into the store branch below.
        launch.addInstruction(7, "const-string v1, \"https://github.com/6jarjar6/morphe-patches\"")
        // store branch: orig idx 32 = check-cast v4, idx 33 = Uri.parse.
        // The launch insertion (+1 shift) puts orig idx 32 (check-cast v4) at idx 33.
        // Insert at idx 34: overwrite v4 (before Uri.parse at idx 34).
        launch.addInstruction(34, "const-string v4, \"https://github.com/6jarjar6/morphe-patches\"")
    }
}
