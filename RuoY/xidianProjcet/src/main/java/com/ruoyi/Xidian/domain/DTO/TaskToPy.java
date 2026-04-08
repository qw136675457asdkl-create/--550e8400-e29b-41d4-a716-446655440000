package com.ruoyi.Xidian.domain.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.Xidian.domain.Coordinate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskToPy implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("basic")
    private BasicConfig basic;

    @JsonProperty("datasets")
    private Map<String, DatasetConfig> datasets = new LinkedHashMap<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public BasicConfig getBasic() {
        return basic;
    }

    public void setBasic(BasicConfig basic) {
        this.basic = basic;
    }

    public Map<String, DatasetConfig> getDatasets() {
        return datasets;
    }

    public void setDatasets(Map<String, DatasetConfig> datasets) {
        this.datasets = datasets == null ? new LinkedHashMap<>() : datasets;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("motion_model")
        private String motionModel;

        @JsonProperty("start_coords")
        private Coordinate startCoords;

        @JsonProperty("end_coords")
        private Coordinate endCoords;

        public String getMotionModel() {
            return motionModel;
        }

        public void setMotionModel(String motionModel) {
            this.motionModel = motionModel;
        }

        public Coordinate getStartCoords() {
            return startCoords;
        }

        public void setStartCoords(Coordinate startCoords) {
            this.startCoords = startCoords;
        }

        public Coordinate getEndCoords() {
            return endCoords;
        }

        public void setEndCoords(Coordinate endCoords) {
            this.endCoords = endCoords;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DatasetConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("enabled")
        private Boolean enabled;

        @JsonProperty("filename")
        private String filename;

        @JsonProperty("flight_start_datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
        private Date flightStartDatetime;

        @JsonProperty("flight_end_datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
        private Date flightEndDatetime;

        @JsonProperty("sample_rate_hz")
        private BigDecimal sampleRateHz;

        @JsonProperty("target_num")
        private Integer targetNum;

        @JsonProperty("enemy_num")
        private Integer enemyNum;

        @JsonProperty("friendly_num")
        private Integer friendlyNum;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public Date getFlightStartDatetime() {
            return flightStartDatetime;
        }

        public void setFlightStartDatetime(Date flightStartDatetime) {
            this.flightStartDatetime = flightStartDatetime;
        }

        public Date getFlightEndDatetime() {
            return flightEndDatetime;
        }

        public void setFlightEndDatetime(Date flightEndDatetime) {
            this.flightEndDatetime = flightEndDatetime;
        }

        public BigDecimal getSampleRateHz() {
            return sampleRateHz;
        }

        public void setSampleRateHz(BigDecimal sampleRateHz) {
            this.sampleRateHz = sampleRateHz;
        }

        public Integer getTargetNum() {
            return targetNum;
        }

        public void setTargetNum(Integer targetNum) {
            this.targetNum = targetNum;
        }

        public Integer getEnemyNum() {
            return enemyNum;
        }

        public void setEnemyNum(Integer enemyNum) {
            this.enemyNum = enemyNum;
        }

        public Integer getFriendlyNum() {
            return friendlyNum;
        }

        public void setFriendlyNum(Integer friendlyNum) {
            this.friendlyNum = friendlyNum;
        }
    }
}
