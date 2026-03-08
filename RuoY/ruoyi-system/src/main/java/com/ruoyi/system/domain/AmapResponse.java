package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 高德地图行政区域查询 API 响应
 */
public class AmapResponse {

    private String status;
    private String info;
    private String infocode;
    private String count;
    private Suggestion suggestion;

    @JsonProperty("districts")
    private List<AmapDistrict> districts;

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInfo() { return info; }
    public void setInfo(String info) { this.info = info; }

    public String getInfocode() { return infocode; }
    public void setInfocode(String infocode) { this.infocode = infocode; }

    public String getCount() { return count; }
    public void setCount(String count) { this.count = count; }

    public Suggestion getSuggestion() { return suggestion; }
    public void setSuggestion(Suggestion suggestion) { this.suggestion = suggestion; }

    public List<AmapDistrict> getDistricts() { return districts; }
    public void setDistricts(List<AmapDistrict> districts) { this.districts = districts; }

    // 内部类：建议信息
    public static class Suggestion {
        private List<String> keywords;
        private List<String> cities;

        // getters/setters
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public List<String> getCities() { return cities; }
        public void setCities(List<String> cities) { this.cities = cities; }
    }

    // 内部类：单个区域信息
    public static class AmapDistrict {
        private String adcode;
        private String name;
        private String center;
        private String level;
        private List<AmapDistrict> districts;

        // getters/setters
        public String getAdcode() { return adcode; }
        public void setAdcode(String adcode) { this.adcode = adcode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCenter() { return center; }
        public void setCenter(String center) { this.center = center; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public List<AmapDistrict> getDistricts() { return districts; }
        public void setDistricts(List<AmapDistrict> districts) { this.districts = districts; }
    }
}
