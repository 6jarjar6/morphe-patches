import app.morphe.patcher.Fingerprint
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.resource.CpuArchitecture
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import java.io.File

fun apsiSigCls(): Class<*> = Class.forName("com.reandroid.archive.block.ApkSignatureBlock")

fun main() {
    val baseDir = File("/home/andy/morphe/template/src/bejeweled-analysis")
    val apkFile = baseDir.resolve("base.apk")
    val patchedFile = baseDir.resolve("patched_dbg.apk")
    val workspace = baseDir.resolve("workspace_dbg")
    workspace.mkdirs()

    val config = PatcherConfig(
        apkFile,
        patchedFile,
        setOf(CpuArchitecture.ARM64_V8A, CpuArchitecture.ARMEABI_V7A),
        BytecodeMode.NONE,
        NoOpDexVerifier,
        workspace,
        "2.0.0",
        false
    )
    // Construct the context via reflection (internal at JVM level).
    val pkgMeta = PackageMetadata::class.java
        .getDeclaredConstructor(String::class.java, String::class.java, String::class.java, apsiSigCls())
        .apply { isAccessible = true }
        .newInstance("com.ea.gp.bej3", "4.0.0", "4.0.0", null)
    val ctx = BytecodePatchContext::class.java
        .getDeclaredConstructor(PatcherConfig::class.java, PackageMetadata::class.java)
        .apply { isAccessible = true }
        .newInstance(config, pkgMeta) as BytecodePatchContext

    // Replicate the Patcher's path: decode the APK's dex files into the context.
    ctx.javaClass.getDeclaredMethod("decodeDexFiles\$morphe_patcher").apply { isAccessible = true }.invoke(ctx)

    // Check the class map directly.
    val pc = ctx.javaClass.getDeclaredField("patchClasses").apply { isAccessible = true }.get(ctx)
    val cm = pc.javaClass.getDeclaredMethod("getClassMap\$morphe_patcher").apply { isAccessible = true }.invoke(pc) as Map<*, *>
    println("classMap size: ${cm.size}")
    println("has AdvertisingImpl: ${cm.containsKey("Lcsdk/gluads/impl/AdvertisingImpl;")}")


    val dex = DexFileFactory.loadDexFile(baseDir.resolve("classes6.dex"), Opcodes.forApi(24))
    val cls = dex.classes.first { it.type == "Lcsdk/gluads/impl/AdvertisingImpl;" }
    val m = cls.methods.first { it.name == "show" }

    val filters = listOf(
        string("placement can't be null."),
        methodCall(definingClass = "Lcsdk/gluads/util/Common;", name = "require"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Ljava/util/Map;"),
        methodCall(definingClass = "Lcsdk/gluads/IAdvertising;", name = "show")
    )
    val fp = Fingerprint(
        definingClass = "Lcsdk/gluads/impl/AdvertisingImpl;",
        name = "show",
        isStatic = false,
        returnType = "V",
        parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/util/Map;"),
        filters = filters
    )

    val match = with(ctx) {
        fp.matchOrNull()
    }
    println("SEARCH-ALL MATCH RESULT: ${if (match != null) "MATCHED" else "NULL (failed)"}")

    // Also try with the class-def variant
    val match2 = with(ctx) {
        fp.matchOrNull(cls)
    }
    println("CLASS-DEF MATCH RESULT: ${if (match2 != null) "MATCHED" else "NULL (failed)"}")
}
