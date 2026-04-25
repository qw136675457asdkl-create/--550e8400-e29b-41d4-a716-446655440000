package com.ruoyi.Xidian.domain.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatlabExecutionResultDTO {
    private boolean success;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long durationMs;
    private Long startedAtEpochMs;
}

