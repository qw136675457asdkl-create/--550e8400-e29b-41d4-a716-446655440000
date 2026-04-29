package com.ruoyi.Xidian.domain;

import lombok.Data;

@Data
public class UploadedFileInfo {
    private String objectName;
    private String originalFilename;
    private String contentType;
    private Long size;
}