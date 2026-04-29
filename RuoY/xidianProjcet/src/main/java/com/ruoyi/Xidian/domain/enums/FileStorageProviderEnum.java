package com.ruoyi.Xidian.domain.enums;

public enum FileStorageProviderEnum {
    MINIO("MINIO");

    private final String code;

    FileStorageProviderEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
