package com.ruoyi.system.domain.vo;

public class RegionVo {
    private String code;
    private String name;
    private Boolean leaf;

    public RegionVo() {
    }
    public RegionVo(String code, String name, Boolean leaf) {
        this.code = code;
        this.name = name;
        this.leaf = leaf;
    }
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getLeaf() {
        return leaf;
    }

    public void setLeaf(Boolean leaf) {
        this.leaf = leaf;
    }
}
