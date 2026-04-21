package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.service.MatlabExecutionService;
import com.ruoyi.Xidian.utils.MatlabCommandUtil;
import com.ruoyi.Xidian.utils.MatlabEngineReuseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class MatlabExecutionServiceImpl implements MatlabExecutionService {

    private static final AtomicBoolean IN_PROGRESS = new AtomicBoolean(false);

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
        long startedAt = System.currentTimeMillis();

        try {
            log.info("MATLAB执行服务开始处理");

            if (singleInstance) {
                if (!IN_PROGRESS.compareAndSet(false, true)) {
                    String msg = "MATLAB 正在运行中，本次请求不再新启动 MATLAB。";
                    log.warn(msg);
                    return MatlabExecutionResultDTO.builder()
                            .success(false)
                            .stdout("")
                            .stderr(msg)
                            .exitCode(null)
                            .durationMs(System.currentTimeMillis() - startedAt)
                            .startedAtEpochMs(startedAt)
                            .build();
                }
            }

            // 优先复用 MATLAB Engine（连接已启动共享会话或启动一个并复用）
            if (shouldUseEngine()) {
                MatlabExecutionResultDTO engineResult = executeWithEngine(code, startedAt);
                if (engineResult.isSuccess() || isEngineOnlyMode()) {
                    return engineResult;
                }
                log.warn("MATLAB Engine execution failed, fallback to process mode: {}", engineResult.getStderr());
            }

            // 1. 创建临时MATLAB脚本文件
            tempFile = File.createTempFile("matlab_script_", ".m");
            tempFile.deleteOnExit();
            log.info("创建临时脚本文件: {}", tempFile.getAbsolutePath());

            // 2. 写入MATLAB代码
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(code);
                // 确保脚本最后有退出命令（可选）
                if (!code.trim().endsWith("exit")) {
                    writer.write("\n\nexit;");
                }
            }
            log.info("MATLAB代码已写入临时文件");

            // 3. 获取MATLAB命令
            String command = MatlabCommandUtil.getMatlabCommand(matlabCommand);
            log.info("使用命令: {}", command);

            // 4. 执行MATLAB进程
            // MATLAB批处理模式：matlab -batch "run('script.m')"
            String scriptPath = tempFile.getAbsolutePath().replace("\\", "/").replace("'", "''");
            ProcessBuilder pb = new ProcessBuilder(
                    command,
                    batchMode,
                    "run('" + scriptPath + "')"
            );

            pb.redirectErrorStream(false);

            Process process = pb.start();
            Charset charset = Charset.forName("GBK");
            StreamCollector stdoutCollector = new StreamCollector(process.getInputStream(), charset);
            StreamCollector stderrCollector = new StreamCollector(process.getErrorStream(), charset);
            Thread stdoutThread = startCollectorThread("matlab-stdout-reader", stdoutCollector);
            Thread stderrThread = startCollectorThread("matlab-stderr-reader", stderrCollector);
            log.info("MATLAB进程已启动，等待执行结果...");

            // 5. 等待执行完成
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!completed) {
                log.error("MATLAB 执行超时（{}秒）", timeout);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
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
                /*
                String timeoutMsg = "MATLAB 鎵ц瓒呮椂锛堣秴杩? + timeout + "绉掞級";
                return MatlabExecutionResultDTO.builder()
                        .success(false)
                        .stdout(stdout)
                        .stderr("MATLAB 执行超时（超过" + timeout + "秒）")
                        .exitCode(null)
                        .durationMs(System.currentTimeMillis() - startedAt)
                        .startedAtEpochMs(startedAt)
                        .build();
                */
            }
            int exitCode = process.exitValue();
            joinCollectorThread(stdoutThread);
            joinCollectorThread(stderrThread);
            String stdoutRaw = stdoutCollector.getContent();
            String stderrRaw = stderrCollector.getContent();

            String stdout = filterMatlabNoise(stdoutRaw);
            String stderr = filterMatlabNoise(stderrRaw);

            boolean success = exitCode == 0;
            if (success) {
                log.info("========== MATLAB 代码执行结果 ==========");
                log.info(stdout);
                log.info("==========================================");
                log.info("MATLAB进程退出码: {}", exitCode);
            } else {
                log.error("========== MATLAB 代码执行错误 ==========");
                if (!stdout.isBlank()) {
                    log.error("stdout:\n{}", stdout);
                }
                if (!stderr.isBlank()) {
                    log.error("stderr:\n{}", stderr);
                }
                log.error("==========================================");
                log.error("MATLAB进程退出码: {}", exitCode);
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
            log.error("IO错误: {}", e.getMessage());
            return MatlabExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr("IO错误: " + e.getMessage())
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } catch (InterruptedException e) {
            log.error("执行被中断: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return MatlabExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr("执行被中断: " + e.getMessage())
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage() == null ? "执行失败" : e.getMessage();
            log.error("执行失败：{}", errorMsg, e);
            return MatlabExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr(errorMsg)
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } finally {
            if (singleInstance) {
                IN_PROGRESS.set(false);
            }
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                log.info("临时脚本文件已清理");
            }
            log.info("MATLAB执行服务处理完成");
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
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\\R", -1)) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.contains("MATLAB is selecting")) continue;
            if (trimmed.startsWith("Warning:")) continue;
            sb.append(line).append("\n");
        }
        return sb.toString();
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
        if ("process".equals(mode)) return false;
        if ("engine".equals(mode)) return MatlabEngineReuseUtil.isEngineAvailable();
        // auto
        return MatlabEngineReuseUtil.isEngineAvailable();
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
