package com.ruoyi.Xidian.domain.enums;

public enum MinioBusinessTypeEnum {
    DATA_RELATION("DATA_RELATION");

    private final String code;

    MinioBusinessTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MinioBusinessTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MinioBusinessTypeEnum value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
