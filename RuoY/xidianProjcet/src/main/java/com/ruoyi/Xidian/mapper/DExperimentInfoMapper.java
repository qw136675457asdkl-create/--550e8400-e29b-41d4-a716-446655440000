package com.ruoyi.Xidian.mapper;

import java.util.List;
import com.ruoyi.Xidian.domain.DExperimentInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 试验信息主Mapper接口
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
@Mapper
public interface DExperimentInfoMapper 
{
    /**
     * 查询试验信息主
     * 
     * @param experimentId 试验信息主主键
     * @return 试验信息主
     */
    public DExperimentInfo selectDExperimentInfoByExperimentId(String experimentId);

    /**
     * 查询试验信息主列表
     * 
     * @param dExperimentInfo 试验信息主
     * @return 试验信息主集合
     */
    public List<DExperimentInfo> selectDExperimentInfoList(DExperimentInfo dExperimentInfo);

    /**
     * 新增试验信息主
     * 
     * @param dExperimentInfo 试验信息主
     * @return 结果
     */
    public int insertDExperimentInfo(DExperimentInfo dExperimentInfo);

    /**
     * 修改试验信息主
     * 
     * @param dExperimentInfo 试验信息主
     * @return 结果
     */
    public int updateDExperimentInfo(DExperimentInfo dExperimentInfo);

    /**
     * 删除试验信息主
     * 
     * @param experimentId 试验信息主主键
     * @return 结果
     */
    public int deleteDExperimentInfoByExperimentId(String experimentId);

    /**
     * 批量删除试验信息主
     * 
     * @param experimentIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDExperimentInfoByExperimentIds(String[] experimentIds);

    List<DExperimentInfo> selectDExperimentInfoByExperimentIds(List<String> experimentIds);

    public DExperimentInfo selectSamePathExperiment(@Param("experimentName") String experimentName,@Param("projectId") Long projectId);

    public DExperimentInfo selectExperimentByProjectNameAndExperimentName(@Param("experimentName") String experimentName,@Param("projectName") String projectName);
}
