//package com.hhh.service.impl;
//
//
//import com.hhh.service.PythonExecutionService;
//import com.hhh.util.PythonCommandUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.io.*;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//public class PythonExecutionServiceImpl implements PythonExecutionService {
//
//    @Value("${python.command:python3}")
//    private String pythonCommand;
//
//    @Value("${python.timeout:10}")
//    private int timeout;
//
//    @Override
//    public void executePython(String code) {
//        File tempFile = null;
//
//        try {
//            log.info("Python执行服务开始处理代码");
//
//            // 1. 创建临时文件
//            tempFile = File.createTempFile("code_", ".py");
//            tempFile.deleteOnExit();
//            log.info("创建临时文件: {}", tempFile.getAbsolutePath());
//
//            // 2. 写入代码到临时文件
//            try (FileWriter writer = new FileWriter(tempFile)) {
//                writer.write(code);
//            }
//            log.info("代码已写入临时文件");
//
//            // 3. 获取Python命令
//            String command = PythonCommandUtil.getPythonCommand(pythonCommand);
//            log.info("使用Python命令: {}", command);
//
//            // 4. 执行Python进程
//            ProcessBuilder pb = new ProcessBuilder(command, tempFile.getAbsolutePath());
//            pb.redirectErrorStream(true);
////            pb.directory(tempFile.getParentFile());
//            log.info("启动Python进程执行代码...");
//            Process process = pb.start();
//
//            // 5. 等待执行完成（带超时）
//            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
//
//            if (!completed) {
//                log.error("代码执行超时（{}秒）", timeout);
//                process.destroyForcibly();
//                return;
//            }
//
//            // 6. 读取并输出结果到控制台
//            try (BufferedReader reader = new BufferedReader(
//                    new InputStreamReader(process.getInputStream()))) {
//
//                log.info("========== Python代码执行结果 ==========");
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    System.out.println(line);  // 直接输出到控制台
//                }
//
//                int exitCode = process.exitValue();
//                log.info("==========================================");
//                log.info("进程退出码: {}", exitCode);
//
//                if (exitCode != 0) {
//                    log.error("Python进程异常退出");
//                }
//            }
//
//        } catch (IOException e) {
//            log.error("IO错误: {}", e.getMessage());
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            log.error("执行被中断: {}", e.getMessage());
//            Thread.currentThread().interrupt();
//        } finally {
//            // 清理临时文件
//            if (tempFile != null && tempFile.exists()) {
//                tempFile.delete();
//                log.info("临时文件已清理");
//            }
//            log.info("Python执行服务处理完成");
//        }
//    }
//}


package com.ruoyi.Xidian.service.impl;


import com.ruoyi.Xidian.domain.DTO.PythonExecutionResultDTO;
import com.ruoyi.Xidian.service.PythonExecutionService;
import com.ruoyi.Xidian.utils.PythonCommandUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PythonExecutionServiceImpl implements PythonExecutionService {

    @Value("${python.command:python3}")
    private String pythonCommand;

    @Value("${python.timeout:10}")
    private int timeout;

    @Override
    public PythonExecutionResultDTO executePython(String code) {
        File tempFile = null;
        long startedAt = System.currentTimeMillis();

        try {
            log.info("Python 执行服务开始处理代码");

            if (code == null || code.trim().isEmpty()) {
                throw new IllegalArgumentException("Python 代码不能为空");
            }

            // 1. 创建临时文件
            tempFile = File.createTempFile("code_", ".py");
            tempFile.deleteOnExit();
            log.info("创建临时文件：{}", tempFile.getAbsolutePath());

            // 2. 写入代码到临时文件
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(code);
            }
            log.info("代码已写入临时文件");

            // 3. 获取 Python 命令
            String command = PythonCommandUtil.getPythonCommand(pythonCommand);
            log.info("使用 Python 命令：{}", command);

            // 4. 执行 Python 进程
            ProcessBuilder pb = new ProcessBuilder(command, tempFile.getAbsolutePath());
            pb.redirectErrorStream(false);
            pb.directory(tempFile.getParentFile());

            log.info("启动 Python 进程执行代码...");
            Process process = pb.start();

            // 5. 等待执行完成（带超时）
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!completed) {
                log.error("代码执行超时（{}秒）", timeout);
                process.destroyForcibly();
                return PythonExecutionResultDTO.builder()
                        .success(false)
                        .stdout("")
                        .stderr("代码执行超时（超过" + timeout + "秒）")
                        .exitCode(null)
                        .durationMs(System.currentTimeMillis() - startedAt)
                        .startedAtEpochMs(startedAt)
                        .build();
            }

            // 6. 先检查退出码
            int exitCode = process.exitValue();

            Charset charset = Charset.defaultCharset();
            String stdout = readAll(process.getInputStream(), charset);
            String stderr = readAll(process.getErrorStream(), charset);

            boolean success = exitCode == 0;
            if (success) {
                log.info("========== Python 代码执行结果 ==========");
                log.info(stdout);
                log.info("==========================================");
                log.info("进程退出码：{}", exitCode);
            } else {
                log.error("========== Python 代码执行错误 ==========");
                if (!stdout.isBlank()) {
                    log.error("stdout:\n{}", stdout);
                }
                if (!stderr.isBlank()) {
                    log.error("stderr:\n{}", stderr);
                }
                log.error("==========================================");
                log.error("进程退出码：{}", exitCode);
            }

            return PythonExecutionResultDTO.builder()
                    .success(success)
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();

        } catch (IOException e) {
            String errorMsg = "IO 错误：" + e.getMessage();
            log.error(errorMsg, e);
            return PythonExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr(errorMsg)
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } catch (InterruptedException e) {
            String errorMsg = "执行被中断：" + e.getMessage();
            log.error(errorMsg, e);
            Thread.currentThread().interrupt();
            return PythonExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr(errorMsg)
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage() == null ? "执行失败" : e.getMessage();
            log.error("执行失败：{}", errorMsg, e);
            return PythonExecutionResultDTO.builder()
                    .success(false)
                    .stdout("")
                    .stderr(errorMsg)
                    .exitCode(null)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .startedAtEpochMs(startedAt)
                    .build();
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                log.info("临时文件已清理");
            }
            log.info("Python 执行服务处理完成");
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
}




