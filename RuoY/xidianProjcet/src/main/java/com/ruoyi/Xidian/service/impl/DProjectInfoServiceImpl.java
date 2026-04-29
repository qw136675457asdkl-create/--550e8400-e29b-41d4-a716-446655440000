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
    public int insertDProjectInfo(DProjectInfo dProjectInfo)
    {
        String projectName = dProjectInfo.getProjectName();

        if (projectName != null)
        {
            projectName = projectName.trim();
            dProjectInfo.setProjectName(projectName);
        }

        if (projectName == null || projectName.isEmpty()
                || !projectName.matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("项目名称无效");
        }

        if (dProjectInfoMapper.selectSameNameProject(projectName) != null)
        {
            throw new ServiceException("项目名称重复");
        }

        // 暂时不操作真实文件系统，仅保存假路径
        String fakePath = "/" + projectName;
        dProjectInfo.setPath(fakePath);

        log.info("新增项目, projectName={}, fakePath={}", projectName, fakePath);

        return dProjectInfoMapper.insertDProjectInfo(dProjectInfo);
    }

    @Override
    public int updateDProjectInfo(DProjectInfo dProjectInfo)
    {
        DProjectInfo oldProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(dProjectInfo.getProjectId());
        if (oldProjectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }

        if (dProjectInfo.getPath() == null || dProjectInfo.getPath().trim().isEmpty())
        {
            dProjectInfo.setPath(oldProjectInfo.getPath());
        }

        if (!dProjectInfo.getPath().startsWith("/")
                || !dProjectInfo.getPath().substring(1).matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("项目路径无效");
        }

        // 暂时不移动/创建/检查真实目录，仅更新数据库中的 path
        redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + dProjectInfo.getProjectId());
        dProjectInfo.setUpdateTime(DateUtils.getNowDate());

        log.info("更新项目, projectId={}, oldPath={}, newPath={}",
                dProjectInfo.getProjectId(),
                oldProjectInfo.getPath(),
                dProjectInfo.getPath());

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

            // 暂时不检查、不删除真实项目目录，仅删除数据库记录
            int rows = dProjectInfoMapper.deleteDProjectInfoByProjectId(projectId);
            if (rows > 0)
            {
                deleteProjectIds.add(projectId);
                redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + projectId);
                log.info("删除项目, projectId={}, path={}", projectId, projectInfo.getPath());
            }
            else
            {
                errorMsg.append("删除项目失败: ").append(projectId).append("\n");
            }
        }

        if (errorMsg.length() > 0 && deleteProjectIds.size() != projectIds.length)
        {
            throw new ServiceException(errorMsg.toString());
        }

        return deleteProjectIds.size();
    }

    @Override
    public int deleteDProjectInfoByProjectId(Long projectId)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }
        // 暂时不检查、不删除真实项目目录，仅删除数据库记录
        redisCache.deleteObject(CacheConstants.PROJECT_INFO_KEY + projectId);

        log.info("删除项目, projectId={}, path={}", projectId, projectInfo.getPath());

        return dProjectInfoMapper.deleteDProjectInfoByProjectId(projectId);
    }

    @Override
    public List<DProjectInfo> selectAllDProjectInfo()
    {
        return dProjectInfoMapper.selectAllDProjectInfo();
    }

}
