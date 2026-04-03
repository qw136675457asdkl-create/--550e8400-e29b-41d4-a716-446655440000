package com.ruoyi.Xidian.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class RandomSeeds implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("host")
    private Integer host;

    @JsonProperty("enemy")
    private Integer enemy;

    @JsonProperty("wingman")
    private Integer wingman;

    @JsonProperty("attitude")
    private Integer attitude;

    public RandomSeeds() {

    }

    public RandomSeeds(Integer host, Integer enemy, Integer wingman, Integer attitude) {
        this.host = host;
        this.enemy = enemy;
        this.wingman = wingman;
        this.attitude = attitude;
    }

    public Integer getHost() {
        return host;
    }

    public Integer getEnemy() {
        return enemy;
    }

    public Integer getWingman() {
        return wingman;
    }

    public Integer getAttitude() {
        return attitude;
    }

    public void setAttitude(Integer attitude) {
        this.attitude = attitude;
    }

    public void setHost(Integer host) {
        this.host = host;
    }

    public void setEnemy(Integer enemy) {
        this.enemy = enemy;
    }

    public void setWingman(Integer wingman) {
        this.wingman = wingman;
    }
}
