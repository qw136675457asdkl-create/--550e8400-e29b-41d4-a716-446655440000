package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.dto.LayerConfigurationDTO;
import com.ruoyi.system.domain.dto.LayerStatusDetailDTO;
import com.ruoyi.system.mapper.LayerConfigurationMapper;
import com.ruoyi.system.service.ILayerConfigurationService;
import com.ruoyi.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图层配置服务实现。
 */
@Service
public class LayerConfigurationServiceImpl implements ILayerConfigurationService
{
    private static final Logger log = LoggerFactory.getLogger(LayerConfigurationServiceImpl.class);

    private static final String LEGACY_CONFIG_KEY = "sys.layer.config";

    private static final String DEFAULT_BG_COLOR = "#00EEFF";

    private static final String DEFAULT_TEXT_COLOR = "#000000";

    private static final String LAYER_CODE_ALL = "ALL";

    private static final Map<String, String> LAYER_NAME_MAPPING;

    static
    {
        Map<String, String> layerNameMapping = new LinkedHashMap<String, String>();
        layerNameMapping.put(LAYER_CODE_ALL, "启用所有功能图层");
        layerNameMapping.put("RADAR", "飞机雷达图层");
        layerNameMapping.put("DASHBOARD", "仪表盘图层");
        layerNameMapping.put("ADSB", "ADS-B应答数据面板图层");
        layerNameMapping.put("NAVIGATION", "导航图层");
        LAYER_NAME_MAPPING = Collections.unmodifiableMap(layerNameMapping);
    }

    @Autowired
    private LayerConfigurationMapper layerConfigurationMapper;

    @Autowired
    private ISysConfigService configService;

    @Override
    @Transactional
    public int saveLayerConfiguration(LayerConfigurationDTO dto)
    {
        try
        {
            LayerConfigurationDTO normalizedConfig = normalizeConfiguration(dto);
            LayerConfigurationDTO currentConfig = layerConfigurationMapper.selectLatestLayerConfiguration();
            if (currentConfig == null)
            {
                layerConfigurationMapper.insertLayerConfiguration(normalizedConfig);
                if (normalizedConfig.getId() == null)
                {
                    LayerConfigurationDTO insertedConfig = layerConfigurationMapper.selectLatestLayerConfiguration();
                    if (insertedConfig != null)
                    {
                        normalizedConfig.setId(insertedConfig.getId());
                    }
                }
            }
            else
            {
                normalizedConfig.setId(currentConfig.getId());
                layerConfigurationMapper.updateLayerConfiguration(normalizedConfig);
                layerConfigurationMapper.deleteLayerStatusDetailsByConfigId(normalizedConfig.getId());
            }
            insertLayerStatusDetails(normalizedConfig);
            log.info("图层配置保存成功，configId={}", normalizedConfig.getId());
            return 1;
        }
        catch (Exception e)
        {
            log.error("保存图层配置失败", e);
            throw new RuntimeException("保存图层配置失败: " + e.getMessage());
        }
    }

