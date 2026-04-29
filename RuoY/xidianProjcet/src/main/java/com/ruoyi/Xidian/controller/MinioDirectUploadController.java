package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DTO.MinioUploadCompleteRequest;
import com.ruoyi.Xidian.domain.DTO.MinioUploadInitRequest;
import com.ruoyi.Xidian.service.MinioDirectUploadService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/minio/direct-upload")
public class MinioDirectUploadController extends BaseController {
    private final MinioDirectUploadService minioDirectUploadService;

    public MinioDirectUploadController(MinioDirectUploadService minioDirectUploadService) {
        this.minioDirectUploadService = minioDirectUploadService;
    }

    @PostMapping("/initiate")
    public AjaxResult initiateUpload(@Valid @RequestBody MinioUploadInitRequest request) {
        return success(minioDirectUploadService.createPresignedUpload(request));
    }

    @PostMapping("/complete")
    public AjaxResult completeUpload(@Valid @RequestBody MinioUploadCompleteRequest request) {
        return success(minioDirectUploadService.completeUpload(request));
    }

    @GetMapping("/status")
    public AjaxResult queryUploadStatus(@RequestParam String objectName) {
        return success(minioDirectUploadService.queryUploadStatus(objectName));
    }
}
