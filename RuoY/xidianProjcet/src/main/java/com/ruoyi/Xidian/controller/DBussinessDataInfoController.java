package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.*;
import com.ruoyi.Xidian.domain.DTO.*;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDTargetInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.uuid.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
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
    private DdataMapper ddataMapper;

    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @Autowired
    private PathLockManager pathLockManager;
    @Autowired
    private IDTargetInfoService iDTargetInfoService;
    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

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
    @Log(title = "查看业务数据详情", businessType = BusinessType.OTHER)
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

    @PostMapping("/insertByPath")
    @Log(title = "业务数据路径导入", businessType = BusinessType.INSERT)
    public AjaxResult insertDDataInfoByPath(@RequestBody DdataInfo ddataInfo)
    {
        try
        {
            return AjaxResult.success(ddataService.insertDdataInfoByPath(ddataInfo));
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/transport")
    @Log(title = "业务数据文件搬运", businessType = BusinessType.INSERT)
    public AjaxResult transportDDataFile(@RequestBody DdataInfo ddataInfo)
    {
        try
        {
            return AjaxResult.success(ddataService.transportDdataFile(ddataInfo));
        }
        catch (Exception e)
        {
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

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @PostMapping("/previewAll")
    public AjaxResult previewAllDDataInfo(@RequestBody DdataInfo ddataInfo)
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

        String relativePath = StringUtils.removeStart(ddataInfo.getDataFilePath(), "/");
        Path projectRoot = buildProjectRoot(projectInfo.getPath());
        Path experimentRoot = buildExperimentRoot(projectInfo.getPath(), experimentInfo.getPath());
        Path absolutePath = resolveDataFilePath(experimentRoot, relativePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(projectRoot, experimentRoot, absolutePath))
        {
            Map<String, Object> previewData =
                    FileUtils.previewFileByPage(absolutePath.toString(), 1, Integer.MAX_VALUE);
            Object total = previewData.get("total");
            previewData.put("pageNum", 1);
            previewData.put("pageSize", total == null ? 0 : total);
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

    @PreAuthorize("@ss.hasPermi('dataInfo:info:Rename')")
    @PutMapping("/rename")
    @Log(title = "规范重命名业务数据文件", businessType = BusinessType.UPDATE)
    public AjaxResult renameDDataInfoFile(@RequestBody List<DdataInfo> ddataInfo)
    {
        if (ddataInfo.isEmpty())
        {
            throw new ServiceException("规范重命名参数不能为空");
        }
        for (DdataInfo ddataInfoItem : ddataInfo)
        {
            if (StringUtils.isEmpty(ddataInfoItem.getExperimentId()) || StringUtils.isEmpty(ddataInfoItem.getDataName()))
            {
                throw new ServiceException("规范重命名参数不能为空");
            }
        }
        //重命名数据文件，数据名称后添加项目、试验名称
        if(ddataService.renameDataName(ddataInfo)!=0){
            return AjaxResult.success("命名成功");
        }else{
            return AjaxResult.error("命名失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:backup')")
    @PutMapping("/backup/{id}")
    @Log(title = "备份数据文件" ,businessType = BusinessType.UPDATE)
    public AjaxResult backupDdataInfoFile(@PathVariable Integer id){
        if(id == null){
            return AjaxResult.error("尚未选择数据");
        }
        if(ddataService.backupDataById(id)!=0){
            return AjaxResult.success("备份成功");
        }
        return AjaxResult.error("备份失败");
    }

    //获取备份数据
    @PreAuthorize("@ss.hasAnyPermi('dataInfo:info:backup,dataInfo:info:restore')")
    @GetMapping("/backup/list")
    public TableDataInfo getbackData(BackupData backupData){
        backupData.setIsRestored(0);
        startPage();
        try {
            return getDataTable(ddataService.selectBackupDataList(backupData));
        } catch (Exception e) {
            throw new ServiceException("查询备份数据列表失败");
        }
    }

    //还原删除的数据
    @PreAuthorize("@ss.hasPermi('dataInfo:info:restore')")
    @PostMapping("/back/restore/{id}")
    public AjaxResult restoreBackupData(@PathVariable Integer id){
        if(id == null){
            return AjaxResult.error("请选择要恢复的数据");
        }
        String msg = ddataService.restoreDataFile(id);
        if(msg == null) {
            return AjaxResult.success("恢复成功");
        }
        return AjaxResult.error(msg);
    }

    @GetMapping("/dbMgt/healthCheck")
    public ResponseEntity<Healthy> healthCheck() {
        Healthy healthy = new Healthy();
        healthy.setStatusCode(200);
        healthy.setStatusMsg("Service is running!");
        return ResponseEntity.ok(healthy);
    }

    @Anonymous
    @PostMapping("/dbMgt/saveICDData")
    public ResponseEntity<Healthy> insertData(@RequestBody ICDRequest icdRequest) {
        Healthy healthy = new Healthy();

        if (icdRequest == null) {
            healthy.setStatusCode(500);
            healthy.setStatusMsg("request body is empty!");
            return ResponseEntity.ok(healthy);
        }
        //插入目标信息
        DTargetInfo dTargetInfo = new DTargetInfo();
        dTargetInfo.setTargetType(icdRequest.getTargetInfo().getTargetType());
        dTargetInfo.setTargetName(icdRequest.getTargetInfo().getTargetName());
        DTargetInfo oldTarget = iDTargetInfoService.selectDTargetInfoList(dTargetInfo).get(0);
        if(oldTarget==null){
            dTargetInfo.setTargetId(UUID.randomUUID().toString());
            iDTargetInfoService.insertDTargetInfo(dTargetInfo);
        } else{
            dTargetInfo.setTargetId(oldTarget.getTargetId());
        }
        //插入项目信息
        DProjectInfo dProjectInfo = new DProjectInfo();
        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        dProjectInfo.setProjectName(icdRequest.getProjectInfo().getProjectName());
        dProjectInfo.setProjectContentDesc(icdRequest.getProjectInfo().getProjectDesc());
        dProjectInfo.setPath(icdRequest.getProjectInfo().getProjectPath());
        DProjectInfo oldProjectInfo = dProjectInfoMapper.selectSameNameProject(dProjectInfo.getProjectName());
        if(oldProjectInfo == null){
            dProjectInfoMapper.insertDProjectInfo(dProjectInfo);
            dExperimentInfo.setProjectId(dProjectInfo.getProjectId());
        } else {
            dExperimentInfo.setProjectId(oldProjectInfo.getProjectId());
        }
        //插入试验信息
        dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
        dExperimentInfo.setExperimentName(icdRequest.getExperimentInfo().getExperiementName());
        dExperimentInfo.setStartTime(DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH));
        dExperimentInfo.setPath(icdRequest.getExperimentInfo().getExperiementPath());
        dExperimentInfo.setTargetId(dTargetInfo.getTargetId());
        DExperimentInfo oldExperimentInfo = dExperimentInfoMapper.selectExperimentByProjectNameAndExperimentName(dExperimentInfo.getExperimentName(),dProjectInfo.getProjectName());
        if(oldExperimentInfo == null){
            dExperimentInfoMapper.insertDExperimentInfo(dExperimentInfo);
        }else{
            dExperimentInfo.setExperimentId(oldExperimentInfo.getExperimentId());
        }
        for(DataRelation dataRelation : icdRequest.getDataRelation()){
            //数据入库
            DdataInfo ddataInfo = new DdataInfo();
            ddataInfo.setExperimentId(dExperimentInfo.getExperimentId());
            ddataInfo.setDataName(dataRelation.getDataName());
            ddataInfo.setDataType(dataRelation.getDataType());
            ddataInfo.setDataFilePath(dataRelation.getDataFilePath());
            ddataInfo.setTargetId(dTargetInfo.getTargetId());
            ddataInfo.setTargetType(dTargetInfo.getTargetType());
            ddataInfo.setSampleFrequency(1000);
            ddataInfo.setWorkStatus("completed");
            ddataMapper.insertDdataInfo(ddataInfo);
        }
        healthy.setStatusCode(200);
        healthy.setStatusMsg("Data saved to db succesfully!");
        return ResponseEntity.ok(healthy);
    }

    @Anonymous
    @PostMapping("/dbMgt/dataQuery")
    public ResponseEntity<List<ICDRequest>> getData(@RequestBody DataQuery dataQuery) {
        //search from database
        //SELECT * FROM D_PROJECT_INFO dpi, D_EXPERIMENT_INFO dei, D_TARGET_INFO dti , MD_DATA_RELATION mdr
        //WHERE dpi.PROJECT_ID  = dei.PROJECT_ID
        //AND dei.TARGET_ID = dti.TARGET_ID
        //AND mdr.EXPERIMENT_ID = dei.EXPERIMENT_ID
        //AND mdr.TARGET_ID  = dti.TARGET_ID

        log.info("Query Param: {}", dataQuery);

        List<ICDRequest> list = new ArrayList<>();
        List<Map<String, Object>> queryResult = ddataMapper.selectIcdDataQueryList(dataQuery);

        if (queryResult == null || queryResult.isEmpty()) {
            return ResponseEntity.ok(list);
        }

        ICDRequest icdRequest = new ICDRequest();
        Map<String, Object> queryInfo = queryResult.get(0);

        ProjectInfoDTO projectInfo = new ProjectInfoDTO();
        projectInfo.setProjectName((String) queryInfo.get("PROJECTNAME"));
        projectInfo.setProjectDesc((String) queryInfo.get("PROJECTDESC"));
        projectInfo.setProjectPath((String) queryInfo.get("PROJECTPATH"));
        icdRequest.setProjectInfo(projectInfo);

        DExperimentInfo dExperimentInfo = dExperimentInfoMapper.selectExperimentByProjectNameAndExperimentName(dataQuery.getExperiementName(), dataQuery.getProjectName());
        TargetInfoDTO targetInfoDTO = new TargetInfoDTO();
        DTargetInfo dTargetInfo = iDTargetInfoService.selectDTargetInfoByTargetId(dExperimentInfo.getTargetId());
        targetInfoDTO.setTargetName(dTargetInfo.getTargetName());
        targetInfoDTO.setTargetType(dTargetInfo.getTargetType());
        icdRequest.setTargetInfo(targetInfoDTO);

        ExperimentInfoDTO experimentInfo = new ExperimentInfoDTO();
        experimentInfo.setExperiementName((String) queryInfo.get("EXPERIMENTNAME"));
        experimentInfo.setExperiementPath((String) queryInfo.get("EXPERIMENTPATH"));
        icdRequest.setExperimentInfo(experimentInfo);

        List<DataRelation> dataRelationList = new ArrayList<>();
        for (Map<String, Object> item : queryResult) {
            DataRelation dataRelation = new DataRelation();
            dataRelation.setDataName((String) item.get("DATANAME"));
            dataRelation.setDataType((String) item.get("DATATYPE"));
            dataRelation.setDataFilePath((String) item.get("DATAFILEPATH"));
            dataRelationList.add(dataRelation);
        }

        icdRequest.setDataRelation(dataRelationList);

        list.add(icdRequest);

        return ResponseEntity.ok(list);
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
