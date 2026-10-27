package app.morphe.patches.bejeweled

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.Constants
import app.morphe.patcher.util.smali.ExternalLabel

/**
 * Ad-free patch for Bejeweled Classic.
 *
 * The game's logic is largely native (Nimble / libBejeweled3.so); the Java layer is
 * the glue that actually renders ads. This patch neutralises the Java ad drivers:
 *
 * 1. GluAds funnel ([AdvertisingImpl.show]) — the central dispatcher for every
 *     GluAds ad type (interstitial, rewarded, offer-wall, banner). Its body is replaced
 *     with a synthetic completion: emit showStarted, grant the configured reward for
 *     rewarded ads, then emit showFinished, so the native side advances as if the ad
 *     played.
 * 2. Google IMA ([GoogleImaDriver.ShowAd]) — forced to return false.
 * 3. Draper ([DraperImageDriver.ShowAd]) — forced to return false.
 */
@Suppress("SpellCheckingInspection")
val bejeweledAdFree = bytecodePatch(
    name = "Ad-free",
    description = "Removes ads. Rewarded ads still grant their reward without playing.",
    default = true
) {
    compatibleWith(Constants.BEJEWELED_COMPATIBILITY)

    execute {
        // ------------------------------------------------------------------
        // 1. GluAds funnel — replace AdvertisingImpl.show body with a synthetic
        //    completion (showStarted -> reward -> showFinished), then skip the
        //    original code.
        // ------------------------------------------------------------------
        val showMethod = AdvertisingImplShowFingerprint.method

        // We need 7 free registers:
        //   listener (held across the whole body), then the 6 contiguous
        //   registers (ev + r1..r5) needed to assemble the PlacementEvent
        //   constructor via invoke-direct/range (this + 5 args = 6 regs).
        showMethod.getFreeRegisterProvider(0, 7).let { rp ->
            val listener = rp.getFreeRegister()
            val ev = rp.getFreeRegister()
            val r1 = rp.getFreeRegister()
            val r2 = rp.getFreeRegister()
            val r3 = rp.getFreeRegister()
            val r4 = rp.getFreeRegister()
            val r5 = rp.getFreeRegister()

            showMethod.addInstructionsWithLabels(
                0,
                """
                    iget-object v$listener, p0, Lcsdk/gluads/impl/AdvertisingImpl;->listener:Ljava/util/concurrent/atomic/AtomicReference;
                    invoke-virtual { v$listener }, Ljava/util/concurrent/atomic/AtomicReference;->get()Ljava/lang/Object;
                    move-result-object v$listener
                    check-cast v$listener, Lcsdk/gluads/IAdvertisingListener;

                    new-instance v$ev, Lcsdk/gluads/PlacementEvent;
                    move-object v$r1, p1
                    move-object v$r2, p2
                    const-string v$r3, "showStarted"
                    const/4 v$r4, 0
                    move-object v$r5, p3
                    invoke-direct/range { v$ev .. v$r5 }, Lcsdk/gluads/PlacementEvent;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Ljava/util/Map;)V
                    invoke-interface { v$listener, v$ev }, Lcsdk/gluads/IAdvertisingListener;->onPlacementEvent(Lcsdk/gluads/PlacementEvent;)V

                    const-string v$r1, "rewardedInterstitial"
                    invoke-virtual { p1, v$r1 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v$r2
                    if-eqz v$r2, :skipReward
                    invoke-virtual { p0, p1, p2 }, Lcsdk/gluads/impl/AdvertisingImpl;->getReward(Ljava/lang/String;Ljava/lang/String;)Lcsdk/gluads/Reward;
                    move-result-object v$r3
                    if-eqz v$r3, :skipReward
                    invoke-interface { v$listener, v$r3 }, Lcsdk/gluads/IAdvertisingListener;->onRewardReceived(Lcsdk/gluads/Reward;)V
                    :skipReward

                    new-instance v$ev, Lcsdk/gluads/PlacementEvent;
                    move-object v$r1, p1
                    move-object v$r2, p2
                    const-string v$r3, "showFinished"
                    const/4 v$r4, 0
                    move-object v$r5, p3
                    invoke-direct/range { v$ev .. v$r5 }, Lcsdk/gluads/PlacementEvent;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Ljava/util/Map;)V
                    invoke-interface { v$listener, v$ev }, Lcsdk/gluads/IAdvertisingListener;->onPlacementEvent(Lcsdk/gluads/PlacementEvent;)V

                    goto :finish
                """,
                ExternalLabel(
                    "finish",
                    showMethod.getInstruction(showMethod.implementation!!.instructions.count() - 1)
                )
            )
        }

        // ------------------------------------------------------------------
        // 2. Google IMA — never show the interactive video ad.
        // ------------------------------------------------------------------
        GoogleImaDriverShowAdFingerprint.method.returnEarly(false)

        // ------------------------------------------------------------------
        // 3. Draper — never show the image ad.
        // ------------------------------------------------------------------
        DraperImageDriverShowAdFingerprint.method.returnEarly(false)
    }
}