    @Override
    public LayerConfigurationDTO getLayerConfiguration()
    {
        try
        {
            LayerConfigurationDTO currentConfig = layerConfigurationMapper.selectLatestLayerConfiguration();
            if (currentConfig != null)
            {
                currentConfig.setLayerStatuses(layerConfigurationMapper.selectLayerStatusDetailsByConfigId(currentConfig.getId()));
                return normalizeConfiguration(currentConfig);
            }

            LayerConfigurationDTO legacyConfig = loadLegacyConfiguration();
            if (legacyConfig != null)
            {
                return normalizeConfiguration(legacyConfig);
            }

            return getDefaultConfiguration();
        }
        catch (Exception e)
        {
            log.error("获取图层配置失败", e);
            throw new RuntimeException("获取图层配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public int resetLayerConfiguration()
    {
        try
        {
            log.info("重置图层配置为默认值");
            return saveLayerConfiguration(getDefaultConfiguration());
        }
        catch (Exception e)
        {
            log.error("重置图层配置失败", e);
            throw new RuntimeException("重置图层配置失败: " + e.getMessage());
        }
    }

    private void insertLayerStatusDetails(LayerConfigurationDTO config)
    {
        if (config.getId() == null)
        {
            throw new IllegalStateException("图层主配置 ID 不能为空");
        }
        List<LayerStatusDetailDTO> layerStatuses = config.getLayerStatuses();
        if (layerStatuses == null || layerStatuses.isEmpty())
        {
            return;
        }

        List<LayerStatusDetailDTO> normalizedLayerStatuses = new ArrayList<LayerStatusDetailDTO>(layerStatuses.size());
        for (LayerStatusDetailDTO layerStatus : layerStatuses)
        {
            if (layerStatus == null)
            {
                continue;
            }
            LayerStatusDetailDTO detail = new LayerStatusDetailDTO();
            detail.setConfigId(config.getId());
            detail.setLayerCode(layerStatus.getLayerCode());
            detail.setLayerName(layerStatus.getLayerName());
            detail.setIsEnabled(Boolean.TRUE.equals(layerStatus.getIsEnabled()));
            normalizedLayerStatuses.add(detail);
        }

        for (LayerStatusDetailDTO layerStatus : normalizedLayerStatuses)
        {
            layerConfigurationMapper.insertLayerStatusDetail(layerStatus);
        }
    }

    private LayerConfigurationDTO loadLegacyConfiguration()
    {
        String jsonConfig = configService.selectConfigByKey(LEGACY_CONFIG_KEY);
        if (StringUtils.isEmpty(jsonConfig))
        {
            return null;
        }
        return normalizeConfiguration(parseLegacyConfiguration(jsonConfig));
    }

    private LayerConfigurationDTO parseLegacyConfiguration(String jsonConfig)
    {
        JSONObject jsonObject = JSON.parseObject(jsonConfig);
        if (jsonObject == null || jsonObject.isEmpty())
        {
            return null;
        }
        if (!jsonObject.containsKey("field103"))
        {
            return JSON.parseObject(jsonConfig, LayerConfigurationDTO.class);
        }
        LayerConfigurationDTO config = new LayerConfigurationDTO();
        config.setDefaultOpacity(parseInteger(jsonObject.getString("field103"), 90));
        config.setUseTransBg(isChecked(jsonObject.getJSONArray("field105")));
        config.setBgColor(defaultColor(jsonObject.getString("field106"), DEFAULT_BG_COLOR));
        config.setTextColor(defaultColor(jsonObject.getString("field107"), DEFAULT_TEXT_COLOR));
        config.setTextSize(parseInteger(jsonObject.getString("field108"), 12));
        config.setLayerStatuses(buildLayerStatuses(resolveLegacyLayerCodes(jsonObject.getJSONArray("field109"))));
        config.setRefreshRate(parseInteger(jsonObject.getString("field110"), 10));
        config.setShowFloatTip(isChecked(jsonObject.getJSONArray("field111")));
        config.setShowErrorWind(isChecked(jsonObject.getJSONArray("field112")));
        config.setEnableCache(isChecked(jsonObject.getJSONArray("field113")));
        config.setCacheExpireTime(parseInteger(jsonObject.getString("field114"), 30));
        return config;
    }

    private LayerConfigurationDTO normalizeConfiguration(LayerConfigurationDTO source)
    {
        LayerConfigurationDTO normalized = new LayerConfigurationDTO();
        if (source != null)
        {
            normalized.setId(source.getId());
        }
        normalized.setDefaultOpacity(source != null && source.getDefaultOpacity() != null ? source.getDefaultOpacity() : 90);
        normalized.setUseTransBg(source == null || source.getUseTransBg() == null ? Boolean.TRUE : source.getUseTransBg());
        normalized.setBgColor(source != null ? defaultColor(source.getBgColor(), DEFAULT_BG_COLOR) : DEFAULT_BG_COLOR);
        normalized.setTextColor(source != null ? defaultColor(source.getTextColor(), DEFAULT_TEXT_COLOR) : DEFAULT_TEXT_COLOR);
        normalized.setTextSize(source != null && source.getTextSize() != null ? source.getTextSize() : 12);
        normalized.setRefreshRate(source != null && source.getRefreshRate() != null ? source.getRefreshRate() : 10);
        normalized.setShowFloatTip(source == null || source.getShowFloatTip() == null ? Boolean.TRUE : source.getShowFloatTip());
        normalized.setShowErrorWind(source == null || source.getShowErrorWind() == null ? Boolean.TRUE : source.getShowErrorWind());
        normalized.setEnableCache(source == null || source.getEnableCache() == null ? Boolean.TRUE : source.getEnableCache());
        normalized.setCacheExpireTime(source != null && source.getCacheExpireTime() != null ? source.getCacheExpireTime() : 30);
        normalized.setLayerStatuses(buildLayerStatuses(extractEnabledLayerCodes(source != null ? source.getLayerStatuses() : null)));
        return normalized;
    }

    private Set<String> extractEnabledLayerCodes(List<LayerStatusDetailDTO> layerStatuses)
    {
        Set<String> enabledLayerCodes = new LinkedHashSet<String>();
        if (layerStatuses == null)
        {
            return enabledLayerCodes;
        }
        for (LayerStatusDetailDTO layerStatus : layerStatuses)
        {
            if (layerStatus == null || !Boolean.TRUE.equals(layerStatus.getIsEnabled()))
            {
                continue;
            }
            String layerCode = layerStatus.getLayerCode();
            if (LAYER_NAME_MAPPING.containsKey(layerCode))
            {
                enabledLayerCodes.add(layerCode);
            }
        }
        if (enabledLayerCodes.contains(LAYER_CODE_ALL))
        {
            enabledLayerCodes.clear();
            enabledLayerCodes.add(LAYER_CODE_ALL);
        }
        return enabledLayerCodes;
    }

    private List<LayerStatusDetailDTO> buildLayerStatuses(Collection<String> enabledLayerCodes)
    {
        Set<String> enabledCodeSet = new LinkedHashSet<String>();
        if (enabledLayerCodes != null)
        {
            enabledCodeSet.addAll(enabledLayerCodes);
        }
        boolean enableAll = enabledCodeSet.contains(LAYER_CODE_ALL);
        List<LayerStatusDetailDTO> layerStatuses = new ArrayList<LayerStatusDetailDTO>(LAYER_NAME_MAPPING.size());
        for (Map.Entry<String, String> entry : LAYER_NAME_MAPPING.entrySet())
        {
            LayerStatusDetailDTO layerStatus = new LayerStatusDetailDTO();
            layerStatus.setLayerCode(entry.getKey());
            layerStatus.setLayerName(entry.getValue());
            layerStatus.setIsEnabled(enableAll ? LAYER_CODE_ALL.equals(entry.getKey()) : enabledCodeSet.contains(entry.getKey()));
            layerStatuses.add(layerStatus);
        }
        return layerStatuses;
    }

    private Set<String> resolveLegacyLayerCodes(JSONArray legacyLayerValues)
    {
        Set<String> enabledLayerCodes = new LinkedHashSet<String>();
        if (legacyLayerValues == null)
        {
            return enabledLayerCodes;
        }
        for (int index = 0; index < legacyLayerValues.size(); index++)
        {
            String layerValue = legacyLayerValues.getString(index);
            if (StringUtils.isEmpty(layerValue))
            {
                continue;
            }
            String normalizedValue = layerValue.replace(" ", "");
            if (normalizedValue.contains("所有功能"))
            {
                enabledLayerCodes.clear();
                enabledLayerCodes.add(LAYER_CODE_ALL);
                return enabledLayerCodes;
            }
            if (normalizedValue.contains("飞机雷达"))
            {
                enabledLayerCodes.add("RADAR");
            }
            else if (normalizedValue.contains("仪表盘"))
            {
                enabledLayerCodes.add("DASHBOARD");
            }
            else if (normalizedValue.toUpperCase().contains("ADS-B"))
            {
                enabledLayerCodes.add("ADSB");
            }
            else if (normalizedValue.contains("导航"))
            {
                enabledLayerCodes.add("NAVIGATION");
            }
        }
        return enabledLayerCodes;
    }

    private boolean isChecked(JSONArray values)
    {
        return values != null && !values.isEmpty();
    }

    private Integer parseInteger(String value, Integer defaultValue)
    {
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private String defaultColor(String color, String defaultColor)
    {
        return StringUtils.isEmpty(color) ? defaultColor : color.toUpperCase();
    }

    private LayerConfigurationDTO getDefaultConfiguration()
    {
        LayerConfigurationDTO defaultConfig = new LayerConfigurationDTO();
        defaultConfig.setDefaultOpacity(90);
        defaultConfig.setUseTransBg(true);
        defaultConfig.setBgColor(DEFAULT_BG_COLOR);
        defaultConfig.setTextColor(DEFAULT_TEXT_COLOR);
        defaultConfig.setTextSize(12);
        defaultConfig.setRefreshRate(10);
        defaultConfig.setShowFloatTip(true);
        defaultConfig.setShowErrorWind(true);
        defaultConfig.setEnableCache(true);
        defaultConfig.setCacheExpireTime(30);
        defaultConfig.setLayerStatuses(buildLayerStatuses(Collections.singleton(LAYER_CODE_ALL)));
        return defaultConfig;
    }
}
