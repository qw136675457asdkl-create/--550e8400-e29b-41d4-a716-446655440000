package com.ruoyi.Xidian.domain.enums;

public enum OutputTypeEnum {
    BIT("位输出"),
    CSV("CSV输出");

    private final String desc;

    OutputTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}