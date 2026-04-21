package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.MatlabCodeRequestDTO;
import com.ruoyi.Xidian.domain.DTO.MatlabExecutionResultDTO;
import com.ruoyi.Xidian.service.MatlabCodeExecutionService;
import com.ruoyi.Xidian.service.MatlabExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MatlabCodeExecutionServiceImpl implements MatlabCodeExecutionService {

    @Autowired
    private MatlabExecutionService matlabExecutionService;

    @Override
    public MatlabExecutionResultDTO executeCode(MatlabCodeRequestDTO request) {
        log.info("业务层开始处理MATLAB代码");

        // 调用MATLAB执行服务
        MatlabExecutionResultDTO result = matlabExecutionService.executeMatlab(request.getCode());

        log.info("业务层处理完成");
        return result;
    }
}