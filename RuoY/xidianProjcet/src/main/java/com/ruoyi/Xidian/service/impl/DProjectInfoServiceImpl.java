package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class DProjectInfoServiceImpl implements IDProjectInfoService
{
    private static final Logger log = LoggerFactory.getLogger(DProjectInfoServiceImpl.class);

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private PathLockManager pathLockManager;

    private final String profile = RuoYiConfig.getProfile() + "/data";

    @Override
    public DProjectInfo selectDProjectInfoByProjectId(Long projectId)
    {
        if (redisCache.getCacheObject(CacheConstants.PROJECT_INFO_KEY + projectId) != null)
        {
            DProjectInfo projectInfo = redisCache.getCacheObject(CacheConstants.PROJECT_INFO_KEY + projectId);
            projectInfo.setFullPath("./data" + projectInfo.getPath());
            return projectInfo;
        }
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
        projectInfo.setFullPath("./data" + projectInfo.getPath());
        redisCache.setCacheObject(CacheConstants.PROJECT_INFO_KEY + projectId, projectInfo, 30, TimeUnit.MINUTES);
        return projectInfo;
    }

    @Override
    public List<DProjectInfo> selectDProjectInfoList(DProjectInfo dProjectInfo)
    {
        return dProjectInfoMapper.selectDProjectInfoList(dProjectInfo);
    }

    @Override
    public int insertDProjectInfo(DProjectInfo dProjectInfo)
    {
        String projectName = dProjectInfo.getProjectName();
        if (projectName == null || !projectName.matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("项目名称无效");
        }

        String pathStr = projectName;
        while (true)
        {
            Path path = buildProjectRootPath("/" + pathStr);
            try (PathLockManager.LockHandle ignored = pathLockManager.lockWrite(path))
            {
                if (dProjectInfoMapper.selectSameNameProject(dProjectInfo.getProjectName())!=null||Files.exists(path))
                {
                    throw new ServiceException("项目名称重复");
                }

                try
                {
                    Files.createDirectories(path);
                }
                catch (IOException e)
                {
                    throw new ServiceException("创建项目目录失败: " + e.getMessage());
                }

                dProjectInfo.setPath("/" + pathStr);
                log.info("新增项目, projectName={}", dProjectInfo.getProjectName());
                return dProjectInfoMapper.insertDProjectInfo(dProjectInfo);
            }
        }
    }

    @Override
    public int updateDProjectInfo(DProjectInfo dProjectInfo)
    {
        DProjectInfo oldProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(dProjectInfo.getProjectId());
        if (oldProjectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }

        if (!dProjectInfo.getPath().startsWith("/")
                || !dProjectInfo.getPath().substring(1).matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("项目路径无效");
        }

        Path oldPath = buildProjectRootPath(oldProjectInfo.getPath());
        Path newPath = buildProjectRootPath(dProjectInfo.getPath());

        try (PathLockManager.LockHandle ignored = pathLockManager.lockWrite(oldPath, newPath))
        {
            if (!oldPath.equals(newPath))
            {
                if (Files.exists(newPath))
                {
                    throw new ServiceException("目标路径已存在");
                }
                try
                {
                    Files.move(oldPath, newPath);
                }
                catch (IOException e)
                {
                    throw new ServiceException("更新项目目录失败: " + e.getMessage());
                }
            }
        }

        redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + dProjectInfo.getProjectId());
        dProjectInfo.setUpdateTime(DateUtils.getNowDate());
        log.info("更新项目, projectId={}", dProjectInfo.getProjectId());
        return dProjectInfoMapper.updateDProjectInfo(dProjectInfo);
    }

    @Override
    public int deleteDProjectInfoByProjectIds(Long[] projectIds)
    {
        if (projectIds == null || projectIds.length == 0)
        {
            return 0;
        }
        if (projectIds[0] == 0)
        {
            return 1;
        }

        List<Long> deleteProjectIds = new ArrayList<>();
        StringBuilder errorMsg = new StringBuilder();

        for (Long projectId : projectIds)
        {
            DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
            if (projectInfo == null)
            {
                errorMsg.append("项目不存在: ").append(projectId).append("\n");
                continue;
            }

            Path projectPath = buildProjectRootPath(projectInfo.getPath());
            try (PathLockManager.LockHandle ignored = pathLockManager.lockWrite(projectPath))
            {
                try
                {
                    if (isDirectoryNotEmpty(projectPath))
                    {
                        errorMsg.append("项目目录不为空: ").append(projectPath).append("\n");
                        continue;
                    }
                }
                catch (IOException e)
                {
                    errorMsg.append("检查项目目录失败, 原因: ")
                            .append(projectPath)
                            .append(", 原因: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                try
                {
                    Files.deleteIfExists(projectPath);
                }
                catch (IOException e)
                {
                    errorMsg.append("删除项目目录失败, 原因: ")
                            .append(projectPath)
                            .append(", 原因: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                dProjectInfoMapper.deleteDProjectInfoByProjectId(projectId);
                deleteProjectIds.add(projectId);
                redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + projectId);
                log.info("删除项目, projectId={}", projectId);
            }
        }

        if (errorMsg.length() > 0 && deleteProjectIds.size() != projectIds.length)
        {
            throw new ServiceException(errorMsg.toString());
        }
        return 1;
    }

    @Override
    public int deleteDProjectInfoByProjectId(Long projectId)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }

        Path projectPath = buildProjectRootPath(projectInfo.getPath());
        try (PathLockManager.LockHandle ignored = pathLockManager.lockWrite(projectPath))
        {
            try
            {
                if (isDirectoryNotEmpty(projectPath))
                {
                    throw new ServiceException("项目目录不为空");
                }
            }
            catch (IOException e)
            {
                throw new ServiceException("检查项目目录失败: " + e.getMessage());
            }

            try
            {
                Files.deleteIfExists(projectPath);
            }
            catch (IOException e)
            {
                throw new ServiceException("删除项目目录失败: " + e.getMessage());
            }

            redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + projectId);
            log.info("删除项目, projectId={}", projectId);
            return dProjectInfoMapper.deleteDProjectInfoByProjectId(projectId);
        }
    }

    @Override
    public List<DProjectInfo> selectAllDProjectInfo()
    {
        return dProjectInfoMapper.selectAllDProjectInfo();
    }

    private Path buildProjectRootPath(String projectPath)
    {
        return Paths.get(profile, projectPath.startsWith("/") ? projectPath.substring(1) : projectPath).normalize();
    }

    private boolean isDirectoryNotEmpty(Path directory) throws IOException
    {
        if (Files.notExists(directory))
        {
            return false;
        }
        try (Stream<Path> stream = Files.list(directory))
        {
            return stream.findAny().isPresent();
        }
    }
}
