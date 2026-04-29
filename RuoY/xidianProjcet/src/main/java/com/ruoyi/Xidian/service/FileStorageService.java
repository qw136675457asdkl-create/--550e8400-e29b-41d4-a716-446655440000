package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.config.MinioProperties;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUtils;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Slf4j
@Service
public class FileStorageService {

    private static final int DEFAULT_EXPIRE_SECONDS = 600;
    private static final int MAX_EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public FileStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public String upload(MultipartFile file,Long userId){
        try{
            if(file.isEmpty()){
                throw new RuntimeException("文件不能为空");
            }
            ensureBucketExists(getRequiredBucket());
            String originName = file.getOriginalFilename();
            String extension = getExtension(originName);
            String objectName = LocalDate.now() + "/user-" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败",e);
            throw new RuntimeException("上传文件失败");
        }
    }
    //上传服务器上的文件
    public String uploadLocalFile(String localFilePath, Long userId) {
        if (localFilePath == null || localFilePath.trim().isEmpty()) {
            throw new RuntimeException("本地文件路径不能为空");
        }

        Path path = Paths.get(localFilePath).normalize();

        if (!Files.exists(path)) {
            throw new RuntimeException("本地文件不存在：" + localFilePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new RuntimeException("指定路径不是文件：" + localFilePath);
        }

        if (!Files.isReadable(path)) {
            throw new RuntimeException("本地文件不可读：" + localFilePath);
        }

        try {
            String bucketName = minioProperties.getBucket();

            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
            }

            File file = path.toFile();
            String originalFileName = file.getName();

            String objectName = buildObjectName(originalFileName, userId);

            String contentType = Files.probeContentType(path);
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "application/octet-stream";
            }

            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .filename(path.toString())
                            .contentType(contentType)
                            .build()
            );

            log.info("本地文件上传 MinIO 成功, localFilePath={}, objectName={}",
                    localFilePath, objectName);

            return objectName;

        } catch (Exception e) {
            log.error("本地文件上传 MinIO 失败, localFilePath={}", localFilePath, e);
            throw new RuntimeException("本地文件上传 MinIO 失败：" + e.getMessage(), e);
        }
    }

    private String buildObjectName(String originalFileName, Long userId) {
        String datePath = java.time.LocalDate.now().toString().replace("-", "/");

        String suffix = "";
        int index = originalFileName.lastIndexOf(".");
        if (index >= 0) {
            suffix = originalFileName.substring(index);
        }

        return "simulation/" + userId + "/" + datePath + "/" + UUID.randomUUID() + suffix;
    }

    public void delete(String objectName){
        if(objectName == null || objectName.trim().isEmpty()) return;
        try{
            minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件删除失败，objectName={}", objectName, e);
            throw new RuntimeException("MinIO 文件删除失败：" + objectName, e);
        }
    }

    public void deleteBath(List<String> objectNames){
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        List<DeleteObject> deleteObjects = objectNames.stream()
                .filter(name -> name != null && !name.trim().isEmpty())
                .map(DeleteObject::new)
                .collect(Collectors.toList());
        if(deleteObjects.isEmpty())
            return;
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .objects(deleteObjects)
                        .build()
        );
        for (Result<DeleteError> result : results) {
            try {
                DeleteError error = result.get();
                log.error("MinIO 批量删除失败，objectName={}, message={}",
                        error.objectName(), error.message());
                throw new RuntimeException("MinIO 批量删除失败：" + error.objectName());
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                log.error("MinIO 批量删除结果解析失败", e);
                throw new RuntimeException("MinIO 批量删除失败", e);
            }
        }
    }

    public void download(String objectName, String originalFileName,HttpServletResponse response) {
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new RuntimeException("文件对象名不能为空");
        }

        try (GetObjectResponse inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .build()
        );
             OutputStream outputStream = response.getOutputStream()) {

            String encodedFileName = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");

            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodedFileName);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.flush();

        } catch (Exception e) {
            log.error("MinIO 文件下载失败，objectName={}", objectName, e);
            throw new RuntimeException("文件下载失败：" + objectName, e);
        }
    }

    public void preview(String objectName, String originalFileName, String contentType, HttpServletResponse response) {
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new RuntimeException("文件对象名不能为空");
        }

        try (GetObjectResponse inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .build()
        );
             OutputStream outputStream = response.getOutputStream()) {

            String resolvedFileName = originalFileName;
            if (resolvedFileName == null || resolvedFileName.trim().isEmpty()) {
                resolvedFileName = objectName;
            }

            String resolvedContentType = contentType;
            if (resolvedContentType == null || resolvedContentType.trim().isEmpty()) {
                resolvedContentType = URLConnection.guessContentTypeFromName(resolvedFileName);
            }
            if (resolvedContentType == null || resolvedContentType.trim().isEmpty()) {
                resolvedContentType = "application/octet-stream";
            }

            String encodedFileName = URLEncoder.encode(resolvedFileName, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");

            response.setContentType(resolvedContentType);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "inline; filename*=UTF-8''" + encodedFileName);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.flush();
        } catch (Exception e) {
            log.error("MinIO 文件预览失败，objectName={}", objectName, e);
            throw new RuntimeException("文件预览失败：" + objectName, e);
        }
    }

    public Map<String, Object> previewByPage(String objectName, String originalFileName, int pageNum, int pageSize) {
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new RuntimeException("文件对象名不能为空");
        }

        Path tempFile = null;
        try (GetObjectResponse inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .build()
        )) {
            String suffix = getExtension(originalFileName);
            tempFile = Files.createTempFile("business-preview-", suffix.isEmpty() ? ".tmp" : suffix);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return FileUtils.previewFileByPage(tempFile.toString(), pageNum, pageSize);
        } catch (Exception e) {
            log.error("MinIO 文件分页预览失败，objectName={}", objectName, e);
            throw new RuntimeException("文件预览失败：" + objectName, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("删除临时预览文件失败: {}", tempFile, ex);
                }
            }
        }
    }

    public String createPresignedPutUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .expiry(resolveExpireSeconds())
                            .build()
            );
        } catch (Exception ex) {
            throw new ServiceException("生成 MinIO 预签名上传地址失败: " + ex.getMessage());
        }
    }
    public String createPresignedGetUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .expiry(resolveExpireSeconds())
                            .build()
            );
        } catch (Exception ex) {
            throw new ServiceException("生成 MinIO 预签名下载地址失败: " + ex.getMessage());
        }
    }

    private int resolveExpireSeconds() {
        Integer configured = minioProperties.getUploadUrlExpirySeconds();
        int expireSeconds = configured == null ? DEFAULT_EXPIRE_SECONDS : configured;
        if (expireSeconds <= 0 || expireSeconds > MAX_EXPIRE_SECONDS) {
            throw new ServiceException("MinIO 预签名地址有效期配置不合法");
        }
        return expireSeconds;
    }

    private String getExtension(String fileName){
        if(fileName == null || !fileName.contains(".")){
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String getRequiredBucket() {
        String bucket = trimToNull(minioProperties.getBucket());
        if (bucket == null) {
            throw new ServiceException("MinIO bucket 未配置");
        }
        return bucket;
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
}
