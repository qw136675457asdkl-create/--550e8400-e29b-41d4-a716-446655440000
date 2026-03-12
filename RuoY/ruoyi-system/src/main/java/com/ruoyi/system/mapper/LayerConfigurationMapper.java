package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.dto.LayerConfigurationDTO;
import com.ruoyi.system.domain.dto.LayerStatusDetailDTO;

/**
 * 图层配置数据层。
 */
public interface LayerConfigurationMapper
{
    /**
     * 查询最新的主配置。
     *
     * @return 主配置
     */
    LayerConfigurationDTO selectLatestLayerConfiguration();

    /**
     * 查询配置对应的图层状态明细。
     *
     * @param configId 配置 ID
     * @return 图层状态明细
     */
    List<LayerStatusDetailDTO> selectLayerStatusDetailsByConfigId(Integer configId);

    /**
     * 新增主配置。
     *
     * @param config 主配置
     * @return 结果
     */
    int insertLayerConfiguration(LayerConfigurationDTO config);

    /**
     * 修改主配置。
     *
     * @param config 主配置
     * @return 结果
     */
    int updateLayerConfiguration(LayerConfigurationDTO config);

    /**
     * 删除配置对应的图层状态明细。
     *
     * @param configId 配置 ID
     * @return 结果
     */
    int deleteLayerStatusDetailsByConfigId(Integer configId);

    /**
     * 新增图层状态明细。
     *
     * @param layerStatus 图层状态明细
     * @return 结果
     */
    int insertLayerStatusDetail(LayerStatusDetailDTO layerStatus);
}
