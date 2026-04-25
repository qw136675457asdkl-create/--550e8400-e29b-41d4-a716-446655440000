package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.*;
import com.ruoyi.Xidian.mapper.*;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.Xidian.utils.NickNameUtil;
import com.ruoyi.Xidian.utils.RegexUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DdataServiceImpl implements IDdataService
{
    private static final Logger log = LoggerFactory.getLogger(DdataServiceImpl.class);
    private static final Set<String> EXPERIMENT_ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("zip", "csv", "xls", "xlsx", "txt", "json", "doc", "docx", "pdf", "bin", "dat", "raw", "png" , "jpg" ,"jpeg","mp3","mp4")
    );

    @Autowired
    private DdataMapper ddataMapper;

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private DTargetInfoMapper dTargetInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private PathLockManager pathLockManager;

    @Autowired
    private BackDataMapper backDataMapper;

    @Autowired
    private IDProjectInfoService projectInfoService;

    @Autowired
    private IDExperimentInfoService dExperimentInfoService;

    private final String profile = RuoYiConfig.getProfile() + "/data";
    private final String backUPdir = RuoYiConfig.getBackupDir();
    private final String backAndRestore = RuoYiConfig.getBackAndRestore();

    @Override
    public List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo)
    {
        return ddataMapper.selectDdataInfoList(ddataInfo);
    }

    private void uploadZipArchive(MultipartFile file, DExperimentInfo experimentInfo, String archivePath)
    {
        boolean hasUploadedEntry = false;
        String archiveParentPath = extractDirectory(archivePath);

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String entryPath = buildArchiveEntryPath(archiveParentPath, entry.getName());
                if (shouldSkipExperimentPath(entryPath))
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String extension = extractExtensionName(entryPath);
                //检查文件拓展名
                assertExperimentExtension(extension, entryPath);
                storeExperimentFile(experimentInfo, entryPath, zipInputStream);
                hasUploadedEntry = true;
                zipInputStream.closeEntry();
            }
        }
        catch (IOException e)
        {
            log.error("解压ZIP文件失败: {}", e.getMessage(), e);
            throw new ServiceException("解压ZIP文件失败: " + e.getMessage());
        }

        if (!hasUploadedEntry)
        {
            log.warn("数据文件中没有可上传的文件");
            throw new ServiceException("没有可上传的文件");
        }
    }

    private void storeExperimentFile(DExperimentInfo experimentInfo, String relativePath, InputStream inputStream)
            throws IOException
    {
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        //上传的数据文件相对所属试验的路径
        String normalizedPath = normalizeExperimentUploadPath(relativePath);
        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        String storagePath = buildExperimentStoragePath(normalizedPath);
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildLockPaths(projectRoot, experimentRoot),
                buildLockPaths(targetPath)))
        {
            Path parentPath = targetPath.getParent();
            if (parentPath != null && Files.notExists(parentPath))
            {
                log.info("创建目录: {}", parentPath);
                Files.createDirectories(parentPath);
            }
            //如果相同目录下存在重名文件，加上后缀处理冲突
            storagePath = resolveAvailableExperimentStoragePath(
                    experimentInfo.getExperimentId(),
                    experimentRoot,
                    storagePath
            );
            targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            DdataInfo ddataInfo = buildExperimentUploadDataInfo(experimentInfo, normalizedPath, storagePath);
            DdataInfo oldInfo = ddataMapper.selectSameNameFile(experimentInfo.getExperimentId(), storagePath);
            if (oldInfo != null)
            {
                log.info("数据库中已存在相同文件: {}", oldInfo);
                //处理数据库非空冲突
                mergeExistingDataInfo(ddataInfo, oldInfo);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + oldInfo.getId());
                ddataMapper.updateDdataInfo(ddataInfo);
                return;
            }

            ddataMapper.insertDdataInfo(ddataInfo);
        }
    }

    private DdataInfo buildExperimentUploadDataInfo(
            DExperimentInfo experimentInfo,
            String relativePath,
            String storagePath)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(experimentInfo.getTargetId());
        ddataInfo.setTargetType(resolveExperimentTargetType(experimentInfo));
        ddataInfo.setDataName(extractFileName(relativePath));
        ddataInfo.setDataType(resolveExperimentDataType(relativePath));
        ddataInfo.setDataFilePath(normalizeDataFilePath(storagePath));
        ddataInfo.setIsSimulation(Boolean.TRUE);
        ddataInfo.setSampleFrequency(1000);
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setCreateBy(NickNameUtil.getNickName());
        return ddataInfo;
    }

    private String buildExperimentStoragePath(String relativePath)
    {
        return normalizeDataFilePath(relativePath);
    }

    private String resolveExperimentTargetType(DExperimentInfo experimentInfo)
    {
        if (experimentInfo == null || StringUtils.isEmpty(experimentInfo.getTargetId()))
        {
            return null;
        }
        if (StringUtils.isNotEmpty(experimentInfo.getTargetType()))
        {
            return experimentInfo.getTargetType();
        }
        if (dTargetInfoMapper.selectDTargetInfoByTargetId(experimentInfo.getTargetId()) == null)
        {
            return null;
        }
        return dTargetInfoMapper.selectDTargetInfoByTargetId(experimentInfo.getTargetId()).getTargetType();
    }

    private String resolveExperimentDataType(String relativePath)
    {
        String extension = extractExtensionName(relativePath);
        return StringUtils.isEmpty(extension) ? "file" : extension;
    }

    private String extractFileName(String relativePath)
    {
        String normalizedPath = normalizeDataFilePath(relativePath);
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    private void assertExperimentExtension(String extension, String relativePath)
    {
        if (StringUtils.isEmpty(extension) || !EXPERIMENT_ALLOWED_EXTENSIONS.contains(extension))
        {
            log.warn("文件扩展名错误: {} {}", extension, relativePath);
            throw new ServiceException("文件扩展名错误: " + extension + " " + relativePath);
        }
    }

    //获取文件后缀，如pdf
    private String extractExtensionName(String path)
    {
        String suffix = extractSuffix(path);
        return StringUtils.isEmpty(suffix) ? "" : suffix.substring(1).toLowerCase(Locale.ROOT);
    }

    private String buildArchiveEntryPath(String archiveParentPath, String entryName)
    {
        String normalizedEntryPath = normalizeExperimentUploadPath(entryName);
        if ("/".equals(archiveParentPath))
        {
            return normalizedEntryPath;
        }
        return normalizeExperimentUploadPath(
                archiveParentPath + "/" + StringUtils.removeStart(normalizedEntryPath, "/"));
    }

    private boolean shouldSkipExperimentPath(String relativePath)
    {
        String normalizedPath = StringUtils.removeStart(normalizeDataFilePath(relativePath), "/");
        String[] segments = normalizedPath.split("/");
        for (String segment : segments)
        {
            String current = segment == null ? "" : segment.trim();
            if (current.isEmpty())
            {
                continue;
            }
            if ("__MACOSX".equalsIgnoreCase(current) || ".DS_Store".equalsIgnoreCase(current)
                    || "Thumbs.db".equalsIgnoreCase(current) || current.startsWith("._"))
            {
                return true;
            }
        }
        return false;
    }

    private String normalizeExperimentUploadPath(String rawPath)
    {
        if (StringUtils.isEmpty(rawPath))
        {
            throw new ServiceException("上传路径不能为空");
        }

        String candidate = rawPath.trim();
        if (candidate.matches("^[a-zA-Z]:[\\\\/].*"))
        {
            int separatorIndex = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
            candidate = separatorIndex >= 0 ? candidate.substring(separatorIndex + 1) : candidate;
        }

        candidate = candidate.replace("\\", "/");
        while (candidate.startsWith("/"))
        {
            candidate = candidate.substring(1);
        }

        List<String> parts = new ArrayList<>();
        for (String segment : candidate.split("/"))
        {
            String current = segment == null ? "" : segment.trim();
            if (current.isEmpty() || ".".equals(current))
            {
                continue;
            }
            if ("..".equals(current))
            {
                throw new ServiceException("上传路径不能包含 .. 段落");
            }
            if (containsIllegalWindowsChar(current))
            {
                throw new ServiceException("上传路径包含非法字符: " + current);
            }
            parts.add(current);
        }

        if (parts.isEmpty())
        {
            throw new ServiceException("上传路径不能为空");
        }
        return "/" + String.join("/", parts);
    }

    private boolean containsIllegalWindowsChar(String name)
    {
        return name.contains(":") || name.contains("*") || name.contains("?") || name.contains("\"")
                || name.contains("<") || name.contains(">") || name.contains("|");
    }

    @Override
    public DdataInfo selectDdataInfoByDdataId(Integer id)
    {
        String cacheKey = CacheConstants.DATA_INFO_KEY + id;
        DdataInfo cachedDdataInfo = redisCache.getCacheObject(cacheKey);
        if (cachedDdataInfo != null)
        {
            return cachedDdataInfo;
        }

        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setId(id);
        List<DdataInfo> records = ddataMapper.selectDdataInfoList(ddataInfo);
        if (records == null || records.isEmpty())
        {
            throw new ServiceException("数据记录不存在");
        }

        DdataInfo result = records.get(0);
        result.setFullPath("./data" + BuildDataFilePath(result) + result.getDataFilePath());
        redisCache.setCacheObject(cacheKey, result, 30, TimeUnit.MINUTES);
        return result;
    }
    @Override
    public int renameDataName(List<DdataInfo> ddataInfos){
        //重命名数据文件，数据名称后添加项目、试验名称
        ddataInfos.forEach(item -> {
            if(RegexUtils.findFirst(item.getDataName(),"_" + item.getProjectName() + "_" +item.getExperimentName()) != null){
                return;
            }
            String baseName = extractBaseName("/" + item.getDataName());
            String extension = extractExtensionName("/" + item.getDataName());
            item.setDataName(baseName + "_" + item.getProjectName() + "_" + item.getExperimentName() + "." + extension);
        });
        try{
            ddataMapper.updateDdataInfos(ddataInfos);
            for(DdataInfo ddataInfo:ddataInfos){
                log.info("重命名数据文件: {}", ddataInfo);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + ddataInfo.getId());
            }
        } catch (Exception e) {
            return 0;
        }
        return 1;
    }
    @Override
    public Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file)
    {
        List<MultipartFile> uploadFiles = new ArrayList<>();
        if (file != null)
        {
            log.info("上传文件: {}", file.getOriginalFilename());
            uploadFiles.add(file);
        }
        return insertDdataInfos(ddataInfo, uploadFiles);
    }

    @Override
    public Integer insertDdataInfos(DdataInfo ddataInfo, List<MultipartFile> files)
    {
        if (files == null || files.isEmpty())
        {
            log.warn("文件不能为空");
            throw new ServiceException("文件不能为空");
        }

        boolean allowCustomDataName = canUseCustomBusinessDataName(ddataInfo, files);
        int successCount = 0;
        for (MultipartFile file : files)
        {
            if (file == null || file.isEmpty())
            {
                log.warn("文件为空: {}", file.getOriginalFilename());
                continue;
            }
            String uploadPath = normalizeExperimentUploadPath(file.getOriginalFilename());
            if (shouldSkipExperimentPath(uploadPath))
            {
                log.warn("跳过文件: {}", uploadPath);
                continue;
            }

            String extension = extractExtensionName(uploadPath);
            if ("zip".equals(extension))
            {
                log.info("上传压缩文件: {}", uploadPath);
                successCount += uploadBusinessZipArchive(file, ddataInfo, uploadPath);
                continue;
            }

            assertExperimentExtension(extension, uploadPath);
            try (InputStream inputStream = file.getInputStream())
            {
                log.info("上传文件: {}", uploadPath);
                storeBusinessImportFile(ddataInfo, uploadPath, inputStream, allowCustomDataName);
                successCount++;
            }
            catch (IOException e)
            {
                log.error("文件上传失败: {}", uploadPath, e);
                throw new ServiceException("文件上传失败: " + e.getMessage());
            }
        }

        if (successCount == 0)
        {
            log.warn("文件上传失败");
            throw new ServiceException("文件上传失败");
        }
        return successCount;
    }
    //登记数据信息
    @Override
    public Integer insertDdataInfoByPath(DdataInfo ddataInfo)
    {
        if (ddataInfo == null)
        {
            log.warn("导入参数不能为空");
            throw new ServiceException("导入参数不能为空");
        }

        String projectName = normalizeOptionalText(ddataInfo.getProjectName());
        String experimentName = normalizeOptionalText(ddataInfo.getExperimentName());
        String dataName = normalizeOptionalText(ddataInfo.getDataName());
        String dataType = normalizeOptionalText(ddataInfo.getDataType());
        if (StringUtils.isEmpty(projectName)
                || StringUtils.isEmpty(experimentName)
                || StringUtils.isEmpty(dataName)
                || StringUtils.isEmpty(dataType)
                || StringUtils.isEmpty(ddataInfo.getDataFilePath()))
        {
            log.warn("导入参数不能为空");
            throw new ServiceException("项目名称、试验名称、数据名称、数据类型、数据路径不能为空");
        }

        DProjectInfo projectInfo = dProjectInfoMapper.selectSameNameProject(projectName);
        if (projectInfo == null)
        {
            log.warn("项目不存在");
            throw new ServiceException("项目不存在");
        }

        DExperimentInfo experimentInfo =
                dExperimentInfoMapper.selectSamePathExperiment(experimentName, projectInfo.getProjectId());
        if (experimentInfo == null)
        {
            log.warn("试验信息不存在");
            throw new ServiceException("试验信息不存在");
        }

        String normalizedDataFilePath = normalizeDataFilePath(ddataInfo.getDataFilePath());
        assertExperimentExtension(extractExtensionName(normalizedDataFilePath), normalizedDataFilePath);

        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        Path absolutePath = resolveAbsoluteDataPath(experimentRoot, normalizedDataFilePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(projectRoot, experimentRoot, absolutePath))
        {
            if (Files.notExists(absolutePath) || Files.isDirectory(absolutePath))
            {
                log.warn("数据文件不存在: {}", absolutePath);
                throw new ServiceException("数据文件不存在");
            }

            DdataInfo insertDataInfo =
                    buildBusinessPathImportDataInfo(ddataInfo, experimentInfo, normalizedDataFilePath);
            DdataInfo oldInfo = ddataMapper.selectSameNameFile(experimentInfo.getExperimentId(), normalizedDataFilePath);
            if (oldInfo != null)
            {
                log.warn("数据文件已存在: {}", absolutePath);
                mergeExistingDataInfo(insertDataInfo, oldInfo);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + oldInfo.getId());
                ddataMapper.updateDdataInfo(insertDataInfo);
                return 1;
            }

            return ddataMapper.insertDdataInfo(insertDataInfo);
        }
    }

    private DdataInfo buildBusinessPathImportDataInfo(
            DdataInfo source,
            DExperimentInfo experimentInfo,
            String dataFilePath)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(StringUtils.isNotEmpty(source.getTargetId())
                ? normalizeOptionalText(source.getTargetId())
                : experimentInfo.getTargetId());
        ddataInfo.setTargetType(StringUtils.isNotEmpty(source.getTargetType())
                ? normalizeOptionalText(source.getTargetType())
                : resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(normalizeOptionalText(source.getTargetCategory()));
        ddataInfo.setDataName(normalizeOptionalText(source.getDataName()));
        ddataInfo.setDataType(normalizeOptionalText(source.getDataType()));
        ddataInfo.setDataFilePath(dataFilePath);
        ddataInfo.setDeviceId(normalizeOptionalText(source.getDeviceId()));
        ddataInfo.setDeviceInfo(normalizeOptionalText(source.getDeviceInfo()));
        ddataInfo.setSampleFrequency(source.getSampleFrequency() == null || source.getSampleFrequency() <= 0
                ? 1000
                : source.getSampleFrequency());
        ddataInfo.setWorkStatus(StringUtils.isNotEmpty(source.getWorkStatus())
                ? normalizeOptionalText(source.getWorkStatus())
                : "completed");
        ddataInfo.setExtAttr(source.getExtAttr());
        ddataInfo.setIsSimulation(source.getIsSimulation() == null ? Boolean.TRUE : source.getIsSimulation());
        ddataInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? normalizeOptionalText(source.getCreateBy())
                : NickNameUtil.getNickName());
        ddataInfo.setCreateTime(source.getCreateTime());
        return ddataInfo;
    }

    @Override
    public Integer transportDdataFile(DdataInfo ddataInfo)
    {
        if (ddataInfo == null)
        {
            throw new ServiceException("数据参数不能为空");
        }

        String projectName = ddataInfo.getProjectName() == null ? null : ddataInfo.getProjectName().trim();
        String experimentName = ddataInfo.getExperimentName() == null ? null : ddataInfo.getExperimentName().trim();
        String sourceFullPath = ddataInfo.getFullPath() == null ? null : ddataInfo.getFullPath().trim();
        if (StringUtils.isEmpty(projectName)
                || StringUtils.isEmpty(experimentName)
                || StringUtils.isEmpty(sourceFullPath))
        {
            log.warn("导入参数不能为空");
            throw new ServiceException("项目名称、试验名称、源文件路径不能为空");
        }

        Path sourcePath = Paths.get(sourceFullPath).normalize();
        if (Files.notExists(sourcePath) || Files.isDirectory(sourcePath))
        {
            log.warn("源数据文件不存在: {}", sourcePath);
            throw new ServiceException("源数据文件不存在");
        }

        DProjectInfo projectInfo = ensureTransportProject(projectName);
        DExperimentInfo experimentInfo = ensureTransportExperiment(projectInfo, experimentName, ddataInfo);

        String sourceFileName = sourcePath.getFileName().toString();
        if (StringUtils.isEmpty(sourceFileName))
        {
            log.warn("源数据文件名无效: {}", sourcePath);
            throw new ServiceException("源数据文件名无效");
        }

        String requestedDataFilePath = StringUtils.isNotEmpty(ddataInfo.getDataFilePath())
                ? normalizeDataFilePath(ddataInfo.getDataFilePath())
                : buildImportedDataFilePath(sourceFileName);

        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        String storagePath = resolveAvailableExperimentStoragePath(
                experimentInfo.getExperimentId(),
                experimentRoot,
                requestedDataFilePath
        );
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildLockPaths(projectRoot, experimentRoot, sourcePath),
                buildLockPaths(targetPath)))
        {
            if (Files.notExists(sourcePath) || Files.isDirectory(sourcePath))
            {
                log.warn("源数据文件不存在: {}", sourcePath);
                throw new ServiceException("源数据文件不存在");
            }

            Path targetParent = targetPath.getParent();
            if (targetParent != null && Files.notExists(targetParent))
            {
                log.warn("目标数据文件路径不存在: {}", targetParent);
                Files.createDirectories(targetParent);
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.warn("搬运数据文件失败: {}", e.getMessage());
            throw new ServiceException("搬运数据文件失败: " + e.getMessage());
        }

        DdataInfo insertDataInfo = buildTransportDataInfo(
                ddataInfo,
                experimentInfo,
                storagePath,
                sourceFileName
        );
        return ddataMapper.insertDdataInfo(insertDataInfo);
    }

    private DProjectInfo ensureTransportProject(String projectName)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectSameNameProject(projectName);
        if (projectInfo != null)
        {
            log.warn("项目已存在: {}", projectName);
            return projectInfo;
        }

        Path projectRoot = Paths.get(
                profile,
                StringUtils.removeStart("/" + projectName, "/")
        ).normalize();
        DProjectInfo newProjectInfo = new DProjectInfo();
        newProjectInfo.setProjectName(projectName);
        newProjectInfo.setCreateBy(NickNameUtil.getNickName());
        newProjectInfo.setPath("/" + projectName);
        if (Files.exists(projectRoot))
        {
            log.warn("项目目录已存在: {}", projectRoot);
            dProjectInfoMapper.insertDProjectInfo(newProjectInfo);
        }
        else
        {
            projectInfoService.insertDProjectInfo(newProjectInfo);
        }

        DProjectInfo createdProjectInfo = dProjectInfoMapper.selectSameNameProject(projectName);
        if (createdProjectInfo == null)
        {
            log.warn("项目创建失败: {}", projectName);
            throw new ServiceException("项目创建失败");
        }
        return createdProjectInfo;
    }

    private DExperimentInfo ensureTransportExperiment(
            DProjectInfo projectInfo,
            String experimentName,
            DdataInfo source)
    {
        DExperimentInfo experimentInfo =
                dExperimentInfoMapper.selectSamePathExperiment(experimentName, projectInfo.getProjectId());
        if (experimentInfo != null)
        {
            return experimentInfo;
        }

        Path experimentRoot = Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/"),
                StringUtils.removeStart("/" + experimentName, "/")
        ).normalize();
        DExperimentInfo newExperimentInfo = new DExperimentInfo();
        newExperimentInfo.setExperimentId(UUID.randomUUID().toString());
        newExperimentInfo.setTargetId(source.getTargetId());
        newExperimentInfo.setExperimentName(experimentName);
        newExperimentInfo.setProjectId(projectInfo.getProjectId());
        newExperimentInfo.setStartTime(new Date());
        newExperimentInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? source.getCreateBy().trim()
                : NickNameUtil.getNickName());
        newExperimentInfo.setPath("/" + experimentName);
        if (Files.exists(experimentRoot))
        {
            log.warn("试验目录已存在: {}", experimentRoot);
            dExperimentInfoMapper.insertDExperimentInfo(newExperimentInfo);
        }
        else
        {
            dExperimentInfoService.insertDExperimentInfo(newExperimentInfo);
        }

        DExperimentInfo createdExperimentInfo =
                dExperimentInfoMapper.selectSamePathExperiment(experimentName, projectInfo.getProjectId());
        if (createdExperimentInfo == null)
        {
            log.warn("试验创建失败: {}", experimentName);
            throw new ServiceException("试验创建失败");
        }
        return createdExperimentInfo;
    }

    private DdataInfo buildTransportDataInfo(
            DdataInfo source,
            DExperimentInfo experimentInfo,
            String dataFilePath,
            String sourceFileName)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(StringUtils.isNotEmpty(source.getTargetId())
                ? source.getTargetId().trim()
                : experimentInfo.getTargetId());
        ddataInfo.setTargetType(StringUtils.isNotEmpty(source.getTargetType())
                ? source.getTargetType().trim()
                : resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(source.getTargetCategory());
        ddataInfo.setDataName(StringUtils.isNotEmpty(source.getDataName())
                ? source.getDataName().trim()
                : sourceFileName);
        ddataInfo.setDataType(StringUtils.isNotEmpty(source.getDataType())
                ? source.getDataType().trim()
                : resolveExperimentDataType(dataFilePath));
        ddataInfo.setDataFilePath(dataFilePath);
        ddataInfo.setDeviceId(source.getDeviceId());
        ddataInfo.setDeviceInfo(source.getDeviceInfo());
        ddataInfo.setSampleFrequency(source.getSampleFrequency() == null || source.getSampleFrequency() <= 0
                ? 1000
                : source.getSampleFrequency());
        ddataInfo.setWorkStatus(StringUtils.isNotEmpty(source.getWorkStatus())
                ? source.getWorkStatus().trim()
                : "completed");
        ddataInfo.setExtAttr(source.getExtAttr());
        ddataInfo.setIsSimulation(source.getIsSimulation() == null ? Boolean.TRUE : source.getIsSimulation());
        ddataInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? source.getCreateBy().trim()
                : NickNameUtil.getNickName());
        return ddataInfo;
    }

    private int uploadBusinessZipArchive(MultipartFile file, DdataInfo ddataInfo, String archivePath)
    {
        boolean hasUploadedEntry = false;
        int uploadCount = 0;
        String archiveParentPath = extractDirectory(archivePath);

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String entryPath = buildArchiveEntryPath(archiveParentPath, entry.getName());
                if (shouldSkipExperimentPath(entryPath))
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String extension = extractExtensionName(entryPath);
                assertExperimentExtension(extension, entryPath);
                storeBusinessImportFile(ddataInfo, entryPath, zipInputStream, false);
                hasUploadedEntry = true;
                uploadCount++;
                zipInputStream.closeEntry();
            }
        }
        catch (IOException e)
        {
            log.warn("文件上传失败: {}", e.getMessage());
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }

        if (!hasUploadedEntry)
        {
            log.warn("文件上传失败: {}", archivePath);
            throw new ServiceException("文件上传失败");
        }
        return uploadCount;
    }

    private void storeBusinessImportFile(
            DdataInfo template,
            String relativePath,
            InputStream inputStream,
            boolean allowCustomDataName) throws IOException
    {
        DExperimentInfo experimentInfo = requireExperiment(template.getExperimentId());
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        String normalizedPath = normalizeExperimentUploadPath(relativePath);
        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        String storagePath = buildExperimentStoragePath(normalizedPath);
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildLockPaths(projectRoot, experimentRoot),
                buildLockPaths(targetPath)))
        {
            Path parentPath = targetPath.getParent();
            if (parentPath != null && Files.notExists(parentPath))
            {
                log.warn("创建目录失败: {}", parentPath);
                Files.createDirectories(parentPath);
            }

            storagePath = resolveAvailableExperimentStoragePath(
                    template.getExperimentId(),
                    experimentRoot,
                    storagePath
            );
            targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            DdataInfo insertDataInfo = buildBusinessImportDataInfo(
                    template,
                    normalizedPath,
                    storagePath,
                    allowCustomDataName
            );
            ddataMapper.insertDdataInfo(insertDataInfo);
        }
    }

    private DdataInfo buildBusinessImportDataInfo(
            DdataInfo template,
            String relativePath,
            String storagePath,
            boolean allowCustomDataName)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(template.getExperimentId());
        ddataInfo.setTargetId(template.getTargetId());
        ddataInfo.setTargetType(resolveBusinessImportTargetType(template));
        ddataInfo.setTargetCategory(template.getTargetCategory());
        ddataInfo.setDataName(resolveBusinessImportDataName(template, relativePath, allowCustomDataName));
        ddataInfo.setDataType(resolveBusinessImportDataType(template, relativePath));
        ddataInfo.setDataFilePath(normalizeDataFilePath(storagePath));
        ddataInfo.setIsSimulation(resolveBusinessImportSimulationFlag(template));
        ddataInfo.setSampleFrequency(1000);
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setExtAttr(template.getExtAttr());
        ddataInfo.setCreateBy(NickNameUtil.getNickName());
        return ddataInfo;
    }

    private String resolveBusinessImportDataName(DdataInfo template, String relativePath, boolean allowCustomDataName)
    {
        if (allowCustomDataName && StringUtils.isNotEmpty(template.getDataName()))
        {
            return template.getDataName().trim();
        }
        return extractFileName(relativePath);
    }

    private String resolveBusinessImportDataType(DdataInfo template, String relativePath)
    {
        if (StringUtils.isNotEmpty(template.getDataType()))
        {
            return template.getDataType().trim();
        }
        return resolveExperimentDataType(relativePath);
    }

    private Boolean resolveBusinessImportSimulationFlag(DdataInfo template)
    {
        return template.getIsSimulation() == null ? Boolean.TRUE : template.getIsSimulation();
    }

    private boolean canUseCustomBusinessDataName(DdataInfo template, List<MultipartFile> files)
    {
        if (template == null || StringUtils.isEmpty(template.getDataName()) || files == null)
        {
            return false;
        }

        MultipartFile candidate = null;
        int validCount = 0;
        for (MultipartFile file : files)
        {
            if (file == null || file.isEmpty())
            {
                continue;
            }

            String uploadPath = normalizeExperimentUploadPath(file.getOriginalFilename());
            if (shouldSkipExperimentPath(uploadPath))
            {
                continue;
            }

            validCount++;
            candidate = file;
            if (validCount > 1)
            {
                return false;
            }
        }

        if (validCount != 1 || candidate == null)
        {
            return false;
        }

        String normalizedPath = normalizeExperimentUploadPath(candidate.getOriginalFilename());
        if ("zip".equals(extractExtensionName(normalizedPath)))
        {
            return false;
        }
        return "/".equals(extractDirectory("/" + StringUtils.removeStart(normalizedPath, "/")));
    }

    private String resolveBusinessImportTargetType(DdataInfo ddataInfo)
    {
        if (StringUtils.isNotEmpty(ddataInfo.getTargetType()))
        {
            return ddataInfo.getTargetType();
        }
        if (StringUtils.isEmpty(ddataInfo.getTargetId()))
        {
            return null;
        }

        if (dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()) == null)
        {
            throw new ServiceException("\u76EE\u6807\u4E0D\u5B58\u5728");
        }
        return dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()).getTargetType();
    }

    @Override
    public void uploadFiles(List<MultipartFile> files, String experimentId)
    {
        if (files == null || files.isEmpty())
        {
            return;
        }

        DExperimentInfo experimentInfo = requireExperiment(experimentId);
        for (MultipartFile file : files)
        {
            if (file == null)
            {
                continue;
            }

            String uploadPath = normalizeExperimentUploadPath(file.getOriginalFilename());
            if (shouldSkipExperimentPath(uploadPath))
            {
                continue;
            }

            String extension = extractExtensionName(uploadPath);
            if ("zip".equals(extension))
            {
                uploadZipArchive(file, experimentInfo, uploadPath);
                continue;
            }

            assertExperimentExtension(extension, uploadPath);
            try (InputStream inputStream = file.getInputStream())
            {
                storeExperimentFile(experimentInfo, uploadPath, inputStream);
            }
            catch (IOException e)
            {
                log.warn("文件上传失败: {}", uploadPath, e);
                throw new ServiceException("文件上传失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void syncSimulationResultFiles(
            String experimentId,
            List<String> storedFileNames,
            List<String> sourceFileNames,
            String createBy,
            String targetCategory, List<TaskDataGroup> taskDataGroups)
    {
        if (StringUtils.isEmpty(experimentId) || StringUtils.isEmpty(storedFileNames))
        {
            return;
        }
        int index = 0;

        DExperimentInfo experimentInfo = requireExperiment(experimentId);
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);

        for (String storedFileName : storedFileNames)
        {
            if (StringUtils.isEmpty(storedFileName))
            {
                index++;
                continue;
            }

            String normalizedFileName = Paths.get(storedFileName.trim()).getFileName().toString();
            String dataFilePath = normalizeDataFilePath("/" + normalizedFileName);
            Path targetPath = resolveAbsoluteDataPath(experimentRoot, dataFilePath);

            try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(projectRoot, experimentRoot, targetPath))
            {
                if (Files.notExists(targetPath) || Files.isDirectory(targetPath))
                {
                    log.warn("生成文件不存在: {}", normalizedFileName);
                    throw new ServiceException("生成文件不存在: " + normalizedFileName);
                }

                DdataInfo ddataInfo = buildSimulationResultDataInfo(
                        experimentInfo,
                        dataFilePath,
                        sourceFileNames.get(index),
                        createBy,
                        targetCategory,taskDataGroups.get(index));
                DdataInfo oldInfo = ddataMapper.selectSameNameFile(experimentId, dataFilePath);
                if (oldInfo != null)
                {
                    mergeExistingDataInfo(ddataInfo, oldInfo);
                    redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + oldInfo.getId());
                    ddataMapper.updateDdataInfo(ddataInfo);
                    index++;
                    continue;
                }
                ddataMapper.insertDdataInfo(ddataInfo);
                index++;
            }
        }
    }

    @Override
    public Integer updateDdataInfo(DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            log.warn("数据ID不能为空");
            throw new ServiceException("数据ID不能为空");
        }

        DdataInfo oldDataInfo = selectDataInfoRecord(ddataInfo.getId());
        if (oldDataInfo == null)
        {
            log.warn("数据记录不存在: {}", ddataInfo.getId());
            throw new ServiceException("数据记录不存在");
        }

        String oldDataFilePath = normalizeDataFilePath(oldDataInfo.getDataFilePath());
        String fileSuffix = extractSuffix(oldDataFilePath);
        String safeFileName = normalizeFileName(ddataInfo.getFileName(), oldDataFilePath);

        String targetExperimentId = StringUtils.isNotEmpty(ddataInfo.getExperimentId())
                ? ddataInfo.getExperimentId()
                : oldDataInfo.getExperimentId();

        DExperimentInfo oldExperiment = requireExperiment(oldDataInfo.getExperimentId());
        DExperimentInfo newExperiment = requireExperiment(targetExperimentId);
        DProjectInfo oldProject = requireProject(oldExperiment.getProjectId());
        DProjectInfo newProject = requireProject(newExperiment.getProjectId());

        String requestedPath = StringUtils.isNotEmpty(ddataInfo.getDataFilePath())
                ? normalizeDataFilePath(ddataInfo.getDataFilePath())
                : oldDataFilePath;
        String targetDir = extractDirectory(requestedPath);
        String targetDataFilePath = buildDataFilePath(targetDir, safeFileName, fileSuffix);

        Path oldProjectRoot = buildProjectRootPath(oldProject);
        Path newProjectRoot = buildProjectRootPath(newProject);
        Path oldExperimentRoot = buildExperimentRootPath(oldProject, oldExperiment);
        Path newExperimentRoot = buildExperimentRootPath(newProject, newExperiment);
        Path oldAbsolutePath = resolveAbsoluteDataPath(oldExperimentRoot, oldDataFilePath);
        Path newAbsolutePath = resolveAbsoluteDataPath(newExperimentRoot, targetDataFilePath);

        try
        {
            try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                    buildLockPaths(oldProjectRoot, oldExperimentRoot, newProjectRoot, newExperimentRoot),
                    buildLockPaths(oldAbsolutePath, newAbsolutePath)))
            {
                if (Files.notExists(oldAbsolutePath))
                {
                    log.warn("源数据文件不存在: {}", oldDataFilePath);
                    throw new ServiceException("源数据文件不存在");
                }
                if (ddataMapper.selectSameNameFile(ddataInfo.getExperimentId(),targetDataFilePath) != null && !newAbsolutePath.equals(oldAbsolutePath))
                {
                    log.warn("目标文件已存在: {}", targetDataFilePath);
                    throw new ServiceException("目标文件已存在");
                }

                if (!newAbsolutePath.equals(oldAbsolutePath))
                {
                    Path targetParent = newAbsolutePath.getParent();
                    if (targetParent != null && Files.notExists(targetParent))
                    {
                        log.warn("创建目录失败: {}", targetParent);
                        Files.createDirectories(targetParent);
                    }
                    Files.move(oldAbsolutePath, newAbsolutePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("移动数据文件失败: {}", newAbsolutePath, e);
            throw new ServiceException("移动数据文件失败: " + e.getMessage());
        }

        ddataInfo.setExperimentId(targetExperimentId);
        ddataInfo.setDataFilePath(targetDataFilePath);
        ddataInfo.setFileName(safeFileName);
        ddataInfo.setUpdateBy(NickNameUtil.getNickName());
        redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + ddataInfo.getId());
        return ddataMapper.updateDdataInfo(ddataInfo);
    }

    @Override
    public Integer deleteDdataInfos(List<Integer> ids)
    {
        StringBuilder errorMsg = new StringBuilder();
        List<Integer> deleteDataIds = new ArrayList<>();

        for (Integer id : ids)
        {
            DdataInfo dataInfo = selectDataInfoRecord(id);
            if (dataInfo == null)
            {
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + id);
                continue;
            }

            DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(dataInfo.getExperimentId());
            if (experimentInfo == null)
            {
                errorMsg.append("试验信息不存在 ").append(id).append("\n");
                continue;
            }

            DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(experimentInfo.getProjectId());
            if (projectInfo == null)
            {
                errorMsg.append("项目不存在 ").append(id).append("\n");
                continue;
            }

            Path projectRoot = buildProjectRootPath(projectInfo);
            Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
            String relativePath = BuildDataFilePath(dataInfo) + dataInfo.getDataFilePath();
            Path sourcePath = Paths.get(profile, relativePath).normalize();
            Path targetPath = Paths.get(backUPdir, relativePath).normalize();

            try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                    buildLockPaths(projectRoot, experimentRoot),
                    buildLockPaths(sourcePath)))
            {
                DdataInfo currentDataInfo = selectDataInfoRecord(id);
                if (currentDataInfo == null)
                {
                    redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + id);
                    continue;
                }

                String currentRelativePath = BuildDataFilePath(currentDataInfo) + currentDataInfo.getDataFilePath();
                sourcePath = Paths.get(profile, currentRelativePath).normalize();
                targetPath = Paths.get(backUPdir, currentRelativePath).normalize();

                try
                {
                    Path targetParentDir = targetPath.getParent();
                    if (targetParentDir != null && !Files.exists(targetParentDir))
                    {
                        log.warn("创建目录失败: {}", targetParentDir);
                        Files.createDirectories(targetParentDir);
                    }
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException e)
                {
                    log.warn("备份文件失败: {}", sourcePath, e);
                    errorMsg.append("备份文件失败: ")
                            .append(sourcePath)
                            .append(" -> ")
                            .append(targetPath)
                            .append(", 原因: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                try
                {
                    Files.deleteIfExists(sourcePath);
                }
                catch (IOException e)
                {
                    log.warn("删除源文件失败: {}", sourcePath, e);
                    errorMsg.append("删除源文件失败: ")
                            .append(sourcePath)
                            .append(", 原因: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                ddataMapper.deleteDdataInfos(buildSingleIdList(id));
                deleteDataIds.add(id);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + id);
            }
        }

        if (errorMsg.length() > 0 && deleteDataIds.size() != ids.size())
        {
            log.warn("备份数据失败，id={}, 错误信息={}", ids, errorMsg.toString());
            throw new ServiceException(errorMsg.toString());
        }
        return 1;
    }
    @Override
    public int backupDataById(Integer id){
        DdataInfo cachedDataInfo = redisCache.getCacheObject(CacheConstants.DATA_INFO_KEY + id);
        DdataInfo ddataInfo = cachedDataInfo != null ? cachedDataInfo : ddataMapper.selectDdataInfoById(id);
        if (ddataInfo == null || StringUtils.isEmpty(ddataInfo.getExperimentId()) || StringUtils.isEmpty(ddataInfo.getDataFilePath())) {
            log.warn("备份失败，数据不存在或缺少必要信息，id={}", id);
            return 0;
        }
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        if (experimentInfo == null || experimentInfo.getProjectId() == null) {
            log.warn("备份失败，试验信息不存在，id={}, experimentId={}", id, ddataInfo.getExperimentId());
            return 0;
        }
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(experimentInfo.getProjectId());
        if(projectInfo == null){
            log.warn("备份失败，项目信息不存在，id={}, projectId={}", id, experimentInfo.getProjectId());
            return 0;
        }
        ddataInfo.setExperimentName(experimentInfo.getExperimentName());
        ddataInfo.setProjectId(projectInfo.getProjectId());
        ddataInfo.setProjectName(projectInfo.getProjectName());
        String relativePath = BuildDataFilePath(ddataInfo) + ddataInfo.getDataFilePath();
        String backupFilePath = extractBaseName(ddataInfo.getDataFilePath()) + "_" + System.currentTimeMillis() + "_" + ddataInfo.getId() + extractSuffix(ddataInfo.getDataFilePath());
        Path sourcePath = Paths.get(profile, relativePath).normalize();
        Path targetPath = Paths.get(backAndRestore, backupFilePath).normalize();
        try (PathLockManager.LockHandle ignored = pathLockManager.lockRead(sourcePath)) {
            if (Files.notExists(sourcePath) || Files.isDirectory(sourcePath)) {
                log.warn("备份失败，源文件不存在或不是文件，id={}, path={}", id, sourcePath);
                return 0;
            }
            Path targetParent = targetPath.getParent();
            if (targetParent != null && Files.notExists(targetParent)) {
                Files.createDirectories(targetParent);
            }
            //已经备份后除非还原否则不可再备份
            if(isDataFileHasBackup(id)){
                log.warn("备份失败，该数据已经备份,id={}", id);
                return 0;
            }
            Files.copy(sourcePath,targetPath,StandardCopyOption.REPLACE_EXISTING);
            BackupData backupData = transferDataToBackupData(ddataInfo, "/" +backupFilePath);
            if (backupData == null || backDataMapper.insertBackupData(backupData) <= 0) {
                log.warn("备份失败，备份记录入库失败，id={}", id);
                return 0;
            }
        } catch (Exception e) {
            log.info("备份异常");
            return 0;
        }
        return 1;
    }
    //查看数据是否已经备份
    private Boolean isDataFileHasBackup(Integer dataId){
        if(backDataMapper.selectBackupDataByDataId(dataId)!=null){
            return true;
        }
        return false;
    }
    @Override
    public String restoreDataFile(Integer BackUpDataId){
        BackupData backupData = backDataMapper.selectBackupDataById(BackUpDataId);
        if(backupData == null || Integer.valueOf(1).equals(backupData.getIsRestored())){
            return "请选择需要恢复的数据";
        }
        if (StringUtils.isEmpty(backupData.getProjectName())
                || StringUtils.isEmpty(backupData.getExperimentName())
                || StringUtils.isEmpty(backupData.getExperimentId())
                || StringUtils.isEmpty(backupData.getSourcePath())
                || StringUtils.isEmpty(backupData.getBackupFilePath())) {
            return "备份记录信息不完整，无法恢复";
        }
        String normalizedSourcePath = normalizeDataFilePath(backupData.getSourcePath());
        Path sourcePath = Paths.get(backAndRestore, StringUtils.removeStart(backupData.getBackupFilePath(), "/"));
        Path experimentRoot = Paths.get(
                profile,
                backupData.getProjectName(),
                backupData.getExperimentName()
        ).normalize();
//        if(Files.notExists(experimentRoot)){
//            DProjectInfo dProjectInfo = new DProjectInfo();
//            //试验不存在新建试验，若项目不存在则新建项目
//            if(Files.notExists(experimentRoot.getParent())){
//                dProjectInfo.setProjectName(backupData.getProjectName());
//                dProjectInfo.setCreateBy(NickNameUtil.getNickName());
//                dProjectInfo.setPath("/" + backupData.getProjectName());
//                projectInfoService.insertDProjectInfo(dProjectInfo);
//            } else {
//                //检查当前存在的项目是否为之前的项目，否则更新项目
//                Long currentProjectId = dProjectInfoMapper.selectSameNameProject(backupData.getProjectName()).getProjectId();
//                if(!Objects.equals(currentProjectId, backupData.getProjectId())){
//                    backupData.setProjectId(currentProjectId);
//                }
//            }
//            DExperimentInfo dExperimentInfo = new DExperimentInfo();
//            dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
//            dExperimentInfo.setTargetId(backupData.getTargetId());
//            dExperimentInfo.setExperimentName(backupData.getExperimentName());
//            dExperimentInfo.setProjectId(backupData.getProjectId());
//            dExperimentInfo.setStartTime(new Date());
//            dExperimentInfo.setCreateBy(NickNameUtil.getNickName());
//            dExperimentInfo.setPath("/" + dExperimentInfo.getExperimentName());
//            dExperimentInfoService.insertDExperimentInfo(dExperimentInfo);
//            backupData.setProjectId(dProjectInfo.getProjectId());
//            backupData.setExperimentId(dExperimentInfo.getExperimentId());
//            backDataMapper.updateBackupData(backupData);
//        } else{
//            Long currentProjectId = dProjectInfoMapper.selectSameNameProject(backupData.getProjectName()).getProjectId();
//            //检查当前存在的试验
//            String currentExperimentId = dExperimentInfoMapper.selectSamePathExperiment(backupData.getExperimentName(),
//                    currentProjectId).getExperimentId();
//            if(!Objects.equals(currentExperimentId, backupData.getExperimentId()))
//                backupData.setExperimentId(currentExperimentId);
//            backDataMapper.updateBackupData(backupData);
//        }
        String restoredDataFilePath = resolveAvailableExperimentStoragePath(
                backupData.getExperimentId(),
                experimentRoot,
                normalizedSourcePath
        );
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, restoredDataFilePath);
        Path targetParent = targetPath.getParent();
        try(PathLockManager.LockHandle ignored = pathLockManager.lockWrite(targetPath)){
            if (Files.notExists(sourcePath) || Files.isDirectory(sourcePath)) {
                log.warn("文件失败，源文件不存在或不是文件，path={}",  sourcePath);
                return "文件恢复失败，源文件不存在或不是文件";
            }
            if (targetParent != null && Files.notExists(targetParent)) {
                DProjectInfo dProjectInfo = new DProjectInfo();
                //试验不存在新建试验，若项目不存在则新建项目
                if(Files.notExists(experimentRoot.getParent())){
                    dProjectInfo.setProjectName(backupData.getProjectName());
                    dProjectInfo.setCreateBy(NickNameUtil.getNickName());
                    dProjectInfo.setPath("/" + backupData.getProjectName());
                    projectInfoService.insertDProjectInfo(dProjectInfo);
                } else {
                    //检查当前存在的项目是否为之前的项目，否则更新项目
                    Long currentProjectId = dProjectInfoMapper.selectSameNameProject(backupData.getProjectName()).getProjectId();
                    if(!Objects.equals(currentProjectId, backupData.getProjectId())){
                        backupData.setProjectId(currentProjectId);
                    }
                }
                DExperimentInfo dExperimentInfo = new DExperimentInfo();
                dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
                dExperimentInfo.setTargetId(backupData.getTargetId());
                dExperimentInfo.setExperimentName(backupData.getExperimentName());
                dExperimentInfo.setProjectId(backupData.getProjectId());
                dExperimentInfo.setStartTime(new Date());
                dExperimentInfo.setCreateBy(NickNameUtil.getNickName());
                dExperimentInfo.setPath("/" + dExperimentInfo.getExperimentName());
                dExperimentInfoService.insertDExperimentInfo(dExperimentInfo);
                backupData.setProjectId(dProjectInfo.getProjectId());
                backupData.setExperimentId(dExperimentInfo.getExperimentId());
                backDataMapper.updateBackupData(backupData);
            }else{
                Long currentProjectId = dProjectInfoMapper.selectSameNameProject(backupData.getProjectName()).getProjectId();
                //检查当前存在的试验
                String currentExperimentId = dExperimentInfoMapper.selectSamePathExperiment(backupData.getExperimentName(),
                        currentProjectId).getExperimentId();
                if(!Objects.equals(currentExperimentId, backupData.getExperimentId()))
                    backupData.setExperimentId(currentExperimentId);
                backDataMapper.updateBackupData(backupData);
            }
            if (!restoredDataFilePath.equals(normalizedSourcePath)) {
                log.info("恢复文件重名，自动调整存储路径，backupId={}, oldPath={}, newPath={}",
                        BackUpDataId, normalizedSourcePath, restoredDataFilePath);
            }
            if(ddataMapper.selectDdataInfoById(backupData.getDataInfoId()) != null){
                return "文件尚未删除，无需恢复";
            }
            Files.copy(sourcePath,targetPath,StandardCopyOption.REPLACE_EXISTING);
            //在数据库中插入数据
            DdataInfo ddataInfo = new DdataInfo();
            ddataInfo.setExperimentId(backupData.getExperimentId());
            ddataInfo.setTargetId(backupData.getTargetId());
            ddataInfo.setTargetType(backupData.getTargetType());
            ddataInfo.setTargetCategory(backupData.getTargetCategory());
            String restoredDataName = backupData.getDataName();
            if (StringUtils.isEmpty(restoredDataName)) {
                restoredDataName = extractFileName(restoredDataFilePath);
            } else if (!restoredDataFilePath.equals(normalizedSourcePath)
                    && restoredDataName.equals(extractFileName(normalizedSourcePath))) {
                restoredDataName = extractFileName(restoredDataFilePath);
            }
            ddataInfo.setDataName(restoredDataName);
            ddataInfo.setDataType(backupData.getDataType());
            ddataInfo.setDataFilePath(restoredDataFilePath);
            ddataInfo.setDeviceId(backupData.getDeviceId());
            ddataInfo.setDeviceInfo(backupData.getDeviceInfo());
            ddataInfo.setSampleFrequency(backupData.getSampleFrequency());
            ddataInfo.setWorkStatus(backupData.getWorkStatus());
            ddataInfo.setExtAttr(backupData.getExtAttr());
            ddataInfo.setCreateBy(NickNameUtil.getNickName());
            if (backupData.getIsSimulation() != null) {
                ddataInfo.setIsSimulation(backupData.getIsSimulation() == 1);
            }
            ddataMapper.insertDdataInfo(ddataInfo);
            backupData.setRestoredDataInfoId(ddataInfo.getId());
            backupData.setRestoreTime(new Date());
            backupData.setRestoreBy(NickNameUtil.getNickName());
            backupData.setIsRestored(1);
            backDataMapper.updateBackupData(backupData);
        }catch (Exception e){
            log.warn("恢复备份数据失败，backupId={}", BackUpDataId, e);
            return "文件操作异常，请稍后重试";
        }
        return null;
    }

    @Override
    public List<BackupData> selectBackupDataList(BackupData backupData)
    {
        return backDataMapper.selectBackupDataList(backupData);
    }

    private BackupData transferDataToBackupData(DdataInfo ddataInfo, String backupFilePath) {
        if (ddataInfo == null) {
            return null;
        }

        BackupData backupData = new BackupData();
        // 原始数据表ID，对应 DdataInfo 的 id
        backupData.setDataInfoId(ddataInfo.getId());

        backupData.setTargetId(ddataInfo.getTargetId());
        backupData.setTargetType(ddataInfo.getTargetType());
        backupData.setTargetCategory(ddataInfo.getTargetCategory());
        backupData.setExperimentId(ddataInfo.getExperimentId());
        backupData.setExperimentName(ddataInfo.getExperimentName());
        backupData.setProjectId(ddataInfo.getProjectId());
        backupData.setProjectName(ddataInfo.getProjectName());
        backupData.setDataName(ddataInfo.getDataName());
        backupData.setDataType(ddataInfo.getDataType());
        backupData.setDeviceId(ddataInfo.getDeviceId());
        backupData.setDeviceInfo(ddataInfo.getDeviceInfo());

        // 源文件地址，对应原数据文件地址
        backupData.setSourcePath(ddataInfo.getDataFilePath());
        backupData.setBackupFilePath(backupFilePath);

        backupData.setSampleFrequency(ddataInfo.getSampleFrequency());
        backupData.setWorkStatus(ddataInfo.getWorkStatus());
        backupData.setExtAttr(ddataInfo.getExtAttr());
        backupData.setRemark(ddataInfo.getRemark());
        if (ddataInfo.getIsSimulation() != null) {
            backupData.setIsSimulation(Boolean.TRUE.equals(ddataInfo.getIsSimulation()) ? 1 : 0);
        }
        String name = NickNameUtil.getNickName();
        backupData.setDeleteBy(name);
        backupData.setDeleteTime(new Date());
        backupData.setCreateBy(name);
        backupData.setCreateTime(new Date());

        // 默认未恢复
        backupData.setIsRestored(0);

        // 以下字段通常由后续流程补充，这里先不赋值
        // backupData.setRestoredDataInfoId(null);
        // backupData.setBackupFilePath(null);
        // backupData.setRestoreBy(null);
        // backupData.setRestoreTime(null);

        return backupData;
    }

    @Override
    public List<Map<String, Object>> getMovePathTree()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        List<DProjectInfo> projects = dProjectInfoMapper.selectDProjectInfoList(new DProjectInfo());
        List<DExperimentInfo> experiments = dExperimentInfoMapper.selectDExperimentInfoList(new DExperimentInfo());

        Map<Long, List<DExperimentInfo>> experimentsByProject = experiments.stream()
                .filter(item -> item.getProjectId() != null)
                .collect(Collectors.groupingBy(DExperimentInfo::getProjectId));

        projects.sort(Comparator.comparing(item -> item.getProjectName() == null ? "" : item.getProjectName()));

        for (DProjectInfo project : projects)
        {
            Map<String, Object> projectNode = new HashMap<>();
            projectNode.put("id", "project-" + project.getProjectId());
            projectNode.put("label", project.getProjectName());
            projectNode.put("type", "project");
            projectNode.put("disabled", true);

            List<Map<String, Object>> experimentNodes = new ArrayList<>();
            List<DExperimentInfo> projectExperiments =
                    experimentsByProject.getOrDefault(project.getProjectId(), new ArrayList<>());
            projectExperiments.sort(
                    Comparator.comparing(item -> item.getExperimentName() == null ? "" : item.getExperimentName()));

            for (DExperimentInfo experiment : projectExperiments)
            {
                Map<String, Object> experimentNode = new HashMap<>();
                experimentNode.put("id", "experiment-" + experiment.getExperimentId());
                experimentNode.put("label", experiment.getExperimentName());
                experimentNode.put("type", "experiment");
                experimentNode.put("disabled", false);
                experimentNode.put("projectName", project.getProjectName());
                experimentNode.put("experimentName", experiment.getExperimentName());
                experimentNode.put("experimentId", experiment.getExperimentId());
                experimentNode.put("relativePath", "/");

                Path experimentRoot = buildExperimentRootPath(project, experiment);
                experimentNode.put(
                        "children",
                        listSubDirectories(
                                experimentRoot,
                                experimentRoot,
                                experiment.getExperimentId(),
                                project.getProjectName(),
                                experiment.getExperimentName()
                        )
                );
                experimentNodes.add(experimentNode);
            }

            projectNode.put("children", experimentNodes);
            result.add(projectNode);
        }
        return result;
    }

    private String BuildDataFilePath(DdataInfo ddataInfo)
    {
        DExperimentInfo experimentInfo = requireExperiment(ddataInfo.getExperimentId());
        String projectPath = requireProject(experimentInfo.getProjectId()).getPath();
        return projectPath + experimentInfo.getPath();
    }

    private DdataInfo selectDataInfoRecord(Integer id)
    {
        DdataInfo query = new DdataInfo();
        query.setId(id);
        List<DdataInfo> records = ddataMapper.selectDdataInfoList(query);
        return records == null || records.isEmpty() ? null : records.get(0);
    }

    private DExperimentInfo requireExperiment(String experimentId)
    {
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (experimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }
        return experimentInfo;
    }

    private DProjectInfo requireProject(Long projectId)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }
        return projectInfo;
    }

    private List<Path> buildLockPaths(Path... paths)
    {
        List<Path> result = new ArrayList<>();
        if (paths == null)
        {
            return result;
        }
        for (Path path : paths)
        {
            if (path != null)
            {
                result.add(path);
            }
        }
        return result;
    }

    private List<Integer> buildSingleIdList(Integer id)
    {
        List<Integer> ids = new ArrayList<>();
        ids.add(id);
        return ids;
    }

    private String buildImportedDataFilePath(String originalFilename)
    {
        String normalizedOriginalPath = normalizeDataFilePath("/" + originalFilename);
        String baseName = extractBaseName(normalizedOriginalPath);
        String suffix = extractSuffix(normalizedOriginalPath);
        return buildDataFilePath("/", baseName ,suffix);
    }

    private String resolveAvailableExperimentStoragePath(String experimentId, Path experimentRoot, String storagePath)
    {
        String normalizedStoragePath = normalizeDataFilePath(storagePath);
        String directory = extractDirectory(normalizedStoragePath);
        String baseName = extractBaseName(normalizedStoragePath);
        String suffix = extractSuffix(normalizedStoragePath);
        String candidatePath = normalizedStoragePath;
        int suffixIndex = 1;

        while (hasExperimentStoragePathConflict(experimentId, experimentRoot, candidatePath))
        {
            candidatePath = buildDataFilePath(directory, baseName + "(" + suffixIndex + ")", suffix);
            suffixIndex++;
        }
        return candidatePath;
    }

    private boolean hasExperimentStoragePathConflict(String experimentId, Path experimentRoot, String storagePath)
    {
        String normalizedStoragePath = normalizeDataFilePath(storagePath);
        return ddataMapper.selectSameNameFile(experimentId, normalizedStoragePath) != null
                || Files.exists(resolveAbsoluteDataPath(experimentRoot, normalizedStoragePath));
    }

    private DdataInfo buildSimulationResultDataInfo(
            DExperimentInfo experimentInfo,
            String dataFilePath,
            String sourceFileName,
            String createBy,
            String targetCategory,TaskDataGroup taskDataGroup)
    {
        String normalizedDataFilePath = normalizeDataFilePath(dataFilePath);
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(experimentInfo.getTargetId());
        ddataInfo.setTargetType(resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(taskDataGroup.getGroupName());
        ddataInfo.setDataName(sourceFileName);
        ddataInfo.setDataType(taskDataGroup.getGroupName());
        ddataInfo.setDataFilePath(normalizedDataFilePath);
        ddataInfo.setSampleFrequency(taskDataGroup.getFrequencyHz().intValueExact());
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setIsSimulation(taskDataGroup.getIsSimulation());
        ddataInfo.setCreateBy(resolveSimulationCreateBy(createBy, experimentInfo));
        return ddataInfo;
    }

    private void mergeExistingDataInfo(DdataInfo ddataInfo, DdataInfo oldInfo)
    {
        ddataInfo.setId(oldInfo.getId());
        if (ddataInfo.getTargetId() == null)
        {
            ddataInfo.setTargetId(oldInfo.getTargetId());
        }
        if (ddataInfo.getTargetType() == null)
        {
            ddataInfo.setTargetType(oldInfo.getTargetType());
        }
        if (ddataInfo.getTargetCategory() == null)
        {
            ddataInfo.setTargetCategory(oldInfo.getTargetCategory());
        }
        if (ddataInfo.getSampleFrequency() == null)
        {
            ddataInfo.setSampleFrequency(oldInfo.getSampleFrequency());
        }
        if (ddataInfo.getDeviceId() == null)
        {
            ddataInfo.setDeviceId(oldInfo.getDeviceId());
        }
        if (ddataInfo.getDeviceInfo() == null)
        {
            ddataInfo.setDeviceInfo(oldInfo.getDeviceInfo());
        }
        if (ddataInfo.getWorkStatus() == null)
        {
            ddataInfo.setWorkStatus(oldInfo.getWorkStatus());
        }
        if (ddataInfo.getExtAttr() == null)
        {
            ddataInfo.setExtAttr(oldInfo.getExtAttr());
        }
        if (ddataInfo.getIsSimulation() == null)
        {
            ddataInfo.setIsSimulation(oldInfo.getIsSimulation());
        }
    }

    private Integer resolveSimulationSampleFrequency(Integer sampleFrequency)
    {
        if (sampleFrequency == null || sampleFrequency <= 0)
        {
            return 1000;
        }
        return sampleFrequency;
    }

    private String resolveSimulationCreateBy(String createBy, DExperimentInfo experimentInfo)
    {
        if (StringUtils.isNotEmpty(createBy))
        {
            return createBy.trim();
        }
        if (experimentInfo != null && StringUtils.isNotEmpty(experimentInfo.getCreateBy()))
        {
            return experimentInfo.getCreateBy().trim();
        }
        return "system";
    }

    private String normalizeOptionalText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFileName(String fileName, String fallbackDataPath)
    {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isEmpty())
        {
            normalized = extractBaseName(fallbackDataPath);
        }
        if (!normalized.matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            log.info("文件名无效: {}", normalized);
            throw new ServiceException("文件名无效");
        }
        return normalized;
    }

    private String normalizeDataFilePath(String dataFilePath)
    {
        if (StringUtils.isEmpty(dataFilePath))
        {
            return "/";
        }

        String normalized = dataFilePath.trim().replace("\\", "/");
        if (!normalized.startsWith("/"))
        {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//"))
        {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.contains(".."))
        {
            throw new ServiceException("文件路径无效");
        }
        return normalized;
    }

    private String extractSuffix(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private String extractBaseName(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String extractDirectory(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        int index = normalized.lastIndexOf('/');
        if (index <= 0)
        {
            return "/";
        }
        return normalized.substring(0, index);
    }

    private String buildDataFilePath(String directory, String fileName, String suffix)
    {
        String normalizedDir = normalizeDataFilePath(StringUtils.isEmpty(directory) ? "/" : directory);
        if (normalizedDir.length() > 1 && normalizedDir.endsWith("/"))
        {
            normalizedDir = normalizedDir.substring(0, normalizedDir.length() - 1);
        }
        String safeSuffix = suffix == null ? "" : suffix;
        return "/".equals(normalizedDir)
                ? "/" + fileName + safeSuffix
                : normalizedDir + "/" + fileName + safeSuffix;
    }

    private Path buildProjectRootPath(DProjectInfo projectInfo)
    {
        return Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/")
        ).normalize();
    }

    private Path buildExperimentRootPath(DProjectInfo projectInfo, DExperimentInfo experimentInfo)
    {
        return Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/"),
                StringUtils.removeStart(experimentInfo.getPath(), "/")
        ).normalize();
    }

    private Path resolveAbsoluteDataPath(Path experimentRoot, String dataFilePath)
    {
        String relativePath = StringUtils.removeStart(normalizeDataFilePath(dataFilePath), "/");
        Path resolved = experimentRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(experimentRoot))
        {
            throw new ServiceException("文件路径无效");
        }
        return resolved;
    }

    private List<Map<String, Object>> listSubDirectories(
            Path experimentRoot,
            Path currentPath,
            String experimentId,
            String projectName,
            String experimentName
    )
    {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (Files.notExists(currentPath) || !Files.isDirectory(currentPath))
        {
            return nodes;
        }

        try (Stream<Path> stream = Files.list(currentPath))
        {
            List<Path> childDirs = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(item -> item.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            for (Path directory : childDirs)
            {
                String relativePath = "/" + experimentRoot.relativize(directory).toString().replace("\\", "/");

                Map<String, Object> node = new HashMap<>();
                node.put("id", "dir-" + experimentId + ":" + relativePath);
                node.put("label", directory.getFileName().toString());
                node.put("type", "dir");
                node.put("disabled", false);
                node.put("experimentId", experimentId);
                node.put("experimentName", experimentName);
                node.put("projectName", projectName);
                node.put("relativePath", relativePath);
                node.put("children", listSubDirectories(experimentRoot, directory, experimentId, projectName, experimentName));
                nodes.add(node);
            }
        }
        catch (IOException e)
        {
            log.warn("列出子目录失败: {}", currentPath, e);
        }
        return nodes;
    }
}
