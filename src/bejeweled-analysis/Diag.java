import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.Opcodes;
public class Diag {
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(args[0], Opcodes.forApi(24));
        int n = 0;
        for (ClassDef c : dex.getClasses()) {
            n++;
            if (n <= 5) System.out.println(c.getType());
        }
        System.out.println("total classes: " + n);
    }
}
