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

public class DumpShowAd {
    // targets: "classType->methodName"
    static List<String> targets = new ArrayList<>();
    static void loadDexes(List<DexBackedDexFile> dexes) throws Exception {
        File dir = new File(".");
        File[] dexts = dir.listFiles((d, n) -> n.endsWith(".dex"));
        if (dexts != null) for (File f : dexts) {
            try { dexes.add(DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(new FileInputStream(f)))); }
            catch (Exception e) {}
        }
    }
    public static void main(String[] args) throws Exception {
        targets.add("Lcom/ea/game/bejeweled3/DraperImageDriver;->ShowAd");
        targets.add("Lcom/popcap/pcsp/marketing/GoogleImaDriver;->ShowAd");
        targets.add("Lcsdk/gluads/impl/AdvertisingImpl;->show");
        List<DexBackedDexFile> dexes = new ArrayList<>();
        loadDexes(dexes);
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
                        System.out.println("### " + target + " (ret=" + m.getReturnType() + " params=" + m.getParameterTypes() + ") ###");
                        if (m.getImplementation() == null) { System.out.println("  (no implementation)"); continue; }
                        m.getImplementation().getInstructions().forEach(i -> {
                            String extra = "";
                            if (i instanceof DexBackedInstruction35c) {
                                extra = "  | ref=" + ((DexBackedInstruction35c) i).getReference();
                            } else if (i.getClass().getName().contains("Instruction11n")) {
                                try { extra = "  | reg=" + i.getClass().getMethod("getRegisterA").invoke(i) + " val=" + i.getClass().getMethod("getNarrowLiteral").invoke(i); } catch (Exception e) {}
                            } else if (i.getClass().getName().contains("Instruction21c")) {
                                try { extra = "  | str=" + i.getClass().getMethod("getString").invoke(i); } catch (Exception e) {}
                            }
                            System.out.println("  " + i.getOpcode() + " " + extra);
                        });
                    }
                }
            }
            if (!found) System.out.println("### " + target + " NOT FOUND ###");
            System.out.println();
        }
    }
}
