package com.ruoyi.Xidian.domain.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.Xidian.domain.Attitude;
import com.ruoyi.Xidian.domain.Coordinate;
import com.ruoyi.Xidian.domain.RandomSeeds;
import com.ruoyi.Xidian.domain.Vector3;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class TaskToPy implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("request_id")
    private String request_id;

    @JsonProperty("start_coords")
    private Coordinate start_coords;

    @JsonProperty("end_coords")
    private Coordinate end_coords;

    @JsonProperty("start_velocity")
    private Vector3 start_velocity;

    @JsonProperty("start_attitude")
    private Attitude start_attitude;

    @JsonProperty("flight_start_datetime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date flight_start_datetime;

    @JsonProperty("flight_end_datetime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date flight_end_datetime;

    @JsonProperty("sample_rate_hz")
    private BigDecimal sample_rate_hz;

    @JsonProperty("num")
    private Integer num;

    @JsonProperty("host_trajectory_type")
    private String host_trajectory_type;

    @JsonProperty("output_directory")
    private String output_directory;

    @JsonProperty("random_seeds")
    private RandomSeeds random_seeds;

    public RandomSeeds getRandomSeeds() {
        return random_seeds;
    }

    public void setRandomSeeds(RandomSeeds random_seeds) {
        this.random_seeds = random_seeds;
    }

    public String getRequest_id() {
        return request_id;
    }

    public void setRequest_id(String request_id) {
        this.request_id = request_id;
    }

    public Coordinate getStart_coords() {
        return start_coords;
    }

    public void setStart_coords(Coordinate start_coords) {
        this.start_coords = start_coords;
    }

    public Coordinate getEnd_coords() {
        return end_coords;
    }

    public void setEnd_coords(Coordinate end_coords) {
        this.end_coords = end_coords;
    }

    public Vector3 getStart_velocity() {
        return start_velocity;
    }

    public void setStart_velocity(Vector3 start_velocity) {
        this.start_velocity = start_velocity;
    }

    public Attitude getStart_attitude() {
        return start_attitude;
    }

    public void setStart_attitude(Attitude start_attitude) {
        this.start_attitude = start_attitude;
    }

    public BigDecimal getSample_rate_hz() {
        return sample_rate_hz;
    }

    public void setSample_rate_hz(BigDecimal sample_rate_hz) {
        this.sample_rate_hz = sample_rate_hz;
    }

    public Integer getNum() {
        return num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public String getHost_trajectory_type() {
        return host_trajectory_type;
    }

    public void setHost_trajectory_type(String host_trajectory_type) {
        this.host_trajectory_type = host_trajectory_type;
    }

    public String getOutput_directory() {
        return output_directory;
    }

    public void setOutput_directory(String output_directory) {
        this.output_directory = output_directory;
    }

    public Date getFlight_start_datetime() {
        return flight_start_datetime;
    }

    public void setFlight_start_datetime(Date flight_start_datetime) {
        this.flight_start_datetime = flight_start_datetime;
    }

    public Date getFlight_end_datetime() {
        return flight_end_datetime;
    }

    public void setFlight_end_datetime(Date flight_end_datetime) {
        this.flight_end_datetime = flight_end_datetime;
    }
}
