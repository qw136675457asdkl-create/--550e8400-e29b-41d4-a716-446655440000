package com.ruoyi.Xidian.domain.DTO;

import com.ruoyi.Xidian.domain.Attitude;
import com.ruoyi.Xidian.domain.Coordinate;
import com.ruoyi.Xidian.domain.RandomSeeds;
import com.ruoyi.Xidian.domain.Vector3;

import java.util.List;

public class TaskCreateRequest {
    private String taskName;
    private Long projectId;
    private String experimentId;
    private Long testId;

    private String carrierType;
    private String motionModel;

    private Coordinate startCoordinate;
    private Coordinate endCoordinate;

    private List<TaskDataGroupDTO> dataGroups;

    // getter/setter
    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
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

    public List<TaskDataGroupDTO> getDataGroups() {
        return dataGroups;
    }

    public void setDataGroups(List<TaskDataGroupDTO> dataGroups) {
        this.dataGroups = dataGroups;
    }
}
