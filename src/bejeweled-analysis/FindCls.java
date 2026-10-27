import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.Opcodes;
public class FindCls {
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(args[0], Opcodes.forApi(24));
        for (ClassDef c : dex.getClasses()) {
            if (c.getType().contains("gluads")) System.out.println(c.getType());
        }
    }
}
