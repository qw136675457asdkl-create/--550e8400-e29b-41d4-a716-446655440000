package com.ruoyi.Xidian.domain.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatlabTaskControlResultDTO {
    private boolean success;
    private boolean cancelled;
    private String message;
}
