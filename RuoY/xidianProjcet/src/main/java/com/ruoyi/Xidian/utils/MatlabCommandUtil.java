package com.ruoyi.Xidian.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MatlabCommandUtil {

    public static String getMatlabCommand(String defaultCommand) {
        File resolvedDefault = resolveMatlabExecutable(defaultCommand);
        if (resolvedDefault != null) {
            return resolvedDefault.getAbsolutePath();
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String[] candidates = os.contains("win")
                ? new String[]{"matlab", "matlab.exe"}
                : new String[]{"matlab", "/usr/local/bin/matlab", "/Applications/MATLAB_R2023b.app/bin/matlab"};

        for (String candidate : candidates) {
            File resolved = resolveMatlabExecutable(candidate);
            if (resolved != null) {
                return resolved.getAbsolutePath();
            }
        }

        log.warn("No executable MATLAB command was found, keeping configured value: {}", defaultCommand);
        return defaultCommand;
    }

    public static File resolveMatlabExecutable(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }

        File direct = new File(command);
        if (direct.isFile() && direct.canExecute()) {
            try {
                return direct.getCanonicalFile();
            } catch (Exception e) {
                return direct.getAbsoluteFile();
            }
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            Process process = os.contains("win")
                    ? Runtime.getRuntime().exec(new String[]{"where", command})
                    : Runtime.getRuntime().exec(new String[]{"which", command});

            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null || line.trim().isEmpty()) {
                    return null;
                }

                File resolved = new File(line.trim());
                if (!resolved.isFile()) {
                    return null;
                }

                try {
                    return resolved.getCanonicalFile();
                } catch (Exception e) {
                    return resolved.getAbsoluteFile();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve MATLAB command: {}", command, e);
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
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                log.info("MATLAB validation output: {}", output.toString().trim());
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to validate MATLAB environment", e);
            return false;
        }
    }
}
