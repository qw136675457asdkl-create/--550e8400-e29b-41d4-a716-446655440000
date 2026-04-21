package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;

public interface MatlabExecutionService {
    /**
     * 执行MATLAB代码
     */
    MatlabExecutionResultDTO executeMatlab(String code);
}