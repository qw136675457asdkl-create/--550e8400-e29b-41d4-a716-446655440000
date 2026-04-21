package com.ruoyi.Xidian.service.impl;


import com.ruoyi.Xidian.domain.DTO.PythonCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.PythonExecutionResultDTO;
import com.ruoyi.Xidian.service.PythonCodeExecutionService;
import com.ruoyi.Xidian.service.PythonExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PythonCodeExecutionServiceImpl implements PythonCodeExecutionService {

    @Autowired
    private PythonExecutionService pythonExecutionService;

    @Override
    public PythonExecutionResultDTO executeCode(PythonCodeRequestDTO request) {
        log.info("业务层开始处理代码执行请求");

        // 直接调用Python执行服务
        PythonExecutionResultDTO result = pythonExecutionService.executePython(request.getCode());

        log.info("业务层代码执行处理完成");
        return result;
    }
}