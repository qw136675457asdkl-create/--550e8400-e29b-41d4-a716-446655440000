package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class MinioUploadInitResponse {
    private String bucket;
    private String objectName;
    private String uploadUrl;
    private String uploadMethod;
    private Integer expireSeconds;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String businessType;
    private String businessId;
}
