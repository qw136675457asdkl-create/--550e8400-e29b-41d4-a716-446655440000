package com.ruoyi.Xidian.domain;

import java.io.Serializable;
import java.math.BigDecimal;

//坐标值对象
public class Coordinate implements Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal lon;
    private BigDecimal lat;
    private BigDecimal alt;

    public Coordinate() {
    }
    public Coordinate(BigDecimal lon, BigDecimal lat, BigDecimal alt) {
        this.lon = lon;
        this.lat = lat;
        this.alt = alt;
    }
    // getter/setter
    public BigDecimal getLon() {
        return lon;
    }
    public void setLon(BigDecimal lon) {
        this.lon = lon;
    }
    public BigDecimal getLat() {
        return lat;
    }
    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }
    public BigDecimal getAlt() {
        return alt;
    }
    public void setAlt(BigDecimal alt) {
        this.alt = alt;
    }
}