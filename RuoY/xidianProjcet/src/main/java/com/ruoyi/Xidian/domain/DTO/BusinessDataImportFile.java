package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BusinessDataImportFile {
    @NotBlank(message = "bucket is required")
    private String bucket;

    @NotBlank(message = "objectName is required")
    private String objectName;

    private String originalFileName;

    private String relativePath;

    private String contentType;

    private Long fileSize;

    private String etag;
}
