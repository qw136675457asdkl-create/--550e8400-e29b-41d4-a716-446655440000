package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabTaskControlResultDTO;

public interface MatlabExecutionService {

    MatlabExecutionResultDTO executeMatlab(String code);

    MatlabTaskControlResultDTO cancelCurrentTask();
}
