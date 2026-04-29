package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MinioUploadCompleteRequest {
    @NotBlank(message = "objectName 不能为空")
    private String objectName;

    private String originalFileName;

    private String businessType;

    private String businessId;
}
