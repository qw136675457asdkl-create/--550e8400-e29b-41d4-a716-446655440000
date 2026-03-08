package com.ruoyi.system.domain;

import java.util.List;

/**
 * 区域
 */
public class Region {
    private String code;
    private String name;
    private String level;
    private String parentCode;
    private List<Region> children;
    public Region() {
    }
    public Region(String code, String name, String level, String parentCode, List<Region> children) {
        this.code = code;
        this.name = name;
        this.level = level;
        this.parentCode = parentCode;
        this.children = children;
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
    public String getLevel() {
        return level;
    }
    public void setLevel(String level) {
        this.level = level;
    }
    public String getParentCode() {
        return parentCode;
    }
    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }
    public List<Region> getChildren() {
        return children;
    }
    public void setChildren(List<Region> children) {
        this.children = children;
    }
}
