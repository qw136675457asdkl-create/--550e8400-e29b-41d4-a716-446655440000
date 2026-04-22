package com.ruoyi.Xidian.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.Xidian.mapper.DTargetInfoMapper;
import com.ruoyi.Xidian.domain.DTargetInfo;
import com.ruoyi.Xidian.service.IDTargetInfoService;

/**
 * 测试目标信息Service业务层处理
 *
 * @author ruoyi
 * @date 2026-01-24
 */
@Service
public class DTargetInfoServiceImpl implements IDTargetInfoService
{
    private static final Logger log = LoggerFactory.getLogger(DTargetInfoServiceImpl.class);

    @Autowired
    private DTargetInfoMapper dTargetInfoMapper;

    @Autowired
    private RedisCache redisCache;

    /**
     * 查询测试目标信息
     *
     * @param targetId 测试目标信息主键
     * @return 测试目标信息
     */
    @Override
    public DTargetInfo selectDTargetInfoByTargetId(String targetId)
    {
        // 先从Redis缓存中获取
        DTargetInfo dTargetInfo = redisCache.getCacheObject(CacheConstants.EXPERIMENT_TARGET_KEY + targetId);
        if (dTargetInfo != null) {
            return dTargetInfo;
        }

        // 如果缓存中没有，则从数据库查询
        dTargetInfo = dTargetInfoMapper.selectDTargetInfoByTargetId(targetId);

        // 将查询结果缓存到Redis中
        if (dTargetInfo != null) {
            redisCache.setCacheObject(CacheConstants.EXPERIMENT_TARGET_KEY + targetId, dTargetInfo, 30, TimeUnit.MINUTES);
        }

        return dTargetInfo;
    }

    /**
     * 查询测试目标信息列表
     *
     * @param dTargetInfo 测试目标信息
     * @return 测试目标信息
     */
    @Override
    public List<DTargetInfo> selectDTargetInfoList(DTargetInfo dTargetInfo)
    {
        return dTargetInfoMapper.selectDTargetInfoList(dTargetInfo);
    }

    /**
     * 新增测试目标信息
     *
     * @param dTargetInfo 测试目标信息
     * @return 结果
     */
    @Override
    public int insertDTargetInfo(DTargetInfo dTargetInfo)
    {
        dTargetInfo.setCreateTime(DateUtils.getNowDate());
        log.info("新增测试目标信息, targetId={}", dTargetInfo.getTargetId());
        return dTargetInfoMapper.insertDTargetInfo(dTargetInfo);
    }

    /**
     * 修改测试目标信息
     *
     * @param dTargetInfo 测试目标信息
     * @return 结果
     */
    @Override
    public int updateDTargetInfo(DTargetInfo dTargetInfo)
    {
        dTargetInfo.setUpdateTime(DateUtils.getNowDate());
        // 删除缓存中的数据
        redisCache.deleteObject(CacheConstants.EXPERIMENT_TARGET_KEY + dTargetInfo.getTargetId());
        log.info("更新测试目标信息, targetId={}", dTargetInfo.getTargetId());
        return dTargetInfoMapper.updateDTargetInfo(dTargetInfo);
    }

    /**
     * 批量删除测试目标信息
     *
     * @param targetIds 需要删除的测试目标信息主键
     * @return 结果
     */
    @Override
    public int deleteDTargetInfoByTargetIds(String[] targetIds)
    {
        // 删除缓存中的数据
        for (String targetId : targetIds) {
            redisCache.deleteObject(CacheConstants.EXPERIMENT_TARGET_KEY + targetId);
            log.info("删除测试目标信息, targetId={}", targetId);
        }
        return dTargetInfoMapper.deleteDTargetInfoByTargetIds(targetIds);
    }

    /**
     * 删除测试目标信息信息
     *
     * @param targetId 测试目标信息主键
     * @return 结果
     */
    @Override
    public int deleteDTargetInfoByTargetId(String targetId)
    {
        // 删除缓存中的数据
        redisCache.deleteObject(CacheConstants.EXPERIMENT_TARGET_KEY + targetId);
        log.info("删除测试目标信息, targetId={}", targetId);
        return dTargetInfoMapper.deleteDTargetInfoByTargetId(targetId);
    }
     /**
     * 查询测试目标类型列表
     *
     * @return 测试目标类型列表
     */
    @Override
    public List<String> selectTargetTypeList()
    {
        return dTargetInfoMapper.selectTargetTypeList();
    }
}
