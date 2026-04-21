package com.ruoyi.Xidian.utils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通过反射调用 MATLAB Engine for Java，避免工程在没有 engine jar 时无法编译/运行。
 *
 * 复用策略：
 * - 优先连接到已共享的 MATLAB 会话（需要 MATLAB 侧执行 matlab.engine.shareEngine）
 * - 若不存在共享会话，则启动一个 engine 并在进程内复用
 */
public final class MatlabEngineReuseUtil {

    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static volatile Object ENGINE; // com.mathworks.engine.MatlabEngine
    private static volatile boolean NATIVE_READY;

    private MatlabEngineReuseUtil() {}

    public static boolean isEngineAvailable() {
        try {
            Class.forName(
                    "com.mathworks.engine.MatlabEngine",
                    false,
                    MatlabEngineReuseUtil.class.getClassLoader()
            );
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static ExecutionResult warmup(String matlabCommand, long timeoutSeconds) {
        long startedAt = System.currentTimeMillis();
        boolean locked = false;
        try {
            locked = LOCK.tryLock(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!locked) {
                return ExecutionResult.fail("MATLAB Engine 预热失败（等待锁超时）", null, System.currentTimeMillis() - startedAt);
            }
            ensureEngine(matlabCommand);
            return ExecutionResult.ok("MATLAB Engine 已就绪", "", 0, System.currentTimeMillis() - startedAt);
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return ExecutionResult.fail("MATLAB Engine 预热失败：" + msg, null, System.currentTimeMillis() - startedAt);
        } finally {
            if (locked) {
                LOCK.unlock();
            }
        }
    }

    public static ExecutionResult runCodeWithEngine(String matlabCommand, String code, long timeoutSeconds) {
        Objects.requireNonNull(code, "code");
        long startedAt = System.currentTimeMillis();
        boolean locked = false;
        try {
            locked = LOCK.tryLock(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!locked) {
                return ExecutionResult.fail("MATLAB Engine 忙碌中（等待锁超时）", null, System.currentTimeMillis() - startedAt);
            }

            Object engine = ensureEngine(matlabCommand);

            // 将代码写入临时 .m，然后用 evalc(run(...)) 捕获 stdout
            File tmp = File.createTempFile("matlab_engine_script_", ".m");
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), code);

            // out = evalc("run('<path>')");
            String path = tmp.getAbsolutePath().replace("\\", "/").replace("'", "''");
            // MatlabEngine.eval(String) 不返回值；因此用 feval('evalc', ...) 取返回字符串更稳
            // 反射：engine.feval("evalc", "run('path')")
            Method feval = engine.getClass().getMethod("feval", String.class, Object[].class);
            Object outObj = feval.invoke(engine, "evalc", new Object[]{ new Object[]{ "run('" + path + "')" } });
            String stdout = outObj == null ? "" : outObj.toString();

            return ExecutionResult.ok(stdout, "", 0, System.currentTimeMillis() - startedAt);
        } catch (InvocationTargetException ite) {
            // MATLAB 执行错误通常包在 InvocationTargetException 里
            Throwable root = ite.getTargetException() == null ? ite : ite.getTargetException();
            String msg = root.getMessage() == null ? root.toString() : root.getMessage();
            return ExecutionResult.fail(msg, null, System.currentTimeMillis() - startedAt);
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return ExecutionResult.fail(msg, null, System.currentTimeMillis() - startedAt);
        } finally {
            if (locked) {
                LOCK.unlock();
            }
        }
    }

    private static Object ensureEngine(String matlabCommand) throws Exception {
        Object cached = ENGINE;
        if (cached != null) return cached;

        synchronized (MatlabEngineReuseUtil.class) {
            if (ENGINE != null) return ENGINE;

            ensureNativeLibraries(matlabCommand);
            Class<?> engineClazz = Class.forName("com.mathworks.engine.MatlabEngine");

            // 1) 尝试连接已共享会话
            try {
                Method findMatlab = engineClazz.getMethod("findMatlab");
                String[] names = (String[]) findMatlab.invoke(null);
                if (names != null && names.length > 0 && names[0] != null && !names[0].isBlank()) {
                    Method connectMatlab = engineClazz.getMethod("connectMatlab", String.class);
                    ENGINE = connectMatlab.invoke(null, names[0]);
                    return ENGINE;
                }
            } catch (NoSuchMethodException ignored) {
                // 不同版本 engine API 可能没有 findMatlab/connectMatlab，继续尝试 startMatlab
            }

            // 2) 启动一个 engine（进程内复用）
            Method startMatlab = engineClazz.getMethod("startMatlab");
            ENGINE = startMatlab.invoke(null);
            return ENGINE;
        }
    }

