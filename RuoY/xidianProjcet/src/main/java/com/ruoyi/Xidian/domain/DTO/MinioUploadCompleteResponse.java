package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class MinioUploadCompleteResponse {
    private String bucket;
    private String objectName;
    private boolean exists;
    private String status;
    private Long size;
    private String etag;
    private String businessType;
    private String businessId;
    private boolean dbUpdated;
}
