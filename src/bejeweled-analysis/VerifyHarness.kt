import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    val baseDir = File("/home/andy/morphe/template/src/bejeweled-analysis")
    val apkFile = baseDir.resolve("base.apk")
    val patchedFile = baseDir.resolve("patched.apk")
    val workspace = baseDir.resolve("workspace")
    workspace.mkdirs()

    val mpp = File("/home/andy/morphe/template/patches/build/libs/")
        .listFiles()!!
        .first { it.name.endsWith(".mpp") }
    println("=== Applying patch: ${mpp.name}")
    println("=== Target APK: ${apkFile.name}")

    val patches = loadPatchesFromJar(setOf(mpp))
    println("=== Loaded ${patches.size} patch(es)")

    val config = PatcherConfig(
        apkFile,
        patchedFile,
        setOf(CpuArchitecture.ARM64_V8A, CpuArchitecture.ARMEABI_V7A),
        BytecodeMode.FULL,
        NoOpDexVerifier,
        workspace,
        "2.0.0",
        false
    )

    val patcherResult = Patcher(config).use { patcher ->
        patcher += patches
        runBlocking {
            patcher().toList().forEach { r ->
                val ex = r.exception
                if (ex == null) {
                    println("RESULT: OK   ${r.patch.name}")
                } else {
                    println("RESULT: FAIL ${r.patch.name}: ${ex.message}")
                    ex.printStackTrace(System.out)
                }
            }
        }
        patcher.get()
    }

    println("=== PatcherResult: ${patcherResult.javaClass.simpleName}")
    if (patchedFile.exists()) {
        println("=== Patched output: ${patchedFile.length()} bytes")
    } else {
        println("=== NOTE: no patchedFile written (expected if get() returns components only)")
    }
}