    private static void ensureNativeLibraries(String matlabCommand) {
        if (NATIVE_READY) {
            return;
        }

        synchronized (MatlabEngineReuseUtil.class) {
            if (NATIVE_READY) {
                return;
            }

            File matlabRoot = MatlabCommandUtil.resolveMatlabRoot(matlabCommand);
            if (matlabRoot == null || !matlabRoot.isDirectory()) {
                throw new IllegalStateException("Cannot resolve MATLAB root from command: " + matlabCommand);
            }

            File binWin64 = new File(matlabRoot, "bin/win64");
            File externBinWin64 = new File(matlabRoot, "extern/bin/win64");
            File runtimeWin64 = new File(matlabRoot, "runtime/win64");

            appendLibraryPath(binWin64);
            appendLibraryPath(externBinWin64);
            appendLibraryPath(runtimeWin64);

            if (isWindows()) {
                preloadWindowsDependencies(runtimeWin64, externBinWin64);
                loadLibraryIfPresent(new File(binWin64, "nativemvm.dll"));
                loadLibraryIfPresent(new File(externBinWin64, "libMatlabEngine.dll"));
            }

            NATIVE_READY = true;
        }
    }

    private static void preloadWindowsDependencies(File runtimeWin64, File externBinWin64) {
        loadLibrariesByPrefix(runtimeWin64, "mclmcrrt");
        loadLibrariesByPrefix(runtimeWin64, "libMatlabCppSharedLib");
        loadLibrariesByPrefix(runtimeWin64, "mclcom");
        loadLibrariesByPrefix(runtimeWin64, "mclxlmain");
        loadLibraryIfPresent(new File(externBinWin64, "libMatlabDataArray.dll"));
        loadLibraryIfPresent(new File(externBinWin64, "libMatlabEngine.dll"));
    }

    private static void loadLibrariesByPrefix(File dir, String prefix) {
        if (dir == null || !dir.isDirectory() || prefix == null || prefix.isBlank()) {
            return;
        }

        String lowerPrefix = prefix.toLowerCase();
        File[] matches = dir.listFiles((ignored, name) -> {
            if (name == null) {
                return false;
            }
            String lowerName = name.toLowerCase();
            return lowerName.startsWith(lowerPrefix) && lowerName.endsWith(".dll");
        });

        if (matches == null || matches.length == 0) {
            return;
        }

        Arrays.sort(matches, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File match : matches) {
            loadLibraryIfPresent(match);
        }
    }

    private static void appendLibraryPath(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }

        String absolutePath = dir.getAbsolutePath();
        String current = System.getProperty("java.library.path", "");
        String separator = File.pathSeparator;

        for (String entry : current.split(java.util.regex.Pattern.quote(separator))) {
            if (absolutePath.equalsIgnoreCase(entry)) {
                return;
            }
        }

        String updated = current == null || current.isBlank() ? absolutePath : current + separator + absolutePath;
        System.setProperty("java.library.path", updated);
    }

    private static void loadLibraryIfPresent(File file) {
        if (file == null || !file.isFile()) {
            return;
        }

        try {
            System.load(file.getAbsolutePath());
        } catch (UnsatisfiedLinkError error) {
            String message = error.getMessage();
            if (message == null || !message.contains("already loaded")) {
                throw error;
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static final class ExecutionResult {
        public final boolean success;
        public final String stdout;
        public final String stderr;
        public final Integer exitCode;
        public final long durationMs;

        private ExecutionResult(boolean success, String stdout, String stderr, Integer exitCode, long durationMs) {
            this.success = success;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.exitCode = exitCode;
            this.durationMs = durationMs;
        }

        public static ExecutionResult ok(String stdout, String stderr, Integer exitCode, long durationMs) {
            return new ExecutionResult(true, stdout, stderr, exitCode, durationMs);
        }

        public static ExecutionResult fail(String stderr, Integer exitCode, long durationMs) {
            return new ExecutionResult(false, "", stderr, exitCode, durationMs);
        }
    }
}

