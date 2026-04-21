package com.ruoyi.Xidian.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MatlabCommandUtil {

    /**
     * 获取可用的MATLAB命令
     */
    public static String getMatlabCommand(String defaultCommand) {
        // 不同操作系统MATLAB命令可能不同
        String os = System.getProperty("os.name").toLowerCase();

        // 尝试使用配置的命令
        if (isCommandAvailable(defaultCommand)) {
            return defaultCommand;
        }

        // 根据操作系统尝试不同的MATLAB命令
        if (os.contains("win")) {
            // Windows系统
            String[] winCommands = {"matlab", "matlab.exe"};
            for (String cmd : winCommands) {
                if (isCommandAvailable(cmd)) {
                    return cmd;
                }
            }
        } else {
            // Linux/Mac系统
            String[] nixCommands = {"matlab", "/usr/local/bin/matlab", "/Applications/MATLAB_R2023b.app/bin/matlab"};
            for (String cmd : nixCommands) {
                if (isCommandAvailable(cmd)) {
                    return cmd;
                }
            }
        }

        log.warn("未找到可用的MATLAB命令，使用默认命令: {}", defaultCommand);
        return defaultCommand;
    }

    public static File resolveMatlabExecutable(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }

        File direct = new File(command);
        if (direct.isFile()) {
            return direct.getAbsoluteFile();
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec(new String[]{"where", command});
            } else {
                process = Runtime.getRuntime().exec(new String[]{"which", command});
            }

            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed || process.exitValue() != 0) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null || line.trim().isEmpty()) {
                    return null;
                }
                File resolved = new File(line.trim());
                return resolved.isFile() ? resolved.getAbsoluteFile() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static File resolveMatlabRoot(String command) {
        File executable = resolveMatlabExecutable(command);
        if (executable == null) {
            return null;
        }

        File binDir = executable.getParentFile();
        return binDir == null ? null : binDir.getParentFile();
    }

    /**
     * 检查命令是否可用
     */
    private static boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process process;

            if (os.contains("win")) {
                // Windows使用where命令
                process = Runtime.getRuntime().exec(new String[]{"where", command});
            } else {
                // Linux/Mac使用which命令
                process = Runtime.getRuntime().exec(new String[]{"which", command});
            }

            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证MATLAB环境
     */
//    public static boolean validateMatlab() {
//        String command = getMatlabCommand("matlab");
//        try {
//            Process process = Runtime.getRuntime().exec(new String[]{command, "-version"});
//            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
//
//            if (completed && process.exitValue() == 0) {
//                try (BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(process.getInputStream()))) {
//                    String version = reader.readLine();
//                    log.info("MATLAB环境可用: {}", version);
//                    return true;
//                }
//            }
//        } catch (Exception e) {
//            log.warn("MATLAB环境验证失败", e);
//        }
//        return false;
//    }

    // ... existing code ...

    public static boolean validateMatlab() {
        String command = getMatlabCommand("matlab");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command,
                    "-batch",
                    "disp('MATLAB OK'); exit;"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(15, TimeUnit.SECONDS);

            if (completed && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    log.info("MATLAB 环境可用：{}", output.toString().trim());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("MATLAB 环境验证失败", e);
        }
        return false;
    }
}

