import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.ReferenceType;

public class DumpSmali {
    public static void main(String[] args) throws Exception {
        String dexFile = args[0];
        String className = args[1];
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.forApi(24));
        for (ClassDef c : dex.getClasses()) {
            if (c.getType().equals(className)) {
                System.out.println("# class " + c);
                for (Method m : c.getMethods()) {
                    System.out.print("--- method: " + m.getName() + " ret=" + m.getReturnType() + " access=" + m.getAccessFlags() + " params=[");
                    for (int i = 0; i < m.getParameterTypes().size(); i++) {
                        if (i > 0) System.out.print(", ");
                        System.out.print(m.getParameterTypes().get(i));
                    }
                    System.out.println("]");
                    MethodImplementation impl = m.getImplementation();
                    if (impl != null) {
                        for (Instruction insn : impl.getInstructions()) {
                            StringBuilder sb = new StringBuilder();
                            sb.append(insn.getOpcode().name.toLowerCase()).append(' ');
                            if (insn instanceof VariableRegisterInstruction) {
                                sb.append("reg=").append(((VariableRegisterInstruction) insn).getRegisterCount());
                            }
                            if (insn instanceof ReferenceInstruction) {
                                sb.append(" ref=").append(((ReferenceInstruction) insn).getReference());
                                sb.append(" [rt=").append(referenceTypeStr(((ReferenceInstruction) insn).getReferenceType())).append("]");
                            }
                            System.out.println(sb.toString());
                        }
                    }
                }
            }
        }
    }

    static String referenceTypeStr(int type) {
        switch (type) {
            case ReferenceType.STRING: return "STRING";
            case ReferenceType.TYPE: return "TYPE";
            case ReferenceType.FIELD: return "FIELD";
            case ReferenceType.METHOD: return "METHOD";
            case ReferenceType.METHOD_PROTO: return "PROTO";
            case ReferenceType.METHOD_HANDLE: return "HANDLE";
            case ReferenceType.CALL_SITE: return "CALLSITE";
            default: return "?" + type;
        }
    }
}
