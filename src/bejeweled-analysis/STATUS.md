# Bejeweled ad-free patch — FINAL STATE (updated 2026-10-26)

## Verdict: DONE — all three patches build, apply, and transform correctly

```
RESULT: OK   Ad-free
RESULT: OK   Telemetry-disabled
```

### CRITICAL BUG FOUND + FIXED (runtime crash)
The shipped `Ad-free` patch crashed the app a few seconds after launch with a
`VerifyError` in `csdk.gluads.impl.AdvertisingImpl.show(String,String,Map)`.

**Root cause:** the injected `PlacementEvent` constructor call was a NON-RANGE
`invoke-direct { p1, p2, v$status, v$nullReg, p3 }` — 5 registers — but the
constructor requires `this` **+ 5 args = 6** registers, which is IMPOSSIBLE for a
non-range invoke (max 5). The `new-instance` (the `this` object) was never
placed in the register list, so the verifier bound `this` to the `String p1`
(and the `Throwable` arg to the INT `const/4`), producing:
`VerifyError [0xD] Expected initialization on uninitialized reference: java.lang.String`.

**Fix:** assemble `this` + 5 args into six CONTIGUOUS free registers and call
with `invoke-direct/range { ev .. r5 }` (=`v1..v6`). Verified in the patched dex:
both synthetic events (showStarted / showFinished) now emit
`INVOKE_DIRECT_RANGE {1..6}` — the correct, verifier-valid form.

#### CRITICAL BUG #2 FOUND + FIXED (v1.0.1 regression → v1.0.2)
Field log after installing v1.0.1 (`logcat`) showed a *new* verifier rejection:
`VerifyError [0x10] register v5 has type String but expected Throwable`.
The first bug was fixed, but my v1.0.1 register allocation **swapped the constant
slots**.

**Root cause:** `getFreeRegisterProvider(0,N).getFreeRegister()` does **not**
hand out registers in numeric order — the observed order was `v0,v1,v2,v3,v5,v4,v6`
(v5 before v4). I indexed the handout `[1]=[v2]`…so `const-string` (meant for ctor
arg 3) landed in **v5** and `const/4` (arg 4, the Throwable) landed in **v4**. In
the `invoke-direct/range {v1..v6}` the positional binding is `v1=this, v2=arg1,
v3=arg2, v4=arg3(String), v5=arg4(Throwable), v6=arg5`. So v5 (String) collided
with the Throwable slot.

**Fix:** ignore the provider's handout order. Pick the LOWEST contiguous 6-wide
free block, then take the *remaining* register as the listener (guaranteed outside
the block), and bind the constants **by position** (arg3 = `lo+3`, arg4 = `lo+4`).
Constraint noted: the method has exactly **7** free locals (v0–v6; p0–p3 sit in
v7–v10), so the provider must be sized `(0,7)` — requesting 8+ raises
`No free registers available`.

