package com.ruoyi.Xidian.service;

import java.util.List;
import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.TreeTable;
import com.ruoyi.Xidian.domain.VO.TreeTableVo;

/**
 * 试验信息主Service接口
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
public interface IDExperimentInfoService 
{
    public List<DProjectInfo> buildDProjectInfoTree();
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
     * 批量删除试验信息主
     * 
     * @param experimentIds 需要删除的试验信息主主键集合
     * @return 结果
     */
    public int deleteDExperimentInfoByExperimentIds(String[] experimentIds);

    /**
     * 删除试验信息主信息
     * 
     * @param experimentId 试验信息主主键
     * @return 结果
     */
    public int deleteDExperimentInfoByExperimentId(String experimentId);


    List<TreeTableVo> getExperimentInfoTree();

    String getExperimentPath(String experimentId);

    String getExperimentRelativePath(String experimentId);

    List<TreeTableVo> selectExperimentInfoTree(TreeTable treeTable);
}
