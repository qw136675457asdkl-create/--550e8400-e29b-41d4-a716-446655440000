package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.regex.Pattern;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.dto.LayerConfigurationDTO;
import com.ruoyi.system.domain.dto.LayerStatusDetailDTO;
import com.ruoyi.system.service.ILayerConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图层配置 Controller。
 */
@RestController
@RequestMapping("/system/layerConfiguration")
public class LayerConfigurationController extends BaseController
{
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Autowired
    private ILayerConfigurationService layerConfigurationService;

    /**
     * 保存图层配置。
     */
    @PreAuthorize("@ss.hasPermi('system:layerConfiguration:save')")
    @Log(title = "图层配置", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody LayerConfigurationDTO dto)
    {
        AjaxResult validationResult = validateConfiguration(dto);
        if (validationResult != null)
        {
            return validationResult;
        }
        int result = layerConfigurationService.saveLayerConfiguration(dto);
        return result > 0 ? AjaxResult.success("保存成功") : AjaxResult.error("保存失败");
    }

    /**
     * 获取图层配置。
     */
    @PreAuthorize("@ss.hasPermi('system:layerConfiguration:query')")
    @GetMapping("/get")
    public AjaxResult get()
    {
        LayerConfigurationDTO config = layerConfigurationService.getLayerConfiguration();
        return AjaxResult.success(config);
    }

    /**
     * 重置图层配置为默认值。
     */
    @PreAuthorize("@ss.hasPermi('system:layerConfiguration:reset')")
    @Log(title = "图层配置", businessType = BusinessType.UPDATE)
    @PostMapping("/reset")
    public AjaxResult reset()
    {
        int result = layerConfigurationService.resetLayerConfiguration();
        return result > 0 ? AjaxResult.success("重置成功") : AjaxResult.error("重置失败");
    }

    private AjaxResult validateConfiguration(LayerConfigurationDTO dto)
    {
        if (dto == null)
        {
            return AjaxResult.error("图层配置不能为空");
        }
        if (dto.getDefaultOpacity() == null)
        {
            return AjaxResult.error("图层默认透明度不能为空");
        }
        if (dto.getDefaultOpacity() < 0 || dto.getDefaultOpacity() > 100)
        {
            return AjaxResult.error("图层默认透明度必须在 0-100 之间");
        }
        if (StringUtils.isBlank(dto.getBgColor()))
        {
            return AjaxResult.error("图层背景色不能为空");
        }
        if (!HEX_COLOR_PATTERN.matcher(dto.getBgColor()).matches())
        {
            return AjaxResult.error("图层背景色格式不正确，应为十六进制颜色值，例如 #00EEFF");
        }
        if (StringUtils.isBlank(dto.getTextColor()))
        {
            return AjaxResult.error("全局文字颜色不能为空");
        }
        if (!HEX_COLOR_PATTERN.matcher(dto.getTextColor()).matches())
        {
            return AjaxResult.error("全局文字颜色格式不正确，应为十六进制颜色值，例如 #000000");
        }
        if (dto.getTextSize() == null || dto.getTextSize() <= 0)
        {
            return AjaxResult.error("全局文字大小必须大于 0");
        }
        if (!hasEnabledLayer(dto.getLayerStatuses()))
        {
            return AjaxResult.error("请至少启用一个功能图层");
        }
        if (dto.getRefreshRate() == null || dto.getRefreshRate() <= 0)
        {
            return AjaxResult.error("图层刷新频率必须大于 0");
        }
        if (dto.getCacheExpireTime() == null || dto.getCacheExpireTime() <= 0)
        {
            return AjaxResult.error("缓存过期时间必须大于 0");
        }
        return null;
    }

    private boolean hasEnabledLayer(List<LayerStatusDetailDTO> layerStatuses)
    {
        if (layerStatuses == null || layerStatuses.isEmpty())
        {
            return false;
        }
        for (LayerStatusDetailDTO layerStatus : layerStatuses)
        {
            if (layerStatus != null && Boolean.TRUE.equals(layerStatus.getIsEnabled()))
            {
                return true;
            }
        }
        return false;
    }
}
