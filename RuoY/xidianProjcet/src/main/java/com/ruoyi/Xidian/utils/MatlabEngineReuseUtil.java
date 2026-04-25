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
 * Uses reflection so the project can still run in process mode when the MATLAB
 * Engine jar is not present on the classpath.
 */
public final class MatlabEngineReuseUtil {

    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static volatile Object ENGINE;
    private static volatile boolean NATIVE_READY;

    private MatlabEngineReuseUtil() {
    }

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
                return ExecutionResult.fail(
                        "MATLAB Engine warmup timed out while waiting for the shared lock.",
                        null,
                        System.currentTimeMillis() - startedAt
                );
            }

            ensureEngine(matlabCommand);
            return ExecutionResult.ok(
                    "MATLAB Engine warmed up.",
                    "",
                    0,
                    System.currentTimeMillis() - startedAt
            );
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return ExecutionResult.fail(
                    "MATLAB Engine warmup failed: " + msg,
                    null,
                    System.currentTimeMillis() - startedAt
            );
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
                return ExecutionResult.fail(
                        "MATLAB Engine is busy and did not become available before timeout.",
                        null,
                        System.currentTimeMillis() - startedAt
                );
            }

            Object engine = ensureEngine(matlabCommand);
            File tmp = File.createTempFile("matlab_engine_script_", ".m");
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), code);

            String path = tmp.getAbsolutePath().replace("\\", "/").replace("'", "''");
            Method feval = engine.getClass().getMethod("feval", String.class, Object[].class);
            Object outObj = feval.invoke(engine, "evalc", new Object[]{new Object[]{"run('" + path + "')"}});
            String stdout = outObj == null ? "" : outObj.toString();

            return ExecutionResult.ok(stdout, "", 0, System.currentTimeMillis() - startedAt);
        } catch (InvocationTargetException e) {
            Throwable root = e.getTargetException() == null ? e : e.getTargetException();
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
        if (cached != null) {
            return cached;
        }

        synchronized (MatlabEngineReuseUtil.class) {
            if (ENGINE != null) {
                return ENGINE;
            }

            ensureNativeLibraries(matlabCommand);
            Class<?> engineClazz = Class.forName("com.mathworks.engine.MatlabEngine");

            try {
                Method findMatlab = engineClazz.getMethod("findMatlab");
                String[] names = (String[]) findMatlab.invoke(null);
                if (names != null && names.length > 0 && names[0] != null && !names[0].isBlank()) {
                    Method connectMatlab = engineClazz.getMethod("connectMatlab", String.class);
                    ENGINE = connectMatlab.invoke(null, names[0]);
                    return ENGINE;
                }
            } catch (NoSuchMethodException ignored) {
                // Older engine versions may only expose startMatlab().
            }

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

            if (isWindows()) {
                File binDir = new File(matlabRoot, "bin/win64");
                File externDir = new File(matlabRoot, "extern/bin/win64");
                File runtimeDir = new File(matlabRoot, "runtime/win64");
                appendLibraryPath(binDir);
                appendLibraryPath(externDir);
                appendLibraryPath(runtimeDir);
                preloadWindowsDependencies(binDir, externDir, runtimeDir);
            } else if (isLinux()) {
                File binDir = new File(matlabRoot, "bin/glnxa64");
                File externDir = new File(matlabRoot, "extern/bin/glnxa64");
                File runtimeDir = new File(matlabRoot, "runtime/glnxa64");
                appendLibraryPath(binDir);
                appendLibraryPath(externDir);
                appendLibraryPath(runtimeDir);
                preloadLinuxDependencies(binDir, externDir, runtimeDir);
            }

            NATIVE_READY = true;
        }
    }

    private static void preloadWindowsDependencies(File binDir, File externDir, File runtimeDir) {
        loadLibrariesByPrefix(runtimeDir, "mclmcrrt");
        loadLibrariesByPrefix(runtimeDir, "libMatlabCppSharedLib");
        loadLibrariesByPrefix(runtimeDir, "mclcom");
        loadLibrariesByPrefix(runtimeDir, "mclxlmain");
        loadLibrariesByPrefix(externDir, "libMatlabDataArray");
        loadLibrariesByPrefix(externDir, "libMatlabEngine");
        loadLibrariesByPrefix(binDir, "nativemvm");
    }

    private static void preloadLinuxDependencies(File binDir, File externDir, File runtimeDir) {
        loadLibrariesByPrefix(runtimeDir, "libmwmclmcrrt");
        loadLibrariesByPrefix(runtimeDir, "libMatlabCppSharedLib");
        loadLibrariesByPrefix(externDir, "libMatlabDataArray");
        loadLibrariesByPrefix(externDir, "libMatlabEngine");
        loadLibrariesByPrefix(binDir, "libmwmvm");
    }

    private static void loadLibrariesByPrefix(File dir, String prefix) {
        if (dir == null || !dir.isDirectory() || prefix == null || prefix.isBlank()) {
            return;
        }

        String lowerPrefix = prefix.toLowerCase();
        String libraryToken = sharedLibraryToken();
        File[] matches = dir.listFiles((ignored, name) -> {
            if (name == null) {
                return false;
            }
            String lowerName = name.toLowerCase();
            return lowerName.startsWith(lowerPrefix) && lowerName.contains(libraryToken);
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
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            if (isWindows() ? absolutePath.equalsIgnoreCase(entry) : absolutePath.equals(entry)) {
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

    private static String sharedLibraryToken() {
        if (isWindows()) {
            return ".dll";
        }
        if (isLinux()) {
            return ".so";
        }
        return ".dylib";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
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
