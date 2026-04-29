package com.ruoyi.Xidian.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

public class DdataInfo extends BaseEntity {
    private Integer id;
    private String targetId;
    private String targetType;
    private String targetCategory;
    private String experimentId;
    private String experimentName;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Excel(name = "试验开始时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startTime;
    @Excel(name = "试验地点")
    private String location;
    @Excel(name = "试验内容描述")
    private String contentDesc;
    private Long projectId;
    private String projectName;
    private String dataName;
    private String dataType;
    private String dataFilePath;
    private Long storageFileId;
    private String deviceId;
    private String deviceInfo;
    private Integer sampleFrequency;
    private String workStatus;
    private String extAttr;
    private Integer pageNum;
    private Integer pageSize;
    private Boolean isSimulation;
    private String FileName;
    private String fullPath;

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetCategory(String targetCategory) {
        this.targetCategory = targetCategory;
    }

    public String getTargetCategory() {
        return targetCategory;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    public void setContentDesc(String contentDesc) {
        this.contentDesc = contentDesc;
    }

    public String getContentDesc() {
        return contentDesc;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataFilePath(String dataFilePath) {
        this.dataFilePath = dataFilePath;
    }

    public String getDataFilePath() {
        return dataFilePath;
    }

    public void setStorageFileId(Long storageFileId) {
        this.storageFileId = storageFileId;
    }

    public Long getStorageFileId() {
        return storageFileId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setSampleFrequency(Integer sampleFrequency) {
        this.sampleFrequency = sampleFrequency;
    }

    public Integer getSampleFrequency() {
        return sampleFrequency;
    }

    public void setWorkStatus(String workStatus) {
        this.workStatus = workStatus;
    }

    public String getWorkStatus() {
        return workStatus;
    }

    public void setExtAttr(String extAttr) {
        this.extAttr = extAttr;
    }

    public String getExtAttr() {
        return extAttr;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setIsSimulation(Boolean isSimulation) {
        this.isSimulation = isSimulation;
    }

    public Boolean getIsSimulation() {
        return isSimulation;
    }

    public void setFileName(String fileName) {
        this.FileName = fileName;
    }

    public String getFileName() {
        return FileName;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getFullPath() {
        return fullPath;
    }
}
