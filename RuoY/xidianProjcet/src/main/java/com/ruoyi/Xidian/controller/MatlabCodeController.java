package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.service.MatlabCodeExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/matlab")
@CrossOrigin(origins = "*")
public class MatlabCodeController {

    @Autowired
    private MatlabCodeExecutionService executionService;

    @PostMapping("/execute")
    public MatlabExecutionResultDTO executeCode(@RequestBody MatlabCodeRequestDTO request) {
        log.info("==========================================");
        log.info("收到MATLAB代码执行请求");
        log.info("代码内容：\n{}", request.getCode());
        log.info("==========================================");

        // 执行代码，结果会输出到控制台
        return executionService.executeCode(request);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "MATLAB执行器服务正常运行中";
    }
}
