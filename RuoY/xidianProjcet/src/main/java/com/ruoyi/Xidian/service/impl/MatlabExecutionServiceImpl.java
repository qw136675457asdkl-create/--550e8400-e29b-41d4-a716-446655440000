package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabTaskControlResultDTO;
import com.ruoyi.Xidian.service.MatlabExecutionService;
import com.ruoyi.Xidian.utils.MatlabCommandUtil;
import com.ruoyi.Xidian.utils.MatlabEngineReuseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class MatlabExecutionServiceImpl implements MatlabExecutionService {

    private static final AtomicBoolean IN_PROGRESS = new AtomicBoolean(false);
    private static final AtomicReference<Process> ACTIVE_PROCESS = new AtomicReference<>();

    @Value("${matlab.command:matlab}")
    private String matlabCommand;

    @Value("${matlab.batch-mode:-batch}")
    private String batchMode;

    @Value("${matlab.timeout:300}")
    private int timeout;

    @Value("${matlab.single-instance:true}")
    private boolean singleInstance;

    @Value("${matlab.mode:auto}")
    private String matlabMode;

    @Override
    public MatlabExecutionResultDTO executeMatlab(String code) {
        File tempFile = null;
        Process process = null;
        long startedAt = System.currentTimeMillis();

        try {
            log.info("Starting MATLAB execution");

            if (singleInstance && !IN_PROGRESS.compareAndSet(false, true)) {
                return buildFailureResult(
                        "MATLAB task is already running. Please wait for the current task to finish.",
                        startedAt
                );
            }

            if (shouldUseEngine()) {
                MatlabExecutionResultDTO engineResult = executeWithEngine(code, startedAt);
                if (engineResult.isSuccess() || isEngineOnlyMode()) {
                    return engineResult;
                }
                log.warn("MATLAB engine execution failed, falling back to process mode: {}", engineResult.getStderr());
            }

            tempFile = File.createTempFile("matlab_script_", ".m");
            tempFile.deleteOnExit();
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(code);
                if (!code.trim().endsWith("exit")) {
                    writer.write("\n\nexit;");
                }
            }

            String command = MatlabCommandUtil.getMatlabCommand(matlabCommand);
            String scriptPath = tempFile.getAbsolutePath().replace("\\", "/").replace("'", "''");
            ProcessBuilder pb = new ProcessBuilder(
                    command,
                    batchMode,
                    "run('" + scriptPath + "')"
            );
            pb.redirectErrorStream(false);

            process = pb.start();
            ACTIVE_PROCESS.set(process);

            Charset charset = Charset.defaultCharset();
            StreamCollector stdoutCollector = new StreamCollector(process.getInputStream(), charset);
            StreamCollector stderrCollector = new StreamCollector(process.getErrorStream(), charset);
            Thread stdoutThread = startCollectorThread("matlab-stdout-reader", stdoutCollector);
            Thread stderrThread = startCollectorThread("matlab-stderr-reader", stderrCollector);

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                terminateProcessTree(process);
                joinCollectorThread(stdoutThread);
                joinCollectorThread(stderrThread);

                String stdout = filterMatlabNoise(stdoutCollector.getContent());
                String stderr = filterMatlabNoise(stderrCollector.getContent());
                String timeoutMessage = "MATLAB execution timed out after " + timeout + " seconds";

                return MatlabExecutionResultDTO.builder()
                        .success(false)
                        .stdout(stdout)
                        .stderr(stderr.isBlank() ? timeoutMessage : timeoutMessage + "\n" + stderr)
                        .exitCode(null)
                        .durationMs(System.currentTimeMillis() - startedAt)
                        .startedAtEpochMs(startedAt)
                        .build();
            }

            int exitCode = process.exitValue();
            joinCollectorThread(stdoutThread);
            joinCollectorThread(stderrThread);

            String stdout = filterMatlabNoise(stdoutCollector.getContent());
            String stderr = filterMatlabNoise(stderrCollector.getContent());
            boolean success = exitCode == 0;

            if (success) {
                log.info("MATLAB execution succeeded with exit code {}", exitCode);
            } else {
                log.error("MATLAB execution failed with exit code {}", exitCode);
            }

            return MatlabExecutionResultDTO.builder()
                    .success(success)
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } catch (IOException e) {
            log.error("Failed to start MATLAB process", e);
            return buildFailureResult("Failed to start MATLAB process: " + e.getMessage(), startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MATLAB execution thread was interrupted", e);
            return buildFailureResult("MATLAB execution was interrupted: " + e.getMessage(), startedAt);
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage() == null ? "MATLAB execution failed" : e.getMessage();
            log.error("MATLAB execution failed", e);
            return buildFailureResult(errorMsg, startedAt);
        } finally {
            if (process != null) {
                ACTIVE_PROCESS.compareAndSet(process, null);
            }
            if (singleInstance) {
                IN_PROGRESS.set(false);
            }
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary MATLAB script: {}", tempFile.getAbsolutePath());
            }
            log.info("MATLAB execution finished");
        }
    }

    @Override
    public MatlabTaskControlResultDTO cancelCurrentTask() {
        Process process = ACTIVE_PROCESS.get();

        if (process == null) {
            if (IN_PROGRESS.get()) {
                return MatlabTaskControlResultDTO.builder()
                        .success(false)
                        .cancelled(false)
                        .message("A MATLAB task is active, but no cancellable external process is registered.")
                        .build();
            }
            return MatlabTaskControlResultDTO.builder()
                    .success(true)
                    .cancelled(false)
                    .message("No running MATLAB process was found.")
                    .build();
        }

        if (!process.isAlive()) {
            ACTIVE_PROCESS.compareAndSet(process, null);
            return MatlabTaskControlResultDTO.builder()
                    .success(true)
                    .cancelled(false)
                    .message("No running MATLAB process was found.")
                    .build();
        }

        boolean cancelled = terminateProcessTree(process);
        if (cancelled) {
            ACTIVE_PROCESS.compareAndSet(process, null);
        }

        return MatlabTaskControlResultDTO.builder()
                .success(cancelled)
                .cancelled(cancelled)
                .message(cancelled
                        ? "Running MATLAB process has been cancelled."
                        : "Failed to cancel the running MATLAB process.")
                .build();
    }

    private MatlabExecutionResultDTO executeWithEngine(String code, long startedAt) {
        MatlabEngineReuseUtil.ExecutionResult result = MatlabEngineReuseUtil.runCodeWithEngine(matlabCommand, code, timeout);
        return MatlabExecutionResultDTO.builder()
                .success(result.success)
                .stdout(filterMatlabNoise(result.stdout))
                .stderr(filterMatlabNoise(result.stderr))
                .exitCode(result.exitCode)
                .durationMs(result.durationMs)
                .startedAtEpochMs(startedAt)
                .build();
    }

    private boolean isEngineOnlyMode() {
        return "engine".equalsIgnoreCase(matlabMode == null ? "" : matlabMode.trim());
    }

    private boolean shouldUseEngine() {
        String mode = matlabMode == null ? "auto" : matlabMode.trim().toLowerCase();
        if ("process".equals(mode)) {
            return false;
        }
        if ("engine".equals(mode)) {
            return MatlabEngineReuseUtil.isEngineAvailable();
        }
        return MatlabEngineReuseUtil.isEngineAvailable();
    }

    private static MatlabExecutionResultDTO buildFailureResult(String stderr, long startedAt) {
        return MatlabExecutionResultDTO.builder()
                .success(false)
                .stdout("")
                .stderr(stderr)
                .exitCode(null)
                .durationMs(System.currentTimeMillis() - startedAt)
                .startedAtEpochMs(startedAt)
                .build();
    }

    private static boolean terminateProcessTree(Process process) {
        if (process == null) {
            return false;
        }
        if (!process.isAlive()) {
            return true;
        }

        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroy);
        process.destroy();

        if (waitForExit(process, 2)) {
            return true;
        }

        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        return waitForExit(process, 5) || !process.isAlive();
    }

    private static boolean waitForExit(Process process, int seconds) {
        try {
            return process.waitFor(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    private static String readAll(InputStream inputStream, Charset charset) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static Thread startCollectorThread(String name, StreamCollector collector) {
        Thread thread = new Thread(collector, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void joinCollectorThread(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String filterMatlabNoise(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\\R", -1)) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("MATLAB is selecting")) {
                continue;
            }
            if (trimmed.startsWith("Warning:")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream inputStream;
        private final Charset charset;
        private final StringBuilder buffer = new StringBuilder();

        private StreamCollector(InputStream inputStream, Charset charset) {
            this.inputStream = inputStream;
            this.charset = charset;
        }

        @Override
        public void run() {
            try {
                buffer.append(readAll(inputStream, charset));
            } catch (IOException e) {
                log.warn("Failed to read MATLAB stream: {}", e.getMessage());
            }
        }

        private String getContent() {
            return buffer.toString();
        }
    }
}
