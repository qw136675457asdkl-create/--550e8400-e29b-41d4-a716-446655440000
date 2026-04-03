package com.ruoyi.Xidian.domain.enums;

public enum MotionModelEnum {

    LINEAR("直线模型"),
    QUADRATIC_CURVE("二次曲线"),
    CUBIC_CURVE("三次曲线"),
    RANDOM_CURVE("随机曲线"),
    POLYLINE_2("二折线"),
    POLYLINE_3("三折线");

    private final String desc;

    MotionModelEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}