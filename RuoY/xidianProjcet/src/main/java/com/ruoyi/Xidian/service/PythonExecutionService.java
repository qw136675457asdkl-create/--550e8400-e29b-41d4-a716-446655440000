package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.PythonExecutionResultDTO;

public interface PythonExecutionService {
    /**
     * 执行Python代码
     */
    PythonExecutionResultDTO executePython(String code);
}