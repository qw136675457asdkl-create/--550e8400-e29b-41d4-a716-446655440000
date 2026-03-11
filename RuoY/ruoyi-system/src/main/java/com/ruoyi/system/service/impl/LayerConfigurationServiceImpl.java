//{
//  "cells": [],
//  "metadata": {
//    "language_info": {
//      "name": "python"
//    }
//  },
//  "nbformat": 4,
//  "nbformat_minor": 2
//}
package com.ruoyi.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.domain.dto.LayerConfigurationDTO;
import com.ruoyi.system.service.ILayerConfigurationService;
import com.ruoyi.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 图层配置服务实现类
 *
 * @author ruoyi
 */
@Service
public class LayerConfigurationServiceImpl implements ILayerConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(LayerConfigurationServiceImpl.class);
    
    private static final String CONFIG_KEY = "sys.layer.config";

    @Autowired
    private ISysConfigService configService;

    @Override
    public int saveLayerConfiguration(LayerConfigurationDTO dto) {
        try {
            log.info("保存图层配置: {}", dto);

            String jsonConfig = JSON.toJSONString(dto);

            SysConfig query = new SysConfig();
            query.setConfigKey(CONFIG_KEY);
            List<SysConfig> list = configService.selectConfigList(query);

            if (list != null && !list.isEmpty()) {
                // 更新现有配置
                SysConfig existConfig = list.get(0);
                existConfig.setConfigValue(jsonConfig);
                configService.updateConfig(existConfig);
            } else {
                // 新增配置
                SysConfig newConfig = new SysConfig();
                newConfig.setConfigName("图层全局配置");
                newConfig.setConfigKey(CONFIG_KEY);
                newConfig.setConfigValue(jsonConfig);
                newConfig.setConfigType("Y"); // Y=内置, N=自定义
                configService.insertConfig(newConfig);
            }

            log.info("图层配置保存成功");
            return 1;
        } catch (Exception e) {
            log.error("保存图层配置失败", e);
            throw new RuntimeException("保存图层配置失败: " + e.getMessage());
        }
    }

    @Override
    public LayerConfigurationDTO getLayerConfiguration() {
        try {
            String jsonConfig = configService.selectConfigByKey(CONFIG_KEY);
            if (jsonConfig != null && !jsonConfig.isEmpty()) {
                return JSON.parseObject(jsonConfig, LayerConfigurationDTO.class);
            }
            // 如果数据库中没有配置，返回默认配置
            return getDefaultConfiguration();
        } catch (Exception e) {
            log.error("获取图层配置失败", e);
            throw new RuntimeException("获取图层配置失败: " + e.getMessage());
        }
    }

    @Override
    public int resetLayerConfiguration() {
        try {
            log.info("重置图层配置为默认值");

            LayerConfigurationDTO defaultConfig = getDefaultConfiguration();
            return saveLayerConfiguration(defaultConfig);

        } catch (Exception e) {
            log.error("重置图层配置失败", e);
            throw new RuntimeException("重置图层配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认配置
     *
     * @return 默认配置DTO
     */
    private LayerConfigurationDTO getDefaultConfiguration() {
        LayerConfigurationDTO defaultConfig = new LayerConfigurationDTO();
        defaultConfig.setField103("90");
        defaultConfig.setField105(Arrays.asList("使用透明背景"));
        defaultConfig.setField106("#00eeff");
        defaultConfig.setField107("#000000");
        defaultConfig.setField108("12");
        defaultConfig.setField109(Arrays.asList(
                "启用所有功能图层",
                "飞机雷达图图层",
                "仪表盘图层",
                "ADS-B应答数据面板图层",
                "导航图层"
        ));
        defaultConfig.setField110("10");
        defaultConfig.setField111(Arrays.asList("启用图层悬浮显示名称及状态提示"));
        defaultConfig.setField112(Arrays.asList("图层加载失败时显示弹窗提示"));
        defaultConfig.setField113(Arrays.asList("启用图层数据本地缓存（提升重复加载速度）"));
        defaultConfig.setField114("30");
        return defaultConfig;
    }
}
