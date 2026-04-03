package com.ruoyi.Xidian.domain;

import java.io.Serializable;
import java.math.BigDecimal;

//角速率
public class Rate implements Serializable {
    private static final long serialVersionUID = 1L;
    // 角速率
    private BigDecimal rateP;
    private BigDecimal rateQ;
    private BigDecimal rateR;
    public Rate() {
    }
    public Rate(BigDecimal rateP, BigDecimal rateQ, BigDecimal rateR) {
        this.rateP = rateP;
        this.rateQ = rateQ;
        this.rateR = rateR;
    }
    //getters
    public BigDecimal getRateP() {
        return rateP;
    }
    public BigDecimal getRateQ() {
        return rateQ;
    }
    public BigDecimal getRateR() {
        return rateR;
    }
    //setters
    public void setRateP(BigDecimal rateP) {
        this.rateP = rateP;
    }
    public void setRateQ(BigDecimal rateQ) {
        this.rateQ = rateQ;
    }
    public void setRateR(BigDecimal rateR) {
        this.rateR = rateR;
    }
}
