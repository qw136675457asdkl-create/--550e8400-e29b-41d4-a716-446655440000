package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabTaskControlResultDTO;
import com.ruoyi.Xidian.service.MatlabCodeExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/matlab")
@CrossOrigin(origins = "*")
public class MatlabCodeController {

    @Autowired
    private MatlabCodeExecutionService executionService;

    @PreAuthorize("@ss.hasPermi('system:machineLearning:execute')")
    @PostMapping("/execute")
    public MatlabExecutionResultDTO executeCode(@RequestBody MatlabCodeRequestDTO request) {
        log.info("Received MATLAB execution request");
        return executionService.executeCode(request);
    }

    @PreAuthorize("@ss.hasPermi('system:machineLearning:execute')")
    @PostMapping("/cancel")
    public MatlabTaskControlResultDTO cancelCurrentTask() {
        log.info("Received MATLAB cancellation request");
        return executionService.cancelCurrentTask();
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "MATLAB executor is running";
    }
}
