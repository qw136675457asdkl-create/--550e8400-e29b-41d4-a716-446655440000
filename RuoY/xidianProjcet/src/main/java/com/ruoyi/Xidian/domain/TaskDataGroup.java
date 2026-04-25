package com.ruoyi.Xidian.domain;

import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class TaskDataGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    @Setter
    private Long id;
    @Setter
    private Long taskId;
    @Setter
    private String groupName;
    //数据名称
    private String dataName;
    @Setter
    private String requestId;
    @Setter
    private String outputType;
    private Integer sortNo;
    @Setter
    private String outputDirectory;
    @Setter
    private String dataSourceType;
    @Setter
    private String sourceFileName;
    @Setter
    private Long startTimeMs;
    @Setter
    private Long endTimeMs;
    private BigDecimal frequencyHz;
    private Integer targetNum;
    private Boolean enabled;
    private String status;
    private Vector3 startVelocity;
    private Attitude startAttitude;
    private Boolean isSimulation;
    private List<TaskDataMetric> metrics;

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getOutputType() {
        return outputType;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public BigDecimal getFrequencyHz() {
        return frequencyHz;
    }

    public void setFrequencyHz(BigDecimal frequencyHz) {
        this.frequencyHz = frequencyHz;
    }

    public Integer getTargetNum() {
        return targetNum;
    }

    public void setTargetNum(Integer targetNum) {
        this.targetNum = targetNum;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Vector3 getStartVelocity() {
        return startVelocity;
    }

    public void setStartVelocity(Vector3 startVelocity) {
        this.startVelocity = startVelocity;
    }

    public Attitude getStartAttitude() {
        return startAttitude;
    }

    public void setStartAttitude(Attitude startAttitude) {
        this.startAttitude = startAttitude;
    }

    public List<TaskDataMetric> getMetrics() {
        return metrics;
    }

    public void setMetric(List<TaskDataMetric> metrics) {
        this.metrics = metrics;
    }

    public void setIsSimulation(Boolean isSimulation){
        this.isSimulation = isSimulation;
    }
    public Boolean getIsSimulation(){
        return isSimulation;
    }

    public void setDataName(String dataName){
        this.dataName = dataName;
    }
    public String getDataName(){
        return dataName;
    }
    public void setSortNo(Integer sortNo){
        this.sortNo = sortNo;
    }
    public Integer getSortNo(){
        return sortNo;
    }
}
