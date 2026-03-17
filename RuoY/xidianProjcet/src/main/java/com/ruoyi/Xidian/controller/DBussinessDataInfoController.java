package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data/bussiness")
public class DBussinessDataInfoController extends BaseController {
    private static final int DEFAULT_PREVIEW_PAGE_SIZE = 20;
    private static final int MAX_PREVIEW_PAGE_SIZE = 1000;
    public final String profilePath = RuoYiConfig.getProfile() + "/data";

    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private IDdataService ddataService;
    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @GetMapping("/experimentInfoTree")
    public AjaxResult getDExperimentInfoTree()
    {
        return AjaxResult.success(dExperimentInfoService.getExperimentInfoTree());
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:list')")
    @GetMapping("/datalist")
    public TableDataInfo getDDataInfoList(DdataInfo ddataInfo)
    {
        startPage();
        return getDataTable(ddataService.selectDdataInfoList(ddataInfo));
    }
    @PreAuthorize("@ss.hasPermi('dataInfo:info:query')")
    @GetMapping("/{id}")
    public AjaxResult getDDataInfoByDdataId(@PathVariable Integer id)
    {
        DdataInfo ddataInfo = ddataService.selectDdataInfoByDdataId(id);
        if (ddataInfo != null && StringUtils.isNotEmpty(ddataInfo.getDataFilePath()))
        {
            String relativePath = StringUtils.removeStart(ddataInfo.getDataFilePath(), "/");
            String fileName = FileUtils.getName(relativePath);
            int dotIndex = fileName.lastIndexOf(".");
            ddataInfo.setFileName(dotIndex > -1 ? fileName.substring(0, dotIndex) : fileName);
        }
        return AjaxResult.success(ddataInfo);
    }
    @PreAuthorize("@ss.hasPermi('dataInfo:info:insert')")
    @PostMapping("/insert")
    @Log(title = "导入业务数据", businessType = BusinessType.INSERT)
    public AjaxResult insertDDataInfo(@ModelAttribute DdataInfo ddataInfo, @RequestParam("file") MultipartFile file)
    {
        return AjaxResult.success(ddataService.insertDdataInfo(ddataInfo, file));
    }
    @PreAuthorize("@ss.hasPermi('dataInfo:info:update')")
    @PutMapping("/update")
    @Log(title = "更新业务数据信息", businessType = BusinessType.UPDATE)
    public AjaxResult updateDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        return AjaxResult.success(ddataService.updateDdataInfo(ddataInfo));
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:update')")
    @GetMapping("/movePathTree")
    public AjaxResult getMovePathTree()
    {
        return AjaxResult.success(ddataService.getMovePathTree());
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:delete')")
    @DeleteMapping("/delete")
    @Log(title = "删除业务数据信息", businessType = BusinessType.DELETE)
    public AjaxResult deleteDdataInfos(@RequestBody List<Integer> ids){
        if(ids==null || ids.isEmpty()){
            throw new ServiceException("请选择需要删除的数据");
        }
        return AjaxResult.success(ddataService.deleteDdataInfos(ids));
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @PostMapping("/preview")
    public AjaxResult previewDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        String ExperimentId = ddataInfo.getExperimentId();
        DExperimentInfo dExperimentInfo = dExperimentInfoService.selectDExperimentInfoByExperimentId(ExperimentId);
        String AncestPath = dProjectInfoService.selectDProjectInfoByProjectId(dExperimentInfo.getProjectId()).getPath();
        int pageNum = ddataInfo.getPageNum() == null || ddataInfo.getPageNum() < 1 ? 1 : ddataInfo.getPageNum();
        int pageSize = ddataInfo.getPageSize() == null || ddataInfo.getPageSize() < 1 ? DEFAULT_PREVIEW_PAGE_SIZE : ddataInfo.getPageSize();
        pageSize = Math.min(pageSize, MAX_PREVIEW_PAGE_SIZE);
        String filePath = profilePath + AncestPath + dExperimentInfo.getPath()  + ddataInfo.getDataFilePath();
        Map<String, Object> previewData = FileUtils.previewExcelByPage(filePath, pageNum, pageSize);
        return success(previewData);
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:download')")
    @PostMapping("/download")
    @Log(title = "下载数据文件" , businessType = BusinessType.EXPORT)
    public void downloadDDataInfoFile(@RequestBody DdataInfo ddataInfo, HttpServletResponse response)
    {
        if (StringUtils.isEmpty(ddataInfo.getExperimentId()) || StringUtils.isEmpty(ddataInfo.getDataFilePath()))
        {
            throw new ServiceException("下载参数不能为空");
        }

        DExperimentInfo dExperimentInfo = dExperimentInfoService.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        if (dExperimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        String ancestPath = dProjectInfoService.selectDProjectInfoByProjectId(dExperimentInfo.getProjectId()).getPath();
        String relativePath = StringUtils.removeStart(ddataInfo.getDataFilePath(), "/");
        String absolutePath = profilePath + ancestPath + dExperimentInfo.getPath() + "/" + relativePath;
        FileUtils.downloadFile(absolutePath, FileUtils.getName(relativePath), response);
    }
}
