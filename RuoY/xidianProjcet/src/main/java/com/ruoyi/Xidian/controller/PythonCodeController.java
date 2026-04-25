package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DTO.PythonCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.PythonExecutionResultDTO;
import com.ruoyi.Xidian.service.PythonCodeExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/python")
@CrossOrigin(origins = "*")
public class PythonCodeController {

    @Autowired
    private PythonCodeExecutionService executionService;

    /**
     * 执行代码接口 - 结果输出到控制台
     */
    @PreAuthorize("@ss.hasPermi('system:machineLearning:execute')")
    @PostMapping("/execute")
    public PythonExecutionResultDTO executeCode(@RequestBody PythonCodeRequestDTO request) {
        log.info("========== 收到新的代码执行请求 ==========");
        log.info("代码内容：\n{}", request.getCode());
        log.info("==========================================");

        // 执行代码（结果会输出到控制台）
        return executionService.executeCode(request);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public String healthCheck() {
        return "服务正常运行中";
    }
}
