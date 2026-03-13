package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DTargetInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DdataServiceImpl implements IDdataService
{
    private static final Logger log = LoggerFactory.getLogger(DdataServiceImpl.class);

    @Autowired
    private DdataMapper ddataMapper;

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private DTargetInfoMapper dTargetInfoMapper;

    private final String profile = RuoYiConfig.getProfile() + "/data";
    private final String backUPdir = RuoYiConfig.getBackupDir();

    @Override
    public List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo)
    {
        return ddataMapper.selectDdataInfoList(ddataInfo);
    }

    @Override
    public DdataInfo selectDdataInfoByDdataId(Integer id)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setId(id);
        return ddataMapper.selectDdataInfoList(ddataInfo).get(0);
    }

    @Override
    public Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file)
    {
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setSampleFrequency(1000);
        try
        {
            String filePath = BuildDataFilePath(ddataInfo);
            FileUploadUtils.upload(filePath, file);
        }
        catch (IOException e)
        {
            throw new ServiceException("文件上传失败? " + e.getMessage());
        }
        ddataInfo.setCreateBy(SecurityUtils.getUsername());
        ddataInfo.setDataFilePath("/" + ddataInfo.getDataName());
        ddataInfo.setTargetType(dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()).getTargetType());
        return ddataMapper.insertDdataInfo(ddataInfo);
    }

    @Override
    public Integer updateDdataInfo(DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("缺少数据ID");
        }

        DdataInfo query = new DdataInfo();
        query.setId(ddataInfo.getId());
        List<DdataInfo> records = ddataMapper.selectDdataInfoList(query);
        if (records == null || records.isEmpty())
        {
            throw new ServiceException("未找到数据记录");
        }
        DdataInfo oldDataInfo = records.get(0);

        String oldDataFilePath = normalizeDataFilePath(oldDataInfo.getDataFilePath());
        String fileSuffix = extractSuffix(oldDataFilePath);
        String safeFileName = normalizeFileName(ddataInfo.getFileName(), oldDataFilePath);

        String targetExperimentId = StringUtils.isNotEmpty(ddataInfo.getExperimentId())
                ? ddataInfo.getExperimentId()
                : oldDataInfo.getExperimentId();

        DExperimentInfo oldExperiment = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(oldDataInfo.getExperimentId());
        DExperimentInfo newExperiment = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(targetExperimentId);
        if (oldExperiment == null || newExperiment == null)
        {
            throw new ServiceException("未找到实验记录");
        }

        DProjectInfo oldProject = dProjectInfoMapper.selectDProjectInfoByProjectId(oldExperiment.getProjectId());
        DProjectInfo newProject = dProjectInfoMapper.selectDProjectInfoByProjectId(newExperiment.getProjectId());
        if (oldProject == null || newProject == null)
        {
            throw new ServiceException("未找到项目记录");
        }

        String requestedPath = StringUtils.isNotEmpty(ddataInfo.getDataFilePath())
                ? normalizeDataFilePath(ddataInfo.getDataFilePath())
                : oldDataFilePath;
        String targetDir = extractDirectory(requestedPath);
        String targetDataFilePath = buildDataFilePath(targetDir, safeFileName, fileSuffix);

        Path oldExperimentRoot = buildExperimentRootPath(oldProject, oldExperiment);
        Path newExperimentRoot = buildExperimentRootPath(newProject, newExperiment);
        Path oldAbsolutePath = resolveAbsoluteDataPath(oldExperimentRoot, oldDataFilePath);
        Path newAbsolutePath = resolveAbsoluteDataPath(newExperimentRoot, targetDataFilePath);

        if (Files.notExists(oldAbsolutePath))
        {
            throw new ServiceException("源数据文件不存在");
        }
        if (Files.exists(newAbsolutePath) && !newAbsolutePath.equals(oldAbsolutePath))
        {
            throw new ServiceException("目标路径已存在同名文件");
        }

        try
        {
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
        catch (IOException e)
        {
            throw new ServiceException("移动数据文件失败: " + e.getMessage());
        }

        ddataInfo.setExperimentId(targetExperimentId);
        ddataInfo.setDataFilePath(targetDataFilePath);
        ddataInfo.setFileName(safeFileName);
        return ddataMapper.updateDdataInfo(ddataInfo);
    }

    @Override
    public Integer deleteDdataInfos(List<Integer> ids)
    {
        DdataInfo ddataInfo = new DdataInfo();
        for (Integer id : ids)
        {
            ddataInfo.setId(id);
            DdataInfo dataInfo1 = ddataMapper.selectDdataInfoList(ddataInfo).get(0);

            String relativePath = BuildDataFilePath(dataInfo1) + dataInfo1.getDataFilePath();
            Path sourcePath = Paths.get(profile, relativePath);
            Path targetPath = Paths.get(backUPdir, relativePath);

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
                throw new ServiceException("备份数据文件失败: " + sourcePath + " -> " + targetPath + ", 失败原因: ? " + e.getMessage());
            }

            try
            {
                Files.deleteIfExists(sourcePath);
            }
            catch (IOException e)
            {
                throw new ServiceException("删除源数据文件失败: " + sourcePath + ", 失败原因: ? " + e.getMessage());
            }
        }

        return ddataMapper.deleteDdataInfos(ids);
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
            List<DExperimentInfo> projectExperiments = experimentsByProject.getOrDefault(project.getProjectId(), new ArrayList<>());
            projectExperiments.sort(Comparator.comparing(item -> item.getExperimentName() == null ? "" : item.getExperimentName()));

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
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        String projectPath = dProjectInfoMapper.selectDProjectInfoByProjectId(experimentInfo.getProjectId()).getPath();
        return projectPath + experimentInfo.getPath();
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
            log.info("Invalid file name: {}", normalized);
            throw new ServiceException("文件名称只能包含字母、数字、下划线、短横线和中文字符");
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
            throw new ServiceException("文件路径中不能包含'..'");
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
            throw new ServiceException("Path out of bounds");
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
            log.warn("Failed to list subdirectories for path: {}", currentPath, e);
        }
        return nodes;
    }
}
