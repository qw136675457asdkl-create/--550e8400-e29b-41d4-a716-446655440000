package com.ruoyi.Xidian.service;

import java.util.List;
import com.ruoyi.Xidian.domain.DProjectInfo;

/**
 * 项目信息Service接口
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
public interface IDProjectInfoService 
{
    /**
     * 查询项目信息
     * 
     * @param projectId 项目信息主键
     * @return 项目信息
     */
    public DProjectInfo selectDProjectInfoByProjectId(Long projectId);


    public List<DProjectInfo> selectAllDProjectInfo();

    /**
     * 新增项目信息
     * 
     * @param dProjectInfo 项目信息
     * @return 结果
     */
    public int insertDProjectInfo(DProjectInfo dProjectInfo);

    /**
     * 修改项目信息
     * 
     * @param dProjectInfo 项目信息
     * @return 结果
     */
    public int updateDProjectInfo(DProjectInfo dProjectInfo);

    /**
     * 批量删除项目信息
     * 
     * @param projectIds 需要删除的项目信息主键集合
     * @return 结果
     */
    public int deleteDProjectInfoByProjectIds(Long[] projectIds);

    /**
     * 删除项目信息信息
     * 
     * @param projectId 项目信息主键
     * @return 结果
     */
    public int deleteDProjectInfoByProjectId(Long projectId);
}
