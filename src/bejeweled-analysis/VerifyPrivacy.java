import app.morphe.patcher.Patcher;
import app.morphe.patcher.PatcherConfig;
import app.morphe.patcher.PatcherResult;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchResult;
import app.morphe.patcher.patch.PatchLoader;
import app.morphe.patcher.patch.PatchKt;
import app.morphe.patcher.dex.NoOpDexVerifier;
import app.morphe.patcher.dex.BytecodeMode;
import app.morphe.patcher.resource.CpuArchitecture;

import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;
import static kotlinx.coroutines.BuildersKt.runBlocking;

import kotlin.Unit;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VerifyPrivacy {
    public static void main(String[] args) throws Exception {
        File baseDir = new File("/home/andy/morphe/template/src/bejeweled-analysis");
        File apkFile = new File(baseDir, "base.apk");
        File patchedFile = new File(baseDir, "patched-privacy.apk");
        File workspace = new File(baseDir, "workspace-privacy");
        workspace.mkdirs();

        File mpp = new File("/home/andy/morphe/template/patches/build/libs/").listFiles()[0];
        System.out.println("=== Applying patches from: " + mpp.getName());

        Set<File> mpps = new HashSet<>();
        mpps.add(mpp);
        PatchLoader jarLoader = PatchKt.loadPatchesFromJar(mpps);
        System.out.println("=== Loaded " + jarLoader.getSize() + " patch(es)");

        PatcherConfig config = new PatcherConfig(
            apkFile,
            patchedFile,
            new HashSet<>(java.util.Arrays.asList(CpuArchitecture.ARM64_V8A, CpuArchitecture.ARMEABI_V7A)),
            BytecodeMode.FULL,
            NoOpDexVerifier.INSTANCE,
            workspace,
            "2.0.0",
            false
        );

        Patcher patcher = new Patcher(config);
        patcher.plusAssign(jarLoader);

        // Collect patch results.
        final Set<String> outcomes = Collections.synchronizedSet(new java.util.HashSet<String>());
        kotlin.coroutines.Continuation<? super Unit> cont = new kotlin.coroutines.Continuation<>() {
            public kotlin.coroutines.CoroutineContext getContext() { return kotlin.coroutines.EmptyCoroutineContext.INSTANCE; }
            public void resumeWith(Object result) { }
        };
        runBlocking(kotlin.coroutines.EmptyCoroutineContext.INSTANCE, (scope, c) -> {
            Flow<PatchResult> flow = patcher.invoke();
            flow.collect(new FlowCollector<PatchResult>() {
                public Object emit(PatchResult r, kotlin.coroutines.Continuation<? super Unit> cc) {
                    String name = r.getPatch().getName();
                    Throwable ex = r.getException();
                    if (ex == null) {
                        outcomes.add("OK: " + name);
                    } else {
                        outcomes.add("FAIL: " + name + " -> " + ex.getMessage());
                    }
                    return Unit.INSTANCE;
                }
            }, cont);
            return null;
        });

        for (String o : outcomes) System.out.println("RESULT: " + o);

        PatcherResult result = patcher.get();
        patcher.close();

        System.out.println("=== Patched dex files: " + result.getDexFiles().size());
        for (PatcherResult.PatchedDexFile d : result.getDexFiles()) {
            System.out.println("    dex: " + d.getName());
        }
        if (patchedFile.exists()) {
            System.out.println("=== Patched output: " + patchedFile.length() + " bytes");
        }
    }
}
