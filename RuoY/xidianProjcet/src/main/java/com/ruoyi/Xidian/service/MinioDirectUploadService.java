package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.config.MinioProperties;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.domain.MdFileStorage;
import com.ruoyi.Xidian.domain.DTO.MinioUploadCompleteRequest;
import com.ruoyi.Xidian.domain.DTO.MinioUploadCompleteResponse;
import com.ruoyi.Xidian.domain.DTO.MinioUploadInitRequest;
import com.ruoyi.Xidian.domain.DTO.MinioUploadInitResponse;
import com.ruoyi.Xidian.domain.DTO.MinioUploadStatusResponse;
import com.ruoyi.Xidian.domain.enums.FileStorageProviderEnum;
import com.ruoyi.Xidian.domain.enums.FileStorageStatusEnum;
import com.ruoyi.Xidian.domain.enums.MinioBusinessTypeEnum;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.mapper.MdFileStorageMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioDirectUploadService {
    private static final int DEFAULT_EXPIRE_SECONDS = 600;
    private static final int MAX_EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    private static final String DEFAULT_OBJECT_PREFIX = "direct_upload";
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> OBJECT_NOT_FOUND_CODES = Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket", "NotFound");

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MdFileStorageMapper mdFileStorageMapper;
    private final DdataMapper ddataMapper;

    public MinioDirectUploadService(MinioClient minioClient,
                                    MinioProperties minioProperties,
                                    MdFileStorageMapper mdFileStorageMapper,
                                    DdataMapper ddataMapper) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.mdFileStorageMapper = mdFileStorageMapper;
        this.ddataMapper = ddataMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public MinioUploadInitResponse createPresignedUpload(MinioUploadInitRequest request) {
        Long userId = SecurityUtils.getUserId();
        String username = resolveCurrentUsername();
        String bucket = getRequiredBucket();
        ensureBucketExists(bucket);

        String businessType = trimToNull(request.getBusinessType());
        String businessId = trimToNull(request.getBusinessId());
        String originalFileName = extractFileName(request.getFileName());
        String objectName = buildObjectName(userId, request.getFileName(), businessType);
        String uploadUrl = createPresignedPutUrl(bucket, objectName);
        MdFileStorage fileStorage = buildInitFileStorage(
                bucket,
                objectName,
                originalFileName,
                trimToNull(request.getContentType()),
                request.getFileSize(),
                businessType,
                businessId,
                userId,
                username
        );
        mdFileStorageMapper.insertMdFileStorage(fileStorage);

        MinioUploadInitResponse response = new MinioUploadInitResponse();
        response.setBucket(bucket);
        response.setObjectName(objectName);
        response.setUploadUrl(uploadUrl);
        response.setUploadMethod(Method.PUT.name());
        response.setExpireSeconds(resolveExpireSeconds());
        response.setOriginalFileName(originalFileName);
        response.setContentType(trimToNull(request.getContentType()));
        response.setFileSize(request.getFileSize());
        response.setBusinessType(businessType);
        response.setBusinessId(businessId);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public MinioUploadCompleteResponse completeUpload(MinioUploadCompleteRequest request) {
        Long userId = SecurityUtils.getUserId();
        String username = resolveCurrentUsername();
        String bucket = getRequiredBucket();
        String objectName = normalizeObjectName(request.getObjectName());
        validateObjectAccess(userId, objectName);

        StatObjectResponse statObjectResponse = statObjectIfExists(bucket, objectName);
        if (statObjectResponse == null) {
            throw new ServiceException("上传文件不存在，请确认前端已经完成 PUT 上传");
        }

        String requestBusinessType = trimToNull(request.getBusinessType());
        String requestBusinessId = trimToNull(request.getBusinessId());
        MdFileStorage fileStorage = mdFileStorageMapper.selectByBucketAndObjectName(bucket, objectName);
        if (fileStorage == null) {
            fileStorage = buildInitFileStorage(
                    bucket,
                    objectName,
                    resolveOriginalFileName(request.getOriginalFileName(), objectName),
                    null,
                    statObjectResponse.size(),
                    requestBusinessType,
                    requestBusinessId,
                    userId,
                    username
            );
            fileStorage.setEtag(statObjectResponse.etag());
            fileStorage.setUploadStatus(FileStorageStatusEnum.UPLOADED.getCode());
            fileStorage.setCompletedTime(new Date());
            mdFileStorageMapper.insertMdFileStorage(fileStorage);
        }

        refreshFileStorageAfterUpload(fileStorage, request, statObjectResponse, userId, username);
        boolean dbUpdated = bindBusinessRecord(fileStorage, username);
        fileStorage.setUploadStatus((dbUpdated ? FileStorageStatusEnum.BOUND : FileStorageStatusEnum.UPLOADED).getCode());
        fileStorage.setUpdateBy(username);
        fileStorage.setUpdateTime(new Date());
        mdFileStorageMapper.updateMdFileStorage(fileStorage);

        MinioUploadCompleteResponse response = new MinioUploadCompleteResponse();
        response.setBucket(bucket);
        response.setObjectName(objectName);
        response.setExists(true);
        response.setStatus(fileStorage.getUploadStatus());
        response.setSize(statObjectResponse.size());
        response.setEtag(statObjectResponse.etag());
        response.setBusinessType(fileStorage.getBusinessType());
        response.setBusinessId(fileStorage.getBusinessId());
        response.setDbUpdated(dbUpdated);
        return response;
    }

    public MinioUploadStatusResponse queryUploadStatus(String objectName) {
        Long userId = SecurityUtils.getUserId();
        String bucket = getRequiredBucket();
        String normalizedObjectName = normalizeObjectName(objectName);
        validateObjectAccess(userId, normalizedObjectName);

        StatObjectResponse statObjectResponse = statObjectIfExists(bucket, normalizedObjectName);
        MdFileStorage fileStorage = mdFileStorageMapper.selectByBucketAndObjectName(bucket, normalizedObjectName);

        MinioUploadStatusResponse response = new MinioUploadStatusResponse();
        response.setBucket(bucket);
        response.setObjectName(normalizedObjectName);
        response.setExists(statObjectResponse != null);
        response.setStatus(resolveUploadStatus(statObjectResponse != null, fileStorage));
        if (statObjectResponse != null) {
            response.setSize(statObjectResponse.size());
            response.setEtag(statObjectResponse.etag());
        }
        return response;
    }

    private String createPresignedPutUrl(String bucket, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(resolveExpireSeconds())
                            .build()
            );
        } catch (Exception ex) {
            throw new ServiceException("生成 MinIO 预签名上传地址失败: " + ex.getMessage());
        }
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        } catch (Exception ex) {
            throw new ServiceException("初始化 MinIO bucket 失败: " + ex.getMessage());
        }
    }

    private StatObjectResponse statObjectIfExists(String bucket, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (ex.errorResponse() != null && OBJECT_NOT_FOUND_CODES.contains(ex.errorResponse().code())) {
                return null;
            }
            throw new ServiceException("查询 MinIO 对象状态失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new ServiceException("查询 MinIO 对象状态失败: " + ex.getMessage());
        }
    }

    private String buildObjectName(Long userId, String fileName, String businessType) {
        String prefix = resolveObjectPrefix();
        String businessSegment = normalizeBusinessSegment(businessType);
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        String extension = extractExtension(fileName);

        return prefix + "/" + businessSegment + "/" + datePath + "/user-" + userId + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private void validateObjectAccess(Long userId, String objectName) {
        String prefix = resolveObjectPrefix() + "/";
        String userSegment = "/user-" + userId + "/";
        if (!objectName.startsWith(prefix) || !objectName.contains(userSegment)) {
            throw new ServiceException("无权访问该上传对象");
        }
    }

    private MdFileStorage buildInitFileStorage(String bucket,
                                               String objectName,
                                               String originalFileName,
                                               String contentType,
                                               Long fileSize,
                                               String businessType,
                                               String businessId,
                                               Long userId,
                                               String username) {
        Date now = new Date();
        MdFileStorage fileStorage = new MdFileStorage();
        fileStorage.setBusinessType(businessType);
        fileStorage.setBusinessId(businessId);
        fileStorage.setStorageProvider(FileStorageProviderEnum.MINIO.getCode());
        fileStorage.setBucketName(bucket);
        fileStorage.setObjectName(objectName);
        fileStorage.setOriginalFileName(originalFileName);
        fileStorage.setFileExt(extractExtension(originalFileName != null ? originalFileName : objectName));
        fileStorage.setContentType(contentType);
        fileStorage.setFileSize(fileSize);
        fileStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        fileStorage.setUploadUserId(userId);
        fileStorage.setUploadUserName(username);
        fileStorage.setCreateBy(username);
        fileStorage.setCreateTime(now);
        fileStorage.setUpdateBy(username);
        fileStorage.setUpdateTime(now);
        return fileStorage;
    }

    private void refreshFileStorageAfterUpload(MdFileStorage fileStorage,
                                               MinioUploadCompleteRequest request,
                                               StatObjectResponse statObjectResponse,
                                               Long userId,
                                               String username) {
        Date now = new Date();
        String originalFileName = resolveOriginalFileName(request.getOriginalFileName(), fileStorage.getOriginalFileName());
        fileStorage.setBusinessType(firstNonBlank(trimToNull(request.getBusinessType()), fileStorage.getBusinessType()));
        fileStorage.setBusinessId(firstNonBlank(trimToNull(request.getBusinessId()), fileStorage.getBusinessId()));
        fileStorage.setOriginalFileName(originalFileName);
        fileStorage.setFileExt(extractExtension(originalFileName != null ? originalFileName : fileStorage.getObjectName()));
        fileStorage.setFileSize(statObjectResponse.size());
        fileStorage.setEtag(statObjectResponse.etag());
        fileStorage.setUploadUserId(userId);
        fileStorage.setUploadUserName(username);
        fileStorage.setCompletedTime(now);
    }

    private boolean bindBusinessRecord(MdFileStorage fileStorage, String username) {
        MinioBusinessTypeEnum businessTypeEnum = MinioBusinessTypeEnum.fromCode(fileStorage.getBusinessType());
        if (businessTypeEnum == null || !StringUtils.hasText(fileStorage.getBusinessId())) {
            return false;
        }

        switch (businessTypeEnum) {
            case DATA_RELATION:
                Integer relationId = parseDataRelationId(fileStorage.getBusinessId());
                DdataInfo ddataInfo = ddataMapper.selectDdataInfoById(relationId);
                if (ddataInfo == null) {
                    throw new ServiceException("未找到对应的数据文件记录，businessId=" + fileStorage.getBusinessId());
                }
                ddataMapper.updateStorageFileId(relationId, fileStorage.getId());
                fileStorage.setRemark("已绑定 md_data_relation#" + relationId);
                fileStorage.setUpdateBy(username);
                fileStorage.setUpdateTime(new Date());
                return true;
            default:
                return false;
        }
    }

    private Integer parseDataRelationId(String businessId) {
        try {
            return Integer.valueOf(businessId);
        } catch (NumberFormatException ex) {
            throw new ServiceException("DATA_RELATION 类型的 businessId 必须是数字: " + businessId);
        }
    }

    private String resolveUploadStatus(boolean objectExists, MdFileStorage fileStorage) {
        if (objectExists) {
            if (fileStorage != null && StringUtils.hasText(fileStorage.getUploadStatus())) {
                FileStorageStatusEnum statusEnum = FileStorageStatusEnum.fromCode(fileStorage.getUploadStatus());
                if (statusEnum == FileStorageStatusEnum.BOUND || statusEnum == FileStorageStatusEnum.UPLOADED) {
                    return statusEnum.getCode();
                }
            }
            return FileStorageStatusEnum.UPLOADED.getCode();
        }

        if (fileStorage != null && StringUtils.hasText(fileStorage.getUploadStatus())) {
            FileStorageStatusEnum statusEnum = FileStorageStatusEnum.fromCode(fileStorage.getUploadStatus());
            if (statusEnum == FileStorageStatusEnum.INIT || statusEnum == FileStorageStatusEnum.FAILED) {
                return statusEnum.getCode();
            }
        }
        return "MISSING";
    }
    
    public String previewUrl(String objectName) {
    try {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket("uploads")
                        .object(objectName)
                        .expiry(10, TimeUnit.MINUTES)
                        .build()
        );
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

    private String resolveOriginalFileName(String preferredName, String fallbackName) {
        return firstNonBlank(trimToNull(preferredName), trimToNull(fallbackName));
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private String resolveCurrentUsername() {
        return trimToNull(SecurityUtils.getUsername());
    }

    private String getRequiredBucket() {
        String bucket = trimToNull(minioProperties.getBucket());
        if (bucket == null) {
            throw new ServiceException("MinIO bucket 未配置");
        }
        return bucket;
    }

    private int resolveExpireSeconds() {
        Integer configured = minioProperties.getUploadUrlExpirySeconds();
        int expireSeconds = configured == null ? DEFAULT_EXPIRE_SECONDS : configured;
        if (expireSeconds <= 0 || expireSeconds > MAX_EXPIRE_SECONDS) {
            throw new ServiceException("MinIO 预签名地址有效期配置不合法");
        }
        return expireSeconds;
    }

    private String resolveObjectPrefix() {
        String prefix = trimToNull(minioProperties.getObjectPrefix());
        if (prefix == null) {
            return DEFAULT_OBJECT_PREFIX;
        }
        String normalized = prefix.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : DEFAULT_OBJECT_PREFIX;
    }

    private String normalizeBusinessSegment(String businessType) {
        String normalized = trimToNull(businessType);
        if (normalized == null) {
            return "default";
        }

        String safeValue = normalized.toLowerCase(Locale.ROOT)
                .replace("\\", "-")
                .replace("/", "-")
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-{2,}", "-");

        safeValue = trimEdgeDash(safeValue);
        return StringUtils.hasText(safeValue) ? safeValue : "default";
    }

    private String normalizeObjectName(String objectName) {
        String normalized = trimToNull(objectName);
        if (normalized == null) {
            throw new ServiceException("objectName 不能为空");
        }
        return normalized.replace("\\", "/");
    }

    private String extractFileName(String fileName) {
        String normalized = normalizeObjectName(fileName);
        int lastSlashIndex = normalized.lastIndexOf('/');
        return lastSlashIndex >= 0 ? normalized.substring(lastSlashIndex + 1) : normalized;
    }

    private String extractExtension(String fileName) {
        String normalizedFileName = extractFileName(fileName);
        int lastDotIndex = normalizedFileName.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == normalizedFileName.length() - 1) {
            return "";
        }
        return normalizedFileName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    private String trimEdgeDash(String value) {
        String result = value;
        while (result.startsWith("-")) {
            result = result.substring(1);
        }
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
