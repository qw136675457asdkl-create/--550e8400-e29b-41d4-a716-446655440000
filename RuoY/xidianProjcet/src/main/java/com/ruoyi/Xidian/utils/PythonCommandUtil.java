package com.ruoyi.Xidian.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PythonCommandUtil {

    public static String getPythonCommand(String defaultCommand) {
        log.info("Checking Python command: {}", defaultCommand);

        String pythonPath = findPythonPath(defaultCommand);
        if (pythonPath != null) {
            log.info("Resolved Python command: {} -> {}", defaultCommand, pythonPath);
            return pythonPath;
        }

        String errorMsg = String.format(
                "Configured Python command '%s' is not executable. Please check the install path and permissions.",
                defaultCommand
        );
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }

    private static String findPythonPath(String command) {
        try {
            if (command == null || command.trim().isEmpty()) {
                return null;
            }

            if (command.contains(":") || command.contains("/") || command.contains("\\")) {
                File file = new File(command);
                if (file.isFile() && file.canExecute()) {
                    return file.getCanonicalPath();
                }
                return null;
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            Process process = os.contains("win")
                    ? Runtime.getRuntime().exec(new String[]{"where", command})
                    : Runtime.getRuntime().exec(new String[]{"which", command});

            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String path = reader.readLine();
                if (path == null || path.trim().isEmpty()) {
                    return null;
                }

                File resolved = new File(path.trim());
                return resolved.isFile() ? resolved.getCanonicalPath() : path.trim();
            }
        } catch (Exception e) {
            log.debug("Failed to resolve Python command: {}", command, e);
            return null;
        }
    }
}
