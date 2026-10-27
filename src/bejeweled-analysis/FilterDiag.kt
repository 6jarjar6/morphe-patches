import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes

import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.Opcode
import java.io.File

fun main() {
    val dex = DexFileFactory.loadDexFile(
        File("/home/andy/morphe/template/src/bejeweled-analysis/classes6.dex"),
        Opcodes.forApi(24)
    )

    val cls = dex.classes.first { it.type == "Lcsdk/gluads/impl/AdvertisingImpl;" }
    val m = cls.methods.first { it.name == "show" }
    println("Method: ${m.name} ret=${m.returnType} params=${m.parameterTypes}")

    // Mirror the fingerprint filters exactly.
    val f1 = string("placement can't be null.")
    val f2 = methodCall(definingClass = "Lcsdk/gluads/util/Common;", name = "require")
    val f3 = fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Ljava/util/Map;")
    val f4 = methodCall(definingClass = "Lcsdk/gluads/IAdvertising;", name = "show")
    val filters = listOf(f1, f2, f3, f4)

    val insns = m.implementation?.instructions?.toList() ?: emptyList()
    println("Total instructions: ${insns.size}")

    var idx = -1
    for (f in filters) {
        var foundAt = -1
        for (i in idx + 1 until insns.size) {
            if (f.matches(m, insns[i])) {
                foundAt = i
                break
            }
        }
        if (foundAt < 0) {
            println("FILTER FAILED: ${filters.indexOf(f)} ($f)  -- no instruction matches after index ${idx}")
        } else {
            println("FILTER ok:     ${filters.indexOf(f)} ($f)  -- matched at index $foundAt")
            idx = foundAt
        }
    }

    // Also test the full fingerprint match.
    val fp = Fingerprint(
        definingClass = "Lcsdk/gluads/impl/AdvertisingImpl;",
        name = "show",
        isStatic = false,
        returnType = "V",
        parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/util/Map;"),
        filters = filters
    )
    println("\n=== Fingerprint parsed properties (via reflection) ===")
    for (prop in listOf("definingClass", "name", "returnType", "parameters", "accessFlags", "isStatic")) {
        try {
            val getter = fp.javaClass.methods.first { it.name == "get" + prop.replaceFirstChar { c -> c.uppercase() } }
            println("$prop = ${getter.invoke(fp)}")
        } catch (e: Exception) {
            println("$prop = <could not read>")
        }
    }
    println("filters count = ${fp.filters?.size}")
}
