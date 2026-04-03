package com.ruoyi.Xidian.domain;

import java.io.Serializable;
import java.math.BigDecimal;

//指标明细
public class TaskDataMetric implements Serializable {
    private static final long serialVersionUID = 1L;
    // 这一行数据的ID
    private Long id;
    private Long taskId;
    private Long timeStamp;
    // 属于哪个数据项

    private Attitude attitude;
    private Rate rate;
    private Long dataGroupId;
    private BigDecimal flightPath;
    private BigDecimal angleAttitude;
    private BigDecimal sideslipAngle;
    private Enum<?> ahrsStatus;


    //getters
    public Long getId() {
        return id;
    }
    public Long getDataGroupId() {
        return dataGroupId;
    }
    public Long getTimeStamp() {
        return timeStamp;
    }
    //setters
    public void setId(Long id) {
        this.id = id;
    }
    public void setDataGroupId(Long dataGroupId) {
        this.dataGroupId = dataGroupId;
    }
    public void setTimeStamp(Long timeStamp) {
        this.timeStamp = timeStamp;
    }
    public void setAttitude(Attitude attitude) {
        this.attitude = attitude;
    }
    public Attitude getAttitude() {
        return attitude;
    }
    public void setRate(Rate rate) {
        this.rate = rate;
    }
    public Rate getRate() {
        return rate;
    }
    public void setFlightPath(BigDecimal flightPath) {
        this.flightPath = flightPath;
    }
    public BigDecimal getFlightPath() {
        return flightPath;
    }
    public void setAngleAttitude(BigDecimal angleAttitude) {
        this.angleAttitude = angleAttitude;
    }
    public BigDecimal getAngleAttitude() {
        return angleAttitude;
    }
    public void setSideslipAngle(BigDecimal sideslipAngle) {
        this.sideslipAngle = sideslipAngle;
    }
    public BigDecimal getSideslipAngle() {
        return sideslipAngle;
    }
    public void setAhrsStatus(Enum<?> ahrsStatus) {
        this.ahrsStatus = ahrsStatus;
    }
    public Enum<?> getAhrsStatus() {
        return ahrsStatus;
    }
    public Long getTaskId() {
        return taskId;
    }
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
}
