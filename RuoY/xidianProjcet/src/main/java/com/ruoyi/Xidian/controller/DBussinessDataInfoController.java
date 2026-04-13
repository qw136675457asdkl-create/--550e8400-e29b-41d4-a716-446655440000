package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.Xidian.support.PathLockManager;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data/bussiness")
public class DBussinessDataInfoController extends BaseController
{
    private static final int DEFAULT_PREVIEW_PAGE_SIZE = 20;
    private static final int MAX_PREVIEW_PAGE_SIZE = 1000;

    public final String profilePath = RuoYiConfig.getProfile() + "/data";

    @Autowired
    private IDExperimentInfoService dExperimentInfoService;

    @Autowired
    private IDdataService ddataService;

    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @Autowired
    private PathLockManager pathLockManager;

    @GetMapping("/experimentInfoTree")
    public AjaxResult getDExperimentInfoTree()
    {
        try{
            return AjaxResult.success(dExperimentInfoService.getExperimentInfoTree());
        }
        catch (Exception e){
            throw new ServiceException("查询试验信息树失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:list')")
    @GetMapping("/datalist")
    public TableDataInfo getDDataInfoList(DdataInfo ddataInfo)
    {
        startPage();
        try{
            return getDataTable(ddataService.selectDdataInfoList(ddataInfo));
        }
        catch (Exception e){
            throw new ServiceException("查询业务数据列表失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:query')")
    @GetMapping("/{id}")
    public AjaxResult getDDataInfoByDdataId(@PathVariable Integer id)
    {
        DdataInfo ddataInfo = new DdataInfo();
        try {
            ddataInfo = ddataService.selectDdataInfoByDdataId(id);
        }
        catch (Exception e) {

            throw new ServiceException("查询业务数据详情失败");
        }
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
    public AjaxResult insertDDataInfo(
            @ModelAttribute DdataInfo ddataInfo,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile file)
    {
        try {
            List<MultipartFile> uploadFiles = new ArrayList<>();
            if (files != null && !files.isEmpty())
            {
                uploadFiles.addAll(files);
            }
            if (file != null && !file.isEmpty())
            {
                uploadFiles.add(file);
            }
            return AjaxResult.success(ddataService.insertDdataInfos(ddataInfo, uploadFiles));
        } catch (Exception e){
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:update')")
    @PutMapping("/update")
    @Log(title = "更新业务数据", businessType = BusinessType.UPDATE)
    public AjaxResult updateDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        try {
            return AjaxResult.success(ddataService.updateDdataInfo(ddataInfo));
        }
        catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:update')")
    @GetMapping("/movePathTree")
    public AjaxResult getMovePathTree()
    {
        try {
            return AjaxResult.success(ddataService.getMovePathTree());
        }
        catch (Exception e) {
            throw new ServiceException("查询系统路径失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:delete')")
    @DeleteMapping("/delete")
    @Log(title = "删除业务数据", businessType = BusinessType.DELETE)
    public AjaxResult deleteDdataInfos(@RequestBody List<Integer> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            throw new ServiceException("请选择要删除的业务数据");
        }

        try
        {
            ddataService.deleteDdataInfos(ids);
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
        return AjaxResult.success("删除成功");
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @PostMapping("/preview")
    public AjaxResult previewDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        if (StringUtils.isEmpty(ddataInfo.getExperimentId()) || StringUtils.isEmpty(ddataInfo.getDataFilePath()))
        {
            throw new ServiceException("预览参数不能为空");
        }
        DExperimentInfo experimentInfo = new DExperimentInfo();
        try {
            experimentInfo =
                dExperimentInfoService.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        }
        catch (Exception e) {
            throw new ServiceException("查询试验信息失败");
        }
        if (experimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        DProjectInfo projectInfo = new DProjectInfo();
        try {
            projectInfo =
                dProjectInfoService.selectDProjectInfoByProjectId(experimentInfo.getProjectId());
        }
        catch (Exception e) {
            throw new ServiceException("查询项目信息失败");
        }
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }

        int pageNum = ddataInfo.getPageNum() == null || ddataInfo.getPageNum() < 1 ? 1 : ddataInfo.getPageNum();
        int pageSize =
                ddataInfo.getPageSize() == null || ddataInfo.getPageSize() < 1
                        ? DEFAULT_PREVIEW_PAGE_SIZE
                        : ddataInfo.getPageSize();
        pageSize = Math.min(pageSize, MAX_PREVIEW_PAGE_SIZE);

        String relativePath = StringUtils.removeStart(ddataInfo.getDataFilePath(), "/");
        Path projectRoot = buildProjectRoot(projectInfo.getPath());
        Path experimentRoot = buildExperimentRoot(projectInfo.getPath(), experimentInfo.getPath());
        Path absolutePath = resolveDataFilePath(experimentRoot, relativePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(projectRoot, experimentRoot, absolutePath))
        {
            Map<String, Object> previewData =
                    FileUtils.previewFileByPage(absolutePath.toString(), pageNum, pageSize);
            return success(previewData);
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:download')")
    @PostMapping("/download")
    @Log(title = "下载业务数据文件", businessType = BusinessType.EXPORT)
    public void downloadDDataInfoFile(@RequestBody DdataInfo ddataInfo, HttpServletResponse response)
    {
        if (StringUtils.isEmpty(ddataInfo.getExperimentId()) || StringUtils.isEmpty(ddataInfo.getDataFilePath()))
        {
            throw new ServiceException("下载参数不能为空");
        }

        DExperimentInfo experimentInfo = new DExperimentInfo();
        try {
            experimentInfo =
                dExperimentInfoService.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        }
        catch (Exception e) {
            throw new ServiceException("查询试验信息失败");
        }
        if (experimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        DProjectInfo projectInfo = new DProjectInfo();
        try {
            projectInfo =
                dProjectInfoService.selectDProjectInfoByProjectId(experimentInfo.getProjectId());
        }
        catch (Exception e) {
            throw new ServiceException("查询项目信息失败");
        }
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }

        String relativePath = StringUtils.removeStart(ddataInfo.getDataFilePath(), "/");
        Path projectRoot = buildProjectRoot(projectInfo.getPath());
        Path experimentRoot = buildExperimentRoot(projectInfo.getPath(), experimentInfo.getPath());
        Path absolutePath = resolveDataFilePath(experimentRoot, relativePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(projectRoot, experimentRoot, absolutePath))
        {
            FileUtils.downloadFile(absolutePath.toString(), FileUtils.getName(relativePath), response);
        }
    }

    private Path buildProjectRoot(String projectPath)
    {
        return Paths.get(profilePath, StringUtils.removeStart(projectPath, "/")).normalize();
    }

    private Path buildExperimentRoot(String projectPath, String experimentPath)
    {
        return Paths.get(
                profilePath,
                StringUtils.removeStart(projectPath, "/"),
                StringUtils.removeStart(experimentPath, "/")
        ).normalize();
    }

    private Path resolveDataFilePath(Path experimentRoot, String relativePath)
    {
        Path absolutePath = experimentRoot.resolve(relativePath).normalize();
        if (!absolutePath.startsWith(experimentRoot))
        {
            throw new ServiceException("文件路径无效");
        }
        return absolutePath;
    }
}
