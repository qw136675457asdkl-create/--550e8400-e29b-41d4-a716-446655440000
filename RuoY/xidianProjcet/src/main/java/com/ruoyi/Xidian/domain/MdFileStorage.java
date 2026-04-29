package com.ruoyi.Xidian.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class MdFileStorage extends BaseEntity {
    private Long id;
    private String businessType;
    private String businessId;
    private String storageProvider;
    private String bucketName;
    private String objectName;
    private String originalFileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String uploadStatus;
    private Long uploadUserId;
    private String uploadUserName;
    private Date completedTime;
}