//package com.hhh.service.impl;
//
//
//import com.hhh.service.PythonExecutionService;
//import com.hhh.util.PythonCommandUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.io.*;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//public class PythonExecutionServiceImpl implements PythonExecutionService {
//
//    @Value("${python.command:python}")
//    private String pythonCommand;
//
//    @Value("${python.timeout:10}")
//    private int timeout;
//
//    @Override
//    public void executePython(String code) {
//        File tempFile = null;
//
//        try {
//            log.info("Python 执行服务开始处理代码");
//
//            if (code == null || code.trim().isEmpty()) {
//                throw new IllegalArgumentException("Python 代码不能为空");
//            }
//
//            // 1. 创建临时文件
//            tempFile = File.createTempFile("code_", ".py");
//            tempFile.deleteOnExit();
//            log.info("创建临时文件：{}", tempFile.getAbsolutePath());
//
//            // 2. 写入代码到临时文件
//            try (FileWriter writer = new FileWriter(tempFile)) {
//                writer.write(code);
//            }
//            log.info("代码已写入临时文件");
//
//            // 3. 获取 Python 命令（已经是完整路径）
//            String command = PythonCommandUtil.getPythonCommand(pythonCommand);
//            log.info("使用 Python 命令：{}", command);
//
//            // 4. 构建进程命令（使用 List 避免空格问题）
//            List<String> commands = new ArrayList<>();
//
//            // 如果命令被引号包裹，去掉引号
//            if (command.startsWith("\"") && command.endsWith("\"")) {
//                commands.add(command.substring(1, command.length() - 1));
//            } else {
//                commands.add(command);
//            }
//
//            commands.add(tempFile.getAbsolutePath());
//
//            log.info("完整的进程命令：{}", String.join(" ", commands));
//
//            ProcessBuilder pb = new ProcessBuilder(commands);
//            pb.redirectErrorStream(true);
//            pb.directory(tempFile.getParentFile());
//
//            log.info("启动 Python 进程执行代码...");
//            Process process = pb.start();
//
//            // 5. 等待执行完成（带超时）
//            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
//
//            if (!completed) {
//                log.error("代码执行超时（{}秒）", timeout);
//                process.destroyForcibly();
//                throw new RuntimeException("代码执行超时（超过" + timeout + "秒）");
//            }
//
//            // 6. 先检查退出码
//            int exitCode = process.exitValue();
//
//            // 7. 读取所有输出（包括标准输出和错误输出）
//            StringBuilder outputBuilder = new StringBuilder();
//            try (BufferedReader reader = new BufferedReader(
//                    new InputStreamReader(process.getInputStream()))) {
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    outputBuilder.append(line).append("\n");
//                }
//            }
//
//            String output = outputBuilder.toString();
//
//            // 8. 根据退出码处理输出
//            if (exitCode != 0) {
//                // 错误输出，使用 ERROR 级别日志
//                log.error("========== Python 代码执行错误 ==========");
//                log.error(output);
//                log.error("==========================================");
//                log.error("进程退出码：{}", exitCode);
//
//                String errorMsg = String.format(
//                        "Python 进程异常退出（退出码：%d）。错误详情：\n%s",
//                        exitCode,
//                        output.trim()
//                );
//                throw new RuntimeException(errorMsg);
//            } else {
//                // 正常输出
//                log.info("========== Python 代码执行结果 ==========");
//                log.info(output);
//                log.info("==========================================");
//                log.info("进程退出码：{}", exitCode);
//                System.out.println(output);
//            }
//
//            log.info("Python 代码执行成功");
//
//        } catch (IOException e) {
//            String errorMsg = "IO 错误：" + e.getMessage();
//            log.error(errorMsg, e);
//            throw new RuntimeException(errorMsg, e);
//        } catch (InterruptedException e) {
//            String errorMsg = "执行被中断：" + e.getMessage();
//            log.error(errorMsg, e);
//            Thread.currentThread().interrupt();
//            throw new RuntimeException(errorMsg, e);
//        } finally {
//            // 清理临时文件
//            if (tempFile != null && tempFile.exists()) {
//                tempFile.delete();
//                log.info("临时文件已清理");
//            }
//            log.info("Python 执行服务处理完成");
//        }
//    }
//}
