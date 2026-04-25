package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.dto.MachineLearningEnvConfigDTO;
import com.ruoyi.system.service.IMachineLearningEnvConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 机器学习环境配置 Controller
 */
@RestController
@RequestMapping("/system/machineLearning")
public class MachineLearningConfigurationController extends BaseController
{
    @Autowired
    private IMachineLearningEnvConfigService machineLearningEnvConfigService;

    /**
     * 保存机器学习环境配置
     */
    @PreAuthorize("@ss.hasPermi('system:machineLearning:save')")
    @Log(title = "机器学习环境配置", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody MachineLearningEnvConfigDTO dto)
    {
        // 必填项校验（与 MachineLearning.vue rules 对齐）
        if (StringUtils.isBlank(dto.getField119()))
        {
            return AjaxResult.error("Python 解释器路径不能为空");
        }
        if (dto.getField104() == null)
        {
            return AjaxResult.error("Python 版本选择不能为空");
        }
        if (dto.getField107() == null)
        {
            return AjaxResult.error("依赖库安装源不能为空");
        }
        if (StringUtils.isBlank(dto.getField124()))
        {
            return AjaxResult.error("Matlab 安装路径不能为空");
        }
        if (dto.getField125() == null)
        {
            return AjaxResult.error("Matlab 版本选择不能为空");
        }

        int result = machineLearningEnvConfigService.save(dto);
        return result > 0 ? AjaxResult.success("保存成功") : AjaxResult.error("保存失败");
    }

    /**
     * 获取当前机器学习环境配置
     */
    @PreAuthorize("@ss.hasAnyPermi('system:machineLearning:execute,system:machineLearning:save,system:machineLearning:reset')")
    @GetMapping("/get")
    public AjaxResult get()
    {
        return AjaxResult.success(machineLearningEnvConfigService.get());
    }

    /**
     * 重置为默认配置
     */
    @PreAuthorize("@ss.hasPermi('system:machineLearning:reset')")
    @Log(title = "机器学习环境配置", businessType = BusinessType.UPDATE)
    @PostMapping("/reset")
    public AjaxResult reset()
    {
        int result = machineLearningEnvConfigService.reset();
        return result > 0 ? AjaxResult.success("重置成功") : AjaxResult.error("重置失败");
    }
}

