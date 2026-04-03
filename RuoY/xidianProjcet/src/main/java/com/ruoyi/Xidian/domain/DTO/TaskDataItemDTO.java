package com.ruoyi.Xidian.domain.DTO;

import com.ruoyi.Xidian.domain.Attitude;
import com.ruoyi.Xidian.domain.RandomSeeds;
import com.ruoyi.Xidian.domain.Vector3;

import java.math.BigDecimal;
import java.util.List;

public class TaskDataItemDTO {
    private String dataName;
    private String requestId;
    private String outputType;
    private String outputDirectory;
    private String dataSourceType;
    private String sourceFileName;
    private Long startTimeMs;
    private Long endTimeMs;
    private BigDecimal frequencyHz;
    private Integer targetNum;
    private Vector3 startVelocity;
    private Attitude startAttitude;
    private RandomSeeds randomSeeds;
    private List<TaskDataMetricDTO> metrics;

    public String getDataName() {
        return dataName;
    }
    public void setDataName(String dataName) {
        this.dataName = dataName;
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
    public RandomSeeds getRandomSeeds() {
        return randomSeeds;
    }
    public void setRandomSeeds(RandomSeeds randomSeeds) {
        this.randomSeeds = randomSeeds;
    }
    public List<TaskDataMetricDTO> getMetrics() {
        return metrics;
    }
    public void setMetrics(List<TaskDataMetricDTO> metrics) {
        this.metrics = metrics;
    }
}
