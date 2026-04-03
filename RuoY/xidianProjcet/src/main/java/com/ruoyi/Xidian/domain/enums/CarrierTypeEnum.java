package com.ruoyi.Xidian.domain.enums;

public enum CarrierTypeEnum {
    PSEUDO_AIRCRAFT("伪无人机"),
    UAV("无人机AV"),
    SHIP("船舶"),
    VEHICLE("车辆");

    private final String desc;

    CarrierTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
