package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.*;
import com.ruoyi.Xidian.domain.DTO.*;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.mapper.MdFileStorageMapper;
import com.ruoyi.Xidian.service.*;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.uuid.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import java.util.stream.Collectors;

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
    private IDTargetInfoService iDTargetInfoService;
    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private MdFileStorageMapper mdFileStorageMapper;

    @Autowired
    private FileStorageService fileStorageService;

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
    @PostMapping(value = "/insert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Log(title = "导入业务数据", businessType = BusinessType.INSERT)
    public AjaxResult insertDDataInfo(
            @ModelAttribute DdataInfo ddataInfo,
            @RequestParam(value = "files" ) List<MultipartFile> files)
    {
        if (files == null || files.isEmpty()) {
            return error("请选择要上传的文件");
        }

        files = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            return error("请选择有效文件");
        }

        Long userId = SecurityUtils.getUserId();
        List<UploadedFileInfo> uploadedFileInfoList = new ArrayList<>();
        for (MultipartFile multipartFile : files){
            UploadedFileInfo uploadedFileInfo = new UploadedFileInfo();
            try {
                String ObjectName = fileStorageService.upload(multipartFile, userId);
                uploadedFileInfo.setObjectName(ObjectName);
                uploadedFileInfo.setOriginalFilename(multipartFile.getOriginalFilename());
                uploadedFileInfo.setContentType(multipartFile.getContentType());
                uploadedFileInfo.setSize(multipartFile.getSize());
                uploadedFileInfoList.add(uploadedFileInfo);
            } catch (RuntimeException e) {
                log.error("文件上传失败: {}", multipartFile.getOriginalFilename(), e);
            }
        }
        if(uploadedFileInfoList.isEmpty()) return error("文件全部上传失败");
        //落库处理
        Integer importedCount = ddataService.insertDdataInfosByObjectNames(ddataInfo, uploadedFileInfoList);
        return success(importedCount);
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:update')")
    @PutMapping("/update")
    @Log(title = "更新业务数据", businessType = BusinessType.UPDATE)
    public AjaxResult updateDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null) {
            return error("数据ID不能为空");
        }
        if(ddataService.updateDdataInfo(ddataInfo) == 0){
            return error("更新失败，请重试");
        }
        return AjaxResult.success("更新成功");
    
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
    @DeleteMapping("/delete/{id}")
    @Log(title = "删除业务数据", businessType = BusinessType.DELETE)
    public AjaxResult deleteDdataInfoById(@PathVariable Integer id){
        if(id == null){
            return error("请选择需要删除的数据");
        }
        if(ddataService.deleteDataInfoById(id) ==0){
            return error("删除失败，请重试");
        }
        return success("删除成功");
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:delete')")
    @DeleteMapping("/delete")
    @Log(title = "删除业务数据", businessType = BusinessType.DELETE)
    public AjaxResult deleteDdataInfos(@RequestBody List<Integer> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return error("请选择要删除的文件");
        }
        if(ddataService.deleteDdataInfos(ids) == 0)
            return error("删除失败");
        return AjaxResult.success("删除成功");
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @PostMapping("/preview")
    public AjaxResult previewDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null) {
            return error("预览参数不能为空");
        }

        DdataInfo dataInfo = ddataService.selectDdataInfoByDdataId(ddataInfo.getId());
        if (dataInfo == null) {
            return error("数据不存在");
        }
        return success(buildStoragePreviewResult(dataInfo, ddataInfo));
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @GetMapping("/preview/file/{id}")
    public void previewDDataInfoFile(@PathVariable Integer id, HttpServletResponse response)
    {
        if (id == null) {
            throw new ServiceException("预览参数不能为空");
        }

        DdataInfo dataInfo = ddataService.selectDdataInfoByDdataId(id);
        if (dataInfo == null || dataInfo.getStorageFileId() == null) {
            throw new ServiceException("预览文件不存在");
        }

        MdFileStorage mdFileStorage = mdFileStorageMapper.selectById(dataInfo.getStorageFileId());
        if (mdFileStorage == null || StringUtils.isEmpty(mdFileStorage.getObjectName())) {
            throw new ServiceException("预览文件不存在");
        }

        fileStorageService.preview(
                mdFileStorage.getObjectName(),
                mdFileStorage.getOriginalFileName(),
                mdFileStorage.getContentType(),
                response
        );
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:preview')")
    @PostMapping("/previewAll")
    public AjaxResult previewAllDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("预览参数不能为空");
        }

        DdataInfo dataInfo = ddataService.selectDdataInfoByDdataId(ddataInfo.getId());
        if (dataInfo == null)
        {
            throw new ServiceException("数据不存在");
        }

        return success(buildStoragePreviewResult(dataInfo, ddataInfo));
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:download')")
    @PostMapping("/download")
    @Log(title = "下载业务数据文件", businessType = BusinessType.EXPORT)
    public void downloadDDataInfoFile(@RequestBody DdataInfo ddataInfo, HttpServletResponse response)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("下载参数不能为空");
        }

        DdataInfo dataInfo = ddataService.selectDdataInfoByDdataId(ddataInfo.getId());
        if (dataInfo == null)
        {
            throw new ServiceException("数据不存在");
        }

        if (dataInfo.getStorageFileId() != null)
        {
            MdFileStorage mdFileStorage = mdFileStorageMapper.selectById(dataInfo.getStorageFileId());
            if (mdFileStorage != null && StringUtils.isNotEmpty(mdFileStorage.getObjectName()))
            {
                fileStorageService.download(
                        mdFileStorage.getObjectName(),
                        StringUtils.defaultIfBlank(mdFileStorage.getOriginalFileName(), dataInfo.getDataName()),
                        response
                );
            }
        }
    }

    private Map<String, Object> buildStoragePreviewResult(DdataInfo dataInfo, DdataInfo requestData)
    {
        if (dataInfo.getStorageFileId() == null)
        {
            throw new ServiceException("预览文件不存在");
        }

        MdFileStorage mdFileStorage = mdFileStorageMapper.selectById(dataInfo.getStorageFileId());
        if (mdFileStorage == null || StringUtils.isEmpty(mdFileStorage.getObjectName()))
        {
            throw new ServiceException("预览文件不存在");
        }

        String originalFileName = StringUtils.defaultIfBlank(mdFileStorage.getOriginalFileName(), dataInfo.getDataName());
        String lowerFileName = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        if (isBinaryPreviewExtension(lowerFileName))
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("previewType", "unsupported");
            result.put("message", "暂不支持预览二进制文件，请下载后查看");
            result.put("rows", new ArrayList<>());
            result.put("total", 0);
            result.put("pageNum", 1);
            result.put("pageSize", 0);
            result.put("fileName", originalFileName);
            return result;
        }

        if (isInlinePreviewExtension(lowerFileName))
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("previewType", resolveInlinePreviewType(lowerFileName));
            result.put("mimeType", StringUtils.defaultIfBlank(mdFileStorage.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE));
            result.put("message", "");
            result.put("url", "/data/bussiness/preview/file/" + dataInfo.getId());
            result.put("rows", new ArrayList<>());
            result.put("total", 1);
            result.put("pageNum", 1);
            result.put("pageSize", 1);
            result.put("fileName", originalFileName);
            return result;
        }

        int pageNum = requestData == null || requestData.getPageNum() == null ? 1 : requestData.getPageNum();
        int pageSize = requestData == null || requestData.getPageSize() == null ? 200 : requestData.getPageSize();
        Map<String, Object> result = fileStorageService.previewByPage(
                mdFileStorage.getObjectName(),
                originalFileName,
                pageNum,
                pageSize
        );
        result.put("fileName", originalFileName);
        return result;
    }

    private boolean isInlinePreviewExtension(String lowerFileName)
    {
        return lowerFileName.endsWith(".pdf")
                || lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg")
                || lowerFileName.endsWith(".png")
                || lowerFileName.endsWith(".mp3")
                || lowerFileName.endsWith(".mp4");
    }

    private boolean isBinaryPreviewExtension(String lowerFileName)
    {
        return lowerFileName.endsWith(".bin")
                || lowerFileName.endsWith(".dat")
                || lowerFileName.endsWith(".raw");
    }

    private String resolveInlinePreviewType(String lowerFileName)
    {
        if (lowerFileName.endsWith(".pdf"))
        {
            return "pdf";
        }
        if (lowerFileName.endsWith(".mp3"))
        {
            return "audio";
        }
        if (lowerFileName.endsWith(".mp4"))
        {
            return "video";
        }
        return "image";
    }

    @PreAuthorize("@ss.hasPermi('dataInfo:info:Rename')")
    @PutMapping("/rename")
    @Log(title = "规范重命名业务数据文件", businessType = BusinessType.UPDATE)
    public AjaxResult renameDDataInfoFile(@RequestBody List<DdataInfo> ddataInfo)
    {
        if (ddataInfo.isEmpty())
        {
            return AjaxResult.error("规范重命名参数不能为空");
        }
        for (DdataInfo ddataInfoItem : ddataInfo)
        {
            if (StringUtils.isEmpty(ddataInfoItem.getExperimentId()) || StringUtils.isEmpty(ddataInfoItem.getDataName()))
            {
                return AjaxResult.error("规范重命名参数不能为空");
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
        dProjectInfo.setPath("/" + icdRequest.getProjectInfo().getProjectName());
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
    public ResponseEntity<Map<String, List<ICDRequest>>> getData(@RequestBody DataQuery dataQuery) {
        //search from database
        //SELECT * FROM D_PROJECT_INFO dpi, D_EXPERIMENT_INFO dei, D_TARGET_INFO dti , MD_DATA_RELATION mdr
        //WHERE dpi.PROJECT_ID  = dei.PROJECT_ID
        //AND dei.TARGET_ID = dti.TARGET_ID
        //AND mdr.EXPERIMENT_ID = dei.EXPERIMENT_ID
        //AND mdr.TARGET_ID  = dti.TARGET_ID

        log.info("Query Param: {}", dataQuery);

        List<ICDRequest> list = new ArrayList<>();
        List<Map<String, Object>> queryResult = ddataMapper.selectIcdDataQueryList(dataQuery);
        Map<String, List<ICDRequest>> map = new HashMap<>();

        if (queryResult == null || queryResult.isEmpty()) {
            return ResponseEntity.ok(map);
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
        map.put("data", list);

        return ResponseEntity.ok(map);
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
