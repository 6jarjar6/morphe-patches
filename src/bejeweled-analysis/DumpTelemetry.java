import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction35c;
import com.android.tools.smali.dexlib2.Opcodes;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class DumpTelemetry {
    static List<String> targets = new ArrayList<>();
    static String loadDir = ".";
    static void loadDexes(List<DexBackedDexFile> dexes) throws Exception {
        File dir = new File(loadDir);
        File[] dexts = dir.listFiles((d, n) -> n.endsWith("dex"));
        if (dexts != null) for (File f : dexts) {
            try { dexes.add(DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(new FileInputStream(f)))); }
            catch (Exception e) {}
        }
    }
    static void dumpAll(DexBackedMethod m) {
        System.out.println("  --- all instructions ---");
        final int[] n = {0};
        m.getImplementation().getInstructions().forEach(i -> {
            String op = i.getOpcode().toString();
            String ref = "";
            if (op.startsWith("INVOKE")) {
                try {
                    if (i instanceof DexBackedInstruction35c) ref = " | ref=" + ((DexBackedInstruction35c) i).getReference();
                    else {
                        Object r = i.getClass().getMethod("getReference").invoke(i);
                        ref = " | ref=" + r + " [cls=" + i.getClass().getSimpleName() + "]";
                    }
                } catch (Exception e) { ref = " | (ref err: " + e.getMessage() + ")"; }
            }
            if (n[0] < 60) System.out.println("    " + (n[0] + 1) + ": " + op + " " + ref);
            n[0]++;
        });
        System.out.println("  ... total instructions: " + n[0]);
    }
    public static void main(String[] args) throws Exception {
        targets.add("Lcom/ea/nimble/tracking/NimbleTrackingImplBase;->isAbleToPostEvent");
        targets.add("Lcom/ea/nimble/OperationalTelemetryDispatchImpl;->logEvent");
        if (args.length > 0) loadDir = args[0];
        List<DexBackedDexFile> dexes = new ArrayList<>();
        loadDexes(dexes);
        System.out.println("Loaded " + dexes.size() + " dexes from " + loadDir);
        for (String target : targets) {
            String[] parts = target.split("->");
            String clsType = parts[0];
            String methodName = parts[1];
            boolean found = false;
            for (DexBackedDexFile dx : dexes) {
                for (DexBackedClassDef c : dx.getClasses()) {
                    if (!c.getType().equals(clsType)) continue;
                    for (DexBackedMethod m : c.getMethods()) {
                        if (!m.getName().equals(methodName)) continue;
                        found = true;
                        System.out.println("### " + target + " ret=" + m.getReturnType() + " params=" + m.getParameterTypes());
                        if (m.getImplementation() == null) { System.out.println("  (no impl)"); continue; }
                        dumpAll(m);
                    }
                }
            }
            if (!found) System.out.println("### " + target + " NOT FOUND ###");
            System.out.println();
        }
    }
}
