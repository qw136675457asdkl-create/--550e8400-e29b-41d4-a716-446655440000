package com.ruoyi.Xidian.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PythonCommandUtil {

    /**
     * 获取可用的 Python 命令（返回完整路径）
     */
    public static String getPythonCommand(String defaultCommand) {
        log.info("开始检测 Python 命令：{}", defaultCommand);

        // 直接使用配置的命令，验证其是否可用
        String pythonPath = findPythonPath(defaultCommand);
        if (pythonPath != null) {
            log.info("Python 命令可用：{} -> {}", defaultCommand, pythonPath);
            return pythonPath;
        }

        // 如果配置的命令不可用，抛出异常
        String errorMsg = String.format(
                "配置的 Python 命令 '%s' 不可用。请检查：\n" +
                        "1. Python 是否正确安装\n" +
                        "2. 路径是否正确\n" +
                        "3. 是否有执行权限",
                defaultCommand
        );
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }

    /**
     * 查找 Python 可执行文件的完整路径
     */
    private static String findPythonPath(String command) {
        try {
            // 如果命令已经是完整路径（包含盘符或斜杠），直接验证
            if (command.contains(":") || command.contains("/") || command.contains("\\")) {
                log.debug("检测到完整路径格式：{}", command);

                File file = new File(command);
                if (file.exists() && file.canExecute()) {
                    log.debug("文件存在且可执行：{}", command);
                    return command;
                } else {
                    log.warn("文件不存在或不可执行：{}", command);
                    return null;
                }
            }

            // 否则使用 where/which 命令查找
            String os = System.getProperty("os.name").toLowerCase();
            Process process;

            if (os.contains("win")) {
                log.debug("检测到 Windows 系统，使用 'where' 命令检查：{}", command);
                process = Runtime.getRuntime().exec(new String[]{"where", command});
            } else {
                log.debug("检测到类 Unix 系统，使用 'which' 命令检查：{}", command);
                process = Runtime.getRuntime().exec(new String[]{"which", command});
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.debug("命令 {} 检查超时", command);
                return null;
            }

            if (process.exitValue() == 0) {
                // 读取输出，获取完整路径
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String path = reader.readLine();
                    if (path != null && !path.trim().isEmpty()) {
                        log.debug("命令 {} 的路径：{}", command, path);

                        // Windows 下可能需要处理路径中的空格
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            if (path.contains(" ")) {
                                path = "\"" + path + "\"";
                            }
                        }

                        return path.trim();
                    }
                }
            }

            log.debug("命令 {} 不可用", command);
            return null;
        } catch (Exception e) {
            log.debug("检查命令 {} 时发生异常：{}", command, e.getMessage(), e);
            return null;
        }
    }
}
