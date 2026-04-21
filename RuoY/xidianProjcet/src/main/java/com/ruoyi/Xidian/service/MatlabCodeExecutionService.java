package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;

public interface MatlabCodeExecutionService {
    /**
     * 执行代码
     */
    MatlabExecutionResultDTO executeCode(MatlabCodeRequestDTO request);
}