package com.ruoyi.Xidian.domain;

import java.io.Serializable;
import java.math.BigDecimal;

//速度向量
public class Vector3 implements Serializable {
    private static final long serialVersionUID = 1L;
    //经
    private BigDecimal vx;
    //纬
    private BigDecimal vy;
    //高
    private BigDecimal vz;

    // getter/setter
    public Vector3() {
    }
    public Vector3(BigDecimal vx, BigDecimal vy, BigDecimal vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }
    public void setVx(BigDecimal vx) {
        this.vx = vx;
    }
    public void setVy(BigDecimal vy) {
        this.vy = vy;
    }
    public void setVz(BigDecimal vz) {
        this.vz = vz;
    }
    public BigDecimal getVx() {
        return vx;
    }
    public BigDecimal getVy() {
        return vy;
    }
    public BigDecimal getVz() {
        return vz;
    }
}
