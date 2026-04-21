package com.ruoyi.Xidian.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.ArrayList;
import java.util.List;

public class Task extends BaseEntity {
    private Long id;
    private String taskCode;
    private String taskName;
    private Integer projectId;
    private String projectName;
    private String experimentId;
    private String experimentName;
    private String carrierType;
    private String motionModel;
    private Coordinate startCoordinate;
    private Coordinate endCoordinate;
    private String dataCategorySummary;
    private String targetType;
    private String status;
    private String path;
    private List<TaskDataGroup> dataGroups = new ArrayList<>();
    private List<TaskDataGroup> requestDataGroups = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public void setExperimentID(String experimentID) {
        this.experimentId = experimentID;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getCarrierType() {
        return carrierType;
    }

    public void setCarrierType(String carrierType) {
        this.carrierType = carrierType;
    }

    public String getMotionModel() {
        return motionModel;
    }

    public void setMotionModel(String motionModel) {
        this.motionModel = motionModel;
    }

    public Coordinate getStartCoordinate() {
        return startCoordinate;
    }

    public void setStartCoordinate(Coordinate startCoordinate) {
        this.startCoordinate = startCoordinate;
    }

    public Coordinate getEndCoordinate() {
        return endCoordinate;
    }

    public void setEndCoordinate(Coordinate endCoordinate) {
        this.endCoordinate = endCoordinate;
    }

    public String getDataCategorySummary() {
        return dataCategorySummary;
    }

    public void setDataCategorySummary(String dataCategorySummary) {
        this.dataCategorySummary = dataCategorySummary;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<TaskDataGroup> getDataGroups() {
        return dataGroups;
    }

    public void setDataGroups(List<TaskDataGroup> dataGroups) {
        this.dataGroups = dataGroups == null ? new ArrayList<>() : dataGroups;
    }

    public void addDataGroup(TaskDataGroup dataGroup) {
        this.dataGroups.add(dataGroup);
    }

    public List<TaskDataGroup> getRequestDataGroups() {
        return requestDataGroups;
    }

    public void setRequestDataGroups(List<TaskDataGroup> requestDataGroups) {
        this.requestDataGroups = requestDataGroups == null ? new ArrayList<>() : requestDataGroups;
    }
}
