package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.TreeTable;
import com.ruoyi.Xidian.domain.VO.TreeTableVo;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DExperimentInfoServiceImpl implements IDExperimentInfoService
{
    private static final Logger log = LoggerFactory.getLogger(DExperimentInfoServiceImpl.class);

    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private DdataMapper ddataMapper;

    @Autowired
    private RedisCache redisCache;

    private final String profile = RuoYiConfig.getProfile() + "/data";

    @Override
    public List<TreeTableVo> selectExperimentInfoTree(TreeTable treeTable) {
        List<TreeTableVo> treeTables = new ArrayList<>();
        Map<Long, Integer> hashtable = new HashMap<>();
        int index = 0;
        if (treeTable.getId() == null || !treeTable.getId().matches(".*[a-zA-Z-].*")) {
            DProjectInfo dProjectInfo = new DProjectInfo();
            if (StringUtils.isNotEmpty(treeTable.getId())) {
                dProjectInfo.setProjectId(Long.valueOf(treeTable.getId()));
            }
            dProjectInfo.setProjectName(treeTable.getName());
            List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(dProjectInfo);
            for (DProjectInfo projectInfo : dProjectInfos){
                TreeTableVo vo = new TreeTableVo();
                vo.setId(projectInfo.getProjectId().toString());
                vo.setName(projectInfo.getProjectName());
                vo.setType("project");
                hashtable.put(projectInfo.getProjectId(), index);
                index++;
                treeTables.add(vo);
            }
        }
        DExperimentInfo query = new DExperimentInfo();
        query.setExperimentId(treeTable.getId());
        query.setExperimentName(treeTable.getName());
        List<DExperimentInfo> experimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(query);
        for (DExperimentInfo experimentInfo : experimentInfos){
            TreeTableVo vo = new TreeTableVo();
            vo.setId(experimentInfo.getExperimentId());
            vo.setName(experimentInfo.getExperimentName());
            vo.setType("experiment");
            vo.setParentName(experimentInfo.getProjectName());
            int parentIndex = hashtable.getOrDefault(experimentInfo.getProjectId(), -1);
            if (parentIndex != -1)
            {
                ((List<TreeTableVo>) treeTables.get(parentIndex).getChildren()).add(vo);
            }
            else
            {
                Long parentId = experimentInfo.getProjectId();
                DProjectInfo dProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(parentId);
                hashtable.put(dProjectInfo.getProjectId(), index);
                TreeTableVo projectVo = new TreeTableVo();
                projectVo.setId(parentId.toString());
                projectVo.setName(dProjectInfo.getProjectName());
                projectVo.setType("project");
                treeTables.add(projectVo);
                ((List<TreeTableVo>) treeTables.get(index).getChildren()).add(vo);
                index++;
            }
        }
        return treeTables;
    }

    @Override
    public DExperimentInfo selectDExperimentInfoByExperimentId(String experimentId)
    {
        if (redisCache.getCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId) != null)
        {
            DExperimentInfo dExperimentInfo = redisCache.getCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
            dExperimentInfo.setFullPath(getFrontEndExperimentPath(experimentId));
            return dExperimentInfo;
        }
        DExperimentInfo dExperimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (dExperimentInfo != null)
        {
            redisCache.setCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId, dExperimentInfo, 30, TimeUnit.MINUTES);
            dExperimentInfo.setFullPath(getFrontEndExperimentPath(experimentId));
        }
        return dExperimentInfo;
    }

    @Override
    public List<DProjectInfo> buildDProjectInfoTree()
    {
        List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(new DProjectInfo());
        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        for (DProjectInfo dProjectInfo : dProjectInfos)
        {
            dExperimentInfo.setProjectId(dProjectInfo.getProjectId());
            List<DExperimentInfo> dExperimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
            for (DExperimentInfo item : dExperimentInfos)
            {
                item.setParentId(dProjectInfo.getProjectId());
                item.setParentName(dProjectInfo.getProjectName());
            }
            dProjectInfo.setChildren(dExperimentInfos);
        }
        return dProjectInfos;
    }

    @Override
    public List<DExperimentInfo> selectDExperimentInfoList(DExperimentInfo dExperimentInfo)
    {
        return dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String insertDExperimentInfo(DExperimentInfo dExperimentInfo)
    {
        if (dExperimentInfo.getExperimentName() == null
                || !dExperimentInfo.getExperimentName().matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            return "试验名称无效";
        }
        requireProject(dExperimentInfo.getProjectId());
        List<DExperimentInfo> dExperimentInfos = dExperimentInfoMapper
                .selectExperimentByProjectIdAndExperimentName(dExperimentInfo.getProjectId(), dExperimentInfo.getExperimentName());
        for (DExperimentInfo dExperimentInfo1 : dExperimentInfos) {
            if (dExperimentInfo1.getExperimentId() == null
                    || !dExperimentInfo1.getExperimentId().equals(dExperimentInfo.getExperimentId())) {
                return "同一项目下试验名称不能重复";
            }
        }
        dExperimentInfo.setPath("/" + dExperimentInfo.getExperimentName());
        dExperimentInfoMapper.insertDExperimentInfo(dExperimentInfo);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDExperimentInfo(DExperimentInfo dExperimentInfo)
    {
        dExperimentInfo.setUpdateTime(DateUtils.getNowDate());

        if (dExperimentInfo.getPath() == null
                || !dExperimentInfo.getPath().startsWith("/")
                || !dExperimentInfo.getPath().substring(1).matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("试验路径无效");
        }

        String experimentId = dExperimentInfo.getExperimentId();
        DExperimentInfo oldExperimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (oldExperimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        requireProject(dExperimentInfo.getProjectId());
        List<DExperimentInfo> sameNameExperiments = dExperimentInfoMapper
                .selectExperimentByProjectIdAndExperimentName(dExperimentInfo.getProjectId(), dExperimentInfo.getExperimentName());
        for (DExperimentInfo item : sameNameExperiments)
        {
            if (!experimentId.equals(item.getExperimentId()))
            {
                throw new ServiceException("同一项目下试验名称不能重复");
            }
        }

        redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
        redisCache.deleteObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentId);

        log.info("更新试验信息, experimentId={}, projectId={}, fakePath={}",
                experimentId,
                dExperimentInfo.getProjectId(),
                dExperimentInfo.getPath());

        return dExperimentInfoMapper.updateDExperimentInfo(dExperimentInfo);
    }

    @Override
    public int deleteDExperimentInfoByExperimentIds(String[] experimentIds)
    {
        if (experimentIds == null || experimentIds.length == 0)
        {
            return 0;
        }
        if ("0".equals(experimentIds[0]))
        {
            return 1;
        }

        List<DExperimentInfo> experimentInfos =
                dExperimentInfoMapper.selectDExperimentInfoByExperimentIds(Arrays.asList(experimentIds));
        if (experimentInfos.size() != experimentIds.length)
        {
            throw new ServiceException("试验信息不存在");
        }

        List<String> deleteExperimentIds = new ArrayList<>();

        for (DExperimentInfo experimentInfo : experimentInfos)
        {
            dExperimentInfoMapper.deleteDExperimentInfoByExperimentId(experimentInfo.getExperimentId());
            deleteExperimentIds.add(experimentInfo.getExperimentId());
            redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentInfo.getExperimentId());
            redisCache.deleteObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentInfo.getExperimentId());
            log.info("删除试验信息, experimentId={}", experimentInfo.getExperimentId());
        }
        return 1;
    }

    @Override
    public int deleteDExperimentInfoByExperimentId(String experimentId)
    {
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (experimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        int dataCount = ddataMapper.countByExperimentId(experimentId);
        if (dataCount > 0)
        {
            throw new ServiceException("试验下存在数据，不能删除");
        }

        redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
        redisCache.deleteObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentId);

        log.info("删除试验信息, experimentId={}, projectId={}, fakePath={}",
                experimentId,
                experimentInfo.getProjectId(),
                experimentInfo.getPath());

        return dExperimentInfoMapper.deleteDExperimentInfoByExperimentId(experimentId);
    }

    @Override
    public List<TreeTableVo> getExperimentInfoTree()
    {
        return selectExperimentInfoTree(new TreeTable());
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

    public String getExperimentPath(String experimentId){
        if(redisCache.getCacheObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentId) != null){
            return redisCache.getCacheObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentId).toString();
        }
        String relativePath = getExperimentRelativePath(experimentId);
        redisCache.setCacheObject(CacheConstants.EXPERIMENT_PATH_KEY + experimentId, profile + relativePath, 30, TimeUnit.MINUTES);
        return profile + relativePath;
    }

    @Override
    public String getExperimentRelativePath(String experimentId){
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (experimentInfo == null)
        {
            throw new ServiceException("试验不能为空");
        }
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        String projectName = projectInfo.getProjectName();
        String experimentName = experimentInfo.getExperimentName();
        if (StringUtils.isEmpty(projectName) || StringUtils.isEmpty(experimentName))
        {
            throw new ServiceException("试验路径无效");
        }
        return "/" + projectName + "/" + experimentName;
    }

    //返回前端所需的试验信息路径（./data/项目路径/试验路径/），当前仅用于展示假路径
    public String getFrontEndExperimentPath(String experimentId){
        return getExperimentPath(experimentId).replace(profile, "./data");
    }
}
