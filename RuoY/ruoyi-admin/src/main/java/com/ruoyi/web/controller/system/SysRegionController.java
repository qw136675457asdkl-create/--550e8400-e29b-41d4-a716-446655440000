package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.ISysRegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.ruoyi.common.core.domain.AjaxResult.success;

@RestController
@RequestMapping("/system/region")
public class SysRegionController {
    @Autowired
    private ISysRegionService regionService;

    @GetMapping("/tree")
    public AjaxResult getRegionTree(@RequestParam String keyword)
    {
        return success(regionService.getDistrict(keyword, "2"));
    }

    @GetMapping("/children/{parentCode}")
    public AjaxResult getChildren(@PathVariable String parentCode){
        return success(regionService.getChildren(parentCode));
    }
}
