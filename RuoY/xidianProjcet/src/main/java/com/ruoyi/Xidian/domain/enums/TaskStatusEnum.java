package com.ruoyi.Xidian.domain.enums;

public enum TaskStatusEnum {
    DRAFT("提交"),
    RUNNING("运行中"),
    SUCCESS("成功"),
    FAILED("失败");

    private final String desc;

    TaskStatusEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}