Verified: both events now emit `INVOKE_DIRECT_RANGE {0..5}` with `const-string` at
slot 3 and `const/4` at slot 4; `VerifyHarnessKt` → `RESULT: OK` for all three
patches.
```

All three sub-patches match their fingerprints on the real v4.0.0 base.apk and the
injected bytecode assembles. Verified by dumping the patched dexes with dexlib2
(see verification recipe below) — every method was confirmed to transform as intended.

## Patches (all verified in patched dexes)

1. **csdk.gluads.impl.AdvertisingImpl.show(String,String,Map)** — body replaced with a
   synthetic completion that advances the native side as if the ad played:
   `onPlacementEvent(showStarted)` → if `adType=="rewardedInterstitial"` grant the reward
   (`getReward(type,placement)` → `onRewardReceived(reward)`) → `onPlacementEvent(showFinished)`
   → `GOTO` past the original dispatcher. Reward path is conditional and null-safe.
2. **com.popcap.pcsp.marketing.GoogleImaDriver.ShowAd()Z** — `returnEarly(false)` → `const/4 v0,0; return` (false).
3. **com.ea.game.bejeweled3.DraperImageDriver.ShowAd()Z** — `returnEarly(false)` → `const/4 v0,0; return` (false).
4. **Telemetry-disabled** (`TelemetryPatch.kt`) — stops Nimble/Synergy telemetry from POSTing:
   - `NimbleTrackingImplBase.isAbleToPostEvent(Z)Z` → `returnEarly(false)` → `const/4 v0,0; return`.
     This is the definitive gate for ALL Synergy user-behavior events (session/level-up/MTX/push).
     The game keeps running; events still queue locally, they just never leave the device.
   - `OperationalTelemetryDispatchImpl.logEvent(Ljava/lang/String;Ljava/lang/String;)V` → `returnEarly()` → `return-void`.
     Stops the secondary internal operational-telemetry stream.
   Chosen over neutralising `SynergyNetwork.sendRequest` (which the in-store catalog, purchase
   verification and age-gate also use) — this patch is tracking-only and doesn't touch the store.

## Bugs & lessons fixed this work

1. **Draper fingerprint never matched.** The `startActivity` call's defining class is
   `Landroid/app/Activity;`, NOT `Landroid/content/Context;`. The `mActivity` field is
   typed `Activity`, so the compiler resolves `startActivity(Intent)` to
   `android/app/Activity.startActivity(...)` (not `Context`). Dump the method with
   dexlib2 `getReference()` on each `INVOKE*` instruction to see the true defining class.
   Fix: `methodCall(definingClass = "Landroid/app/Activity;", name = "startActivity")`
   in `Fingerprints.kt`.
2. **kotlinx.coroutines NoSuchMethodError at dex-write time.** The "all jars in module
   cache" classpath pulled in coroutines 1.8.0 + 1.9.0 + 1.10.2; the loader picked a
   version lacking `CoroutineDispatcher.limitedParallelism$default(...)` that morphe-
   patcher 2.0.0 needs. morphe-patcher 2.0.0 depends on coroutines **1.10.2** only.
   Fix: build the runtime classpath excluding the older versions:
   `find ... -name '*.jar' | grep -vE 'kotlinx-coroutines-core-jvm/(1\.8\.0|1\.9\.0)'`.
3. **Telemetry fingerprint didn't match — interface-typed `methodCall` definingClass.**
   The `isDataAvailable()` call sits after
   `SynergyEnvironment.getComponent().isDataAvailable()`. `getComponent()` returns the
   interface `ISynergyEnvironment`, so the `invoke-interface` reference resolves to
   `Lcom/ea/nimble/ISynergyEnvironment;->isDataAvailable()Z` — NOT
   `Lcom/ea/nimble/SynergyEnvironment;` (the concrete class). The fingerprint's
   `methodCall(definingClass="Lcom/ea/nimble/SynergyEnvironment;", name="isDataAvailable")`
   failed to match. Same family as the Draper lesson: the `definingClass` in a filter is
   the type at the call site, which is whatever the receiver expression is typed as
   (interface here, `Activity` for the Draper field). Always confirm with a dexlib2 dump
   of the method's `INVOKE*` instructions (`DumpTelemetry.java`), not the Java source class name.

## Verification recipe (proves the .mpp actually works)

```bash
cd ~/morphe/template && ./gradlew buildAndroid          # -> patches/build/libs/patches-1.0.0.mpp
cd src/bejeweled-analysis
CP=$(cat /tmp/patch_cp.txt)                              # classpath w/o old coroutines
java -cp "out/verify-harness:$CP" VerifyHarnessKt        # -> RESULT: OK (Ad-free + Telemetry-disabled)
# Then confirm the bytecode landed:
cp patched.apk/patched/dex/*.dex verify_telem/           # patched dexes
# dump target methods with DumpShowAd.java / DumpTelemetry.java (dexlib2) and check:
#   Draper/GoogleIma ShowAd -> first two instrs = CONST_4 val=0, RETURN
#   AdvertisingImpl.show    -> showStarted .. reward-gate .. showFinished .. GOTO
#   isAbleToPostEvent       -> first two instrs = CONST_4 val=0, RETURN  (always false)
#   logEvent                -> first instr   = RETURN_VOID                  (early return)
```

`/tmp/patch_cp.txt` is regenerated each session (TMPDIR prunes idle files):
```bash
{ echo patches/build/classes/kotlin/main; echo patches/build/resources/main;
  find ~/.gradle/caches/modules-2/files-2.1/ -name '*.jar' \
    | grep -vE 'kotlinx-coroutines-core-jvm/(1\.8\.0|1\.9\.0)'; } \
  | tr '\n' ':' | sed 's/:$//' > /tmp/patch_cp.txt
```

## Key dexlib2 API facts (morphe fork — package differs from upstream)
- Package is `com.android.tools.smali.dexlib2.*` (NOT `com.android.tools.dexlib2.*`).
- All classes live under `dexbacked/` (no separate `iface.dexbacked`); `DexBackedClassDef`,
  `DexBackedMethod`, `DexBackedInstruction35c` are all in `dexbacked`.
- Load a dex: `DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(FileInputStream(f)))`
  — a raw FileInputStream fails ("InputStream must support mark").
- `DexBackedInstruction35c.getReference()` → the method `Reference` (has `getDefiningClass()`).
- `DexBackedInstruction11n`: `getRegisterA()`, `getNarrowLiteral()` (NOT `getValue()`).
- `DexBackedInstruction21c`: `getString()`.

## Artifacts (under `src/bejeweled-analysis/`)
- `VerifyHarness.kt` — end-to-end apply (BytecodeMode.FULL); loads ALL patches in the .mpp
- `DumpShowAd.java` — dumps arbitrary target methods' bytecode (configurable target list)
- `DumpTelemetry.java` — dumps `isAbleToPostEvent` / `logEvent` + all their `INVOKE*` refs
- `Fingerprints.kt` — 5 fingerprints (ad: AdvertisingImpl.show, GoogleIma/Draper ShowAd; telemetry: isAbleToPostEvent, OperationalTelemetry.logEvent)
- `BejeweledPatch.kt` — the ad-free patch (3 sub-patches)
- `TelemetryPatch.kt` — the telemetry-disabled patch (2 sub-patches)
- `patched.apk/patched/dex/*.dex` — last successful patched output (10 dexes)
