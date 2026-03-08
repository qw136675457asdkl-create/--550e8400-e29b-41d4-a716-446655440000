package com.ruoyi.Xidian.mapper;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.Xidian.domain.DProjectInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目信息Mapper接口
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
@Mapper
public interface DProjectInfoMapper 
{
    /**
     * 查询项目信息
     * 
     * @param projectId 项目信息主键
     * @return 项目信息
     */
    public DProjectInfo selectDProjectInfoByProjectId(Long projectId);

    /**
     * 查询项目信息列表
     * 
     * @param dProjectInfo 项目信息
     * @return 项目信息集合
     */
    public List<DProjectInfo> selectDProjectInfoList(DProjectInfo dProjectInfo);

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
     * 删除项目信息
     * 
     * @param projectId 项目信息主键
     * @return 结果
     */
    public int deleteDProjectInfoByProjectId(Long projectId);

    /**
     * 批量删除项目信息
     * 
     * @param projectIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDProjectInfoByProjectIds(Long[] projectIds);

    public List<DProjectInfo> selectAllDProjectInfo();

    List<DProjectInfo> selectDProjectInfoByProjectIds(List<Long> projectIds);
}
