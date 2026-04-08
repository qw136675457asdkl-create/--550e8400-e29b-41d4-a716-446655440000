package com.ruoyi.Xidian.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class TaskDataGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String groupName;
    //数据名称
    private String dataName;
    private String requestId;
    private String outputType;
    private Integer sortNo;
    private String outputDirectory;
    private String dataSourceType;
    private String sourceFileName;
    private Long startTimeMs;
    private Long endTimeMs;
    private BigDecimal frequencyHz;
    private Integer targetNum;
    private String status;
    private Vector3 startVelocity;
    private Attitude startAttitude;
    private Boolean isSimulation;
    private List<TaskDataMetric> metrics;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(Long endTimeMs) {
        this.endTimeMs = endTimeMs;
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
