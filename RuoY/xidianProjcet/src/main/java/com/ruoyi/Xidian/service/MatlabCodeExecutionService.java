package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabTaskControlResultDTO;

public interface MatlabCodeExecutionService {

    MatlabExecutionResultDTO executeCode(MatlabCodeRequestDTO request);

    MatlabTaskControlResultDTO cancelCurrentTask();
}
