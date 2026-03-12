package com.ruoyi.system.domain.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图层配置 DTO。
 */
public class LayerConfigurationDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Integer id;

    private Integer defaultOpacity;

    private Boolean useTransBg;

    private String bgColor;

    private String textColor;

    private Integer textSize;

    private Integer refreshRate;

    private Boolean showFloatTip;

    private Boolean showErrorWind;

    private Boolean enableCache;

    private Integer cacheExpireTime;

    private List<LayerStatusDetailDTO> layerStatuses = new ArrayList<LayerStatusDetailDTO>();

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getDefaultOpacity()
    {
        return defaultOpacity;
    }

    public void setDefaultOpacity(Integer defaultOpacity)
    {
        this.defaultOpacity = defaultOpacity;
    }

    public Boolean getUseTransBg()
    {
        return useTransBg;
    }

    public void setUseTransBg(Boolean useTransBg)
    {
        this.useTransBg = useTransBg;
    }

    public String getBgColor()
    {
        return bgColor;
    }

    public void setBgColor(String bgColor)
    {
        this.bgColor = bgColor;
    }

    public String getTextColor()
    {
        return textColor;
    }

    public void setTextColor(String textColor)
    {
        this.textColor = textColor;
    }

    public Integer getTextSize()
    {
        return textSize;
    }

    public void setTextSize(Integer textSize)
    {
        this.textSize = textSize;
    }

    public Integer getRefreshRate()
    {
        return refreshRate;
    }

    public void setRefreshRate(Integer refreshRate)
    {
        this.refreshRate = refreshRate;
    }

    public Boolean getShowFloatTip()
    {
        return showFloatTip;
    }

    public void setShowFloatTip(Boolean showFloatTip)
    {
        this.showFloatTip = showFloatTip;
    }

    public Boolean getShowErrorWind()
    {
        return showErrorWind;
    }

    public void setShowErrorWind(Boolean showErrorWind)
    {
        this.showErrorWind = showErrorWind;
    }

    public Boolean getEnableCache()
    {
        return enableCache;
    }

    public void setEnableCache(Boolean enableCache)
    {
        this.enableCache = enableCache;
    }

    public Integer getCacheExpireTime()
    {
        return cacheExpireTime;
    }

    public void setCacheExpireTime(Integer cacheExpireTime)
    {
        this.cacheExpireTime = cacheExpireTime;
    }

    public List<LayerStatusDetailDTO> getLayerStatuses()
    {
        return layerStatuses;
    }

    public void setLayerStatuses(List<LayerStatusDetailDTO> layerStatuses)
    {
        this.layerStatuses = layerStatuses;
    }
}
