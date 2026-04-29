package com.ruoyi.Xidian.domain.enums;

public enum FileStorageStatusEnum {
    INIT("INIT", "已初始化"),
    UPLOADED("UPLOADED", "已上传"),
    BOUND("BOUND", "已绑定业务"),
    FAILED("FAILED", "上传失败"),
    DELETED("DELETED", "已删除");

    private final String code;
    private final String desc;

    FileStorageStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static FileStorageStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FileStorageStatusEnum value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
