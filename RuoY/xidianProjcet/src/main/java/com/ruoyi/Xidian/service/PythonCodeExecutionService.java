package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.PythonCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.PythonExecutionResultDTO;

public interface PythonCodeExecutionService {
    /**
     * 执行代码
     */
    PythonExecutionResultDTO executeCode(PythonCodeRequestDTO request);
}