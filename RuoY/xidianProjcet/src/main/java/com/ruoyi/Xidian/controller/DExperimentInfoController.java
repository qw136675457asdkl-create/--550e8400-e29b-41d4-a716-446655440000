package com.ruoyi.Xidian.controller;

import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DTargetInfo;
import com.ruoyi.Xidian.domain.TreeTableVo;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDTargetInfoService;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.common.utils.poi.ExcelUtil;

/**
 * 试验信息主Controller
 */
@RestController
@RequestMapping("/data/info")
public class DExperimentInfoController extends BaseController
{
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private IDProjectInfoService dProjectInfoService;
    @Autowired
    private IDTargetInfoService dTargetInfoService;
    /**
     * 查询试验信息主列表
     */
    @PreAuthorize("@ss.hasPermi('data:info:list')")
    @GetMapping("/list")
    public AjaxResult list(TreeTableVo treeTableVo)
    {
        List<TreeTableVo> treeTableVos = dExperimentInfoService.selectDExperimentInfoTree(treeTableVo);
        return success(treeTableVos);
    }

    /**
     * 导出试验信息主列表
     */
    @PreAuthorize("@ss.hasPermi('data:info:export')")
    @Log(title = "导出试验信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DExperimentInfo dExperimentInfo)
    {
        List<DExperimentInfo> list = dExperimentInfoService.selectDExperimentInfoList(dExperimentInfo);
        ExcelUtil<DExperimentInfo> util = new ExcelUtil<DExperimentInfo>(DExperimentInfo.class);
        util.exportExcel(response, list, "试验信息主数据");
    }

    /**
     * 获取试验信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('data:info:query')")
    @GetMapping(value={"/","/{infoId}"})
    public AjaxResult getInfo(@PathVariable(value = "infoId", required = false) String infoId,@RequestParam String type)
    {
        AjaxResult ajax = AjaxResult.success();
        List<DProjectInfo> dProjectInfos = dProjectInfoService.selectAllDProjectInfo();
        List<DTargetInfo> dTargetInfos = dTargetInfoService.selectDTargetInfoList(null);
        if(type.equals("project"))
        {
            return success(dProjectInfoService.selectDProjectInfoByProjectId(Long.valueOf(infoId)));
        }
        ajax.put("projects",dProjectInfos);
        ajax.put("targetTypes",dTargetInfos);
        if(infoId!=null) {
            ajax.put(AjaxResult.DATA_TAG, dExperimentInfoService.selectDExperimentInfoByExperimentId(infoId));
        }
        return ajax;
    }



    /**
     * 新增试验信息或项目信息
     */
    @PreAuthorize("@ss.hasPermi('data:info:add')")
    @Log(title = "添加项目或试验信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TreeTableVo treeTableVo)
    {
        if(treeTableVo.getType().equals("project")){
            DProjectInfo dProjectInfo=new DProjectInfo();
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setCreateBy(SecurityUtils.getUsername());
            dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
            return toAjax(dProjectInfoService.insertDProjectInfo(dProjectInfo));
        }
        DExperimentInfo dExperimentInfo=new DExperimentInfo();
        dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
        dExperimentInfo.setExperimentName(treeTableVo.getName());
        dExperimentInfo.setCreateBy(SecurityUtils.getUsername());
        dExperimentInfo.setContentDesc(treeTableVo.getContentDesc());
        dExperimentInfo.setLocation(treeTableVo.getLocation());
        dExperimentInfo.setTargetId(treeTableVo.getTargetId());
        dExperimentInfo.setTargetType(treeTableVo.getTargetType());
        dExperimentInfo.setProjectId(treeTableVo.getParentId());
        dExperimentInfo.setStartTime(treeTableVo.getStartTime());
        dExperimentInfo.setPath(treeTableVo.getPath());
        return toAjax(dExperimentInfoService.insertDExperimentInfo(dExperimentInfo));
    }

    /**
     * 修改试验信息主
     */
    @PreAuthorize("@ss.hasPermi('data:info:edit')")
    @Log(title = "试验信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TreeTableVo treeTableVo)
    {
        if(treeTableVo.getType().equals("project")){
            DProjectInfo dProjectInfo=new DProjectInfo();
            dProjectInfo.setProjectId(Long.valueOf(treeTableVo.getId()));
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setCreateTime(treeTableVo.getCreateTime());
            dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
            dProjectInfo.setPath(treeTableVo.getPath());
            return toAjax(dProjectInfoService.updateDProjectInfo(dProjectInfo));
        }
        DExperimentInfo dExperimentInfo=new DExperimentInfo();
        dExperimentInfo.setExperimentId(treeTableVo.getId());
        dExperimentInfo.setExperimentName(treeTableVo.getName());
        dExperimentInfo.setTargetId(treeTableVo.getTargetId());
        dExperimentInfo.setProjectId(treeTableVo.getParentId());
        dExperimentInfo.setLocation(treeTableVo.getLocation());
        dExperimentInfo.setCreateTime(treeTableVo.getCreateTime());
        dExperimentInfo.setContentDesc(treeTableVo.getContentDesc());
        dExperimentInfo.setStartTime(treeTableVo.getStartTime());
        dExperimentInfo.setPath(treeTableVo.getPath());
        return toAjax(dExperimentInfoService.updateDExperimentInfo(dExperimentInfo));
    }

    /**
     * 删除试验信息主
     */
    @PreAuthorize("@ss.hasPermi('data:info:remove')")
    @Log(title = "试验信息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{experimentIds}/project/{projectIds}")
    public AjaxResult remove(@PathVariable String[] experimentIds,@PathVariable Long[] projectIds)
    {
        return toAjax(dExperimentInfoService.deleteDExperimentInfoByExperimentIds(experimentIds)|dProjectInfoService.deleteDProjectInfoByProjectIds(projectIds));
    }

    /**
     * 查看试验信息
     */
    @GetMapping("/experimentInfos")
    public TableDataInfo getExperimentInfos(DExperimentInfo dExperimentInfo){
        startPage();
        List<DExperimentInfo> dExperimentInfos = dExperimentInfoService.selectDExperimentInfoList(dExperimentInfo);
        return getDataTable(dExperimentInfos);
    }

}
