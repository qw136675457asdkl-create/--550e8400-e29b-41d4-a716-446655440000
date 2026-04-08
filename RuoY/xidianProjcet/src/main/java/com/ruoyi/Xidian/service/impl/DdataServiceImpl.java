package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DTargetInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DdataServiceImpl implements IDdataService
{
    private static final Logger log = LoggerFactory.getLogger(DdataServiceImpl.class);
    private static final Set<String> EXPERIMENT_ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("zip", "csv", "xls", "xlsx", "txt", "json", "doc", "docx", "pdf", "bin", "dat", "raw")
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

    private final String profile = RuoYiConfig.getProfile() + "/data";
    private final String backUPdir = RuoYiConfig.getBackupDir();

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
                assertExperimentExtension(extension, entryPath);
                storeExperimentFile(experimentInfo, entryPath, zipInputStream);
                hasUploadedEntry = true;
                zipInputStream.closeEntry();
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("解存失败: " + e.getMessage());
        }

        if (!hasUploadedEntry)
        {
            throw new ServiceException("没有可上传的文件");
        }
    }

    private void storeExperimentFile(DExperimentInfo experimentInfo, String relativePath, InputStream inputStream)
            throws IOException
    {
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
                Files.createDirectories(parentPath);
            }

            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            DdataInfo ddataInfo = buildExperimentUploadDataInfo(experimentInfo, normalizedPath, storagePath);
            DdataInfo oldInfo = ddataMapper.selectSameNameFile(experimentInfo.getExperimentId(), storagePath);
            if (oldInfo != null)
            {
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
        ddataInfo.setCreateBy(SecurityUtils.getUsername());

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
            throw new ServiceException("文件扩展名错误: " + extension + " " + relativePath);
        }
    }

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
        redisCache.setCacheObject(cacheKey, result);
        return result;
    }

    @Override
    public Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file)
    {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename))
        {
            throw new ServiceException("文件名不能为空");
        }

        String normalizedOriginalFilename = Paths.get(originalFilename).getFileName().toString();
        DExperimentInfo experimentInfo = requireExperiment(ddataInfo.getExperimentId());
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        String targetDataFilePath = buildImportedDataFilePath(normalizedOriginalFilename);
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, targetDataFilePath);

        try
        {
            try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                    buildLockPaths(projectRoot, experimentRoot),
                    buildLockPaths(targetPath)))
            {
                Path parentPath = targetPath.getParent();
                if (parentPath != null && Files.notExists(parentPath))
                {
                    Files.createDirectories(parentPath);
                }
                if(ddataMapper.selectSameNameFile(ddataInfo.getExperimentId(),targetDataFilePath) != null){
                    throw new ServiceException("文件名重复，请重新输入");
                }

                FileUploadUtils.assertAllowed(file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
                file.transferTo(targetPath);

                ddataInfo.setCreateBy(SecurityUtils.getUsername());
                ddataInfo.setDataFilePath(targetDataFilePath);
                ddataInfo.setTargetType(
                        dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()).getTargetType()
                );

                ddataInfo.setSampleFrequency(1000);
                ddataInfo.setDeviceId(null);
                ddataInfo.setDeviceInfo(null);
                ddataInfo.setWorkStatus("completed");
                return ddataMapper.insertDdataInfo(ddataInfo);
            } catch (InvalidExtensionException e) {
                throw new RuntimeException(e);
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
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
                throw new ServiceException("文件上传失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void syncSimulationResultFiles(
            String experimentId,
            List<String> storedFileNames,
            List<String> sourceFileNames,
            Integer sampleFrequency,
            String createBy,
            String targetCategory)
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
                    throw new ServiceException("生成文件不存在: " + normalizedFileName);
                }

                DdataInfo ddataInfo = buildSimulationResultDataInfo(
                        experimentInfo,
                        dataFilePath,
                        sourceFileNames.get(index),
                        sampleFrequency,
                        createBy,
                        targetCategory);
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
            throw new ServiceException("数据ID不能为空");
        }

        DdataInfo oldDataInfo = selectDataInfoRecord(ddataInfo.getId());
        if (oldDataInfo == null)
        {
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
                    throw new ServiceException("源数据文件不存在");
                }
                if (ddataMapper.selectSameNameFile(ddataInfo.getExperimentId(),targetDataFilePath) != null && !newAbsolutePath.equals(oldAbsolutePath))
                {
                    throw new ServiceException("目标文件已存在");
                }

                if (!newAbsolutePath.equals(oldAbsolutePath))
                {
                    Path targetParent = newAbsolutePath.getParent();
                    if (targetParent != null && Files.notExists(targetParent))
                    {
                        Files.createDirectories(targetParent);
                    }
                    Files.move(oldAbsolutePath, newAbsolutePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("移动数据文件失败: " + e.getMessage());
        }

        ddataInfo.setExperimentId(targetExperimentId);
        ddataInfo.setDataFilePath(targetDataFilePath);
        ddataInfo.setFileName(safeFileName);
        ddataInfo.setUpdateBy(SecurityUtils.getUsername());
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
                        Files.createDirectories(targetParentDir);
                    }
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException e)
                {
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
            throw new ServiceException(errorMsg.toString());
        }
        return 1;
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

    private DdataInfo buildSimulationResultDataInfo(
            DExperimentInfo experimentInfo,
            String dataFilePath,
            String sourceFileName,
            Integer sampleFrequency,
            String createBy,
            String targetCategory)
    {
        String normalizedDataFilePath = normalizeDataFilePath(dataFilePath);
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(experimentInfo.getTargetId());
        ddataInfo.setTargetType(resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(resolveSimulationTargetCategory(targetCategory, experimentInfo, normalizedDataFilePath));
        ddataInfo.setDataName(sourceFileName);
        ddataInfo.setDataType(resolveExperimentDataType(normalizedDataFilePath));
        ddataInfo.setDataFilePath(normalizedDataFilePath);
        ddataInfo.setSampleFrequency(resolveSimulationSampleFrequency(sampleFrequency));
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setIsSimulation(Boolean.TRUE);
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

    private String resolveSimulationTargetCategory(
            String targetCategory,
            DExperimentInfo experimentInfo,
            String dataFilePath)
    {
        if (StringUtils.isNotEmpty(targetCategory))
        {
            return targetCategory.trim();
        }

        String resolvedTargetType = resolveExperimentTargetType(experimentInfo);
        if (StringUtils.isNotEmpty(resolvedTargetType))
        {
            return resolvedTargetType;
        }

        String resolvedDataType = resolveExperimentDataType(dataFilePath);
        if (StringUtils.isNotEmpty(resolvedDataType))
        {
            return resolvedDataType;
        }
        return "simulation";
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
