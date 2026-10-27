import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;

import java.io.File;
import java.util.*;

public class CheckTransform {
    static final String GLU = "com/glu/plugins/gluanalytics/GluAnalytics";

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "/home/andy/morphe/template/src/bejeweled-analysis/patched-privacy.apk/patched/dex";
        File dexDir = new File(path);
        List<DexFile> dexFiles = new ArrayList<>();
        if (dexDir.isFile()) {
            // It's an APK/zip; read dex entries into temp files.
            java.util.zip.ZipInputStream zr =
                new java.util.zip.ZipInputStream(new java.io.FileInputStream(dexDir));
            java.util.zip.ZipEntry e;
            java.util.List<File> tmp = new java.util.ArrayList<>();
            while ((e = zr.getNextEntry()) != null) {
                if (e.getName().endsWith(".dex")) {
                    File tf = java.io.File.createTempFile("dex_", ".dex");
                    tmp.add(tf);
                    java.nio.file.Files.copy(zr, tf.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    dexFiles.add(DexFileFactory.loadDexFile(tf, com.android.tools.smali.dexlib2.Opcodes.getDefault()));
                }
            }
            zr.close();
            for (File tf : tmp) tf.delete();
        } else {
            for (File f : dexDir.listFiles()) {
                if (f.getName().endsWith(".dex")) {
                    dexFiles.add(DexFileFactory.loadDexFile(f, com.android.tools.smali.dexlib2.Opcodes.getDefault()));
                }
            }
        }
        ClassDef gluu = null;
        for (DexFile df : dexFiles) {
            for (ClassDef c : df.getClasses()) {
                if (c.getType().equals("L" + GLU + ";")) { gluu = c; break; }
            }
            if (gluu != null) break;
        }
        if (gluu == null) { System.out.println("GLU ANALYTICS NOT FOUND in " + path); return; }
        System.out.println("\n########## " + path + " ##########");
        System.out.println("=== Found " + gluu.getType());

        for (String target : new String[]{"<init>", "getCustomProperties"}) {
            System.out.println("\n===== METHOD: " + target + " =====");
            boolean any = false;
            for (Method m : gluu.getMethods()) {
                if (!m.getName().equals(target)) continue;
                MethodImplementation mi = m.getImplementation();
                if (mi == null) { System.out.println("  (no body)"); continue; }
                int i = 0;
                for (Instruction in : mi.getInstructions()) {
                    String op = in.getOpcode().name;
                    boolean isConst = op.startsWith("const/");
                    boolean isGb = isGetBoolean(in);
                    boolean show = (target == "<init>" && i >= 95 && i <= 112) || (target == "getCustomProperties" && i <= 14);
                    if (!show) { i++; continue; }
                    StringBuilder sb = new StringBuilder("  #").append(i).append(": ").append(op);
                    if (in instanceof OneRegisterInstruction) sb.append(" v").append(((OneRegisterInstruction) in).getRegisterA());
                    if (isGb) {
                        Object r = ((ReferenceInstruction) in).getReference();
                        if (r instanceof MethodReference) {
                            MethodReference ref = (MethodReference) r;
                            sb.append(" -> ").append(ref.getName()).append("(").append(ref.getDefiningClass()).append(")");
                        }
                    }
                    System.out.println(sb);
                    i++;
                }
            }
            if (!any) System.out.println("  (no const/* or getBoolean found)");
        }
    }

    static boolean isGetBoolean(Instruction in) {
        if (in instanceof ReferenceInstruction) {
            Object r = ((ReferenceInstruction) in).getReference();
            return r instanceof MethodReference
                && ((MethodReference) r).getName().equals("getBoolean")
                && ((MethodReference) r).getDefiningClass().equals("Landroid/content/SharedPreferences;");
        }
        return false;
    }
}
