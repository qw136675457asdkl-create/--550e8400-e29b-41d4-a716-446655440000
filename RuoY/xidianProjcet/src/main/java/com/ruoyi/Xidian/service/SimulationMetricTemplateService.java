package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.TaskDataMetricDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulationMetricTemplateService {
    public Map<String, List<TaskDataMetricDTO>> listTemplates() {
        Map<String, List<TaskDataMetricDTO>> templates = new LinkedHashMap<>();
        templates.put("INS", buildInsMetrics());
        templates.put("ATTITUDE", buildInsMetrics());
        templates.put("RADAR_TRACK", buildInsMetrics());
        templates.put("ADS_B", buildInsMetrics());
        templates.put("EW", buildInsMetrics());
        templates.put("AIS", buildInsMetrics());
        templates.put("ADSB", buildInsMetrics());
        templates.put("COMM", buildInsMetrics());
        templates.put("DATA9", buildInsMetrics());
        templates.put("DATA10", buildInsMetrics());
        return templates;
    }

    private List<TaskDataMetricDTO> buildInsMetrics() {
        List<TaskDataMetricDTO> metrics = new ArrayList<>();
        metrics.add(createMetric("timestamp", "uint32", "/", "/", "高精度时钟", 1));
        metrics.add(createMetric("lat", "Double", "/", "/", "纬度", 2));
        metrics.add(createMetric("lon", "Double", "/", "/", "经度", 3));
        metrics.add(createMetric("alt", "Double", "/", "/", "高度", 4));
        metrics.add(createMetric("true_airspeed", "Float", "/", "/", "真空速，三速度求解", 5));
        metrics.add(createMetric("heading_true", "Float", "/", "/", "真航向，机头指向相对于真北的角度", 6));
        metrics.add(createMetric("vel_north", "Float", "/", "/", "北向速度", 7));
        metrics.add(createMetric("vel_east", "Float", "/", "/", "东向速度", 8));
        metrics.add(createMetric("vel_vertical", "Float", "/", "/", "地向速度，上升为正", 9));
        metrics.add(createMetric("nav_mode", "Enum", "/", "/", "导航模式状态，如 ALIGN（对准中）等", 10));
        metrics.add(createMetric("ins_status_w", "Hex", "/", "/", "系统状态字，包含 BIT 自检结果和传感器状态", 11));
        return metrics;
    }

    private TaskDataMetricDTO createMetric(
            String fieldName,
            String dataType,
            String recommendedValue,
            String fluctuationRange,
            String description,
            int sortNo
    ) {
        TaskDataMetricDTO metric = new TaskDataMetricDTO();
        metric.setFieldName(fieldName);
        metric.setDataType(dataType);
        metric.setRecommendedValue(recommendedValue);
        metric.setFluctuationRange(fluctuationRange);
        metric.setDescription(description);
        metric.setSortNo(sortNo);
        return metric;
    }
}
