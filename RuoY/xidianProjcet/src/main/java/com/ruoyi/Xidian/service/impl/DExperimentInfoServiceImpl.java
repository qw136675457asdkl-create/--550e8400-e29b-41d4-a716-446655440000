package com.ruoyi.Xidian.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.TreeTableVo;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DTargetInfoMapper;
import com.ruoyi.common.annotation.DataSource;
import com.ruoyi.common.enums.DataSourceType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.stereotype.Service;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;

/**
 * 试验信息主Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
@Service
public class DExperimentInfoServiceImpl implements IDExperimentInfoService {
    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;
    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    /**
     * 查询试验信息主树列表,并完全由后端造树
     *
     * @param treeTableVo 试验信息
     * @return 试验信息
     */
    @Override
    public List<TreeTableVo> selectDExperimentInfoTree(TreeTableVo treeTableVo) {
        List<TreeTableVo> treeTables = new ArrayList<>();
        Map<Long,Integer> hashtable = new HashMap<>();
        int Index=0;
        if (treeTableVo.getId() == null || !treeTableVo.getId().matches(".*[a-zA-Z-].*")) {
            // 查询符合查询条件的项目信息
            DProjectInfo dProjectInfo = new DProjectInfo();

            if (StringUtils.isNotEmpty(treeTableVo.getId())) {
                dProjectInfo.setProjectId(Long.valueOf(treeTableVo.getId()));
            }
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setParams(treeTableVo.getParams());
            dProjectInfo.setCreateBy(treeTableVo.getCreateBy());
            List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(dProjectInfo);


            for (DProjectInfo dProjectInfo1 : dProjectInfos) {
                TreeTableVo vo = new TreeTableVo();
                vo.setId(dProjectInfo1.getProjectId().toString());
                vo.setName(dProjectInfo1.getProjectName());
                vo.setCreateBy(dProjectInfo1.getCreateBy());
                vo.setCreateTime(dProjectInfo1.getCreateTime());
                vo.setContentDesc(dProjectInfo1.getProjectContentDesc());
                vo.setPath(dProjectInfo1.getPath());
                vo.setParentId(0L);
                vo.setAncestors("0");
                vo.setType("project");
                hashtable.put(dProjectInfo1.getProjectId(),Index);
                Index++;
                treeTables.add(vo);
            }
        }


        // 查询符合查询条件的试验信息
        DExperimentInfo dExperimentInfo1 = new DExperimentInfo();
        dExperimentInfo1.setExperimentId(treeTableVo.getId());
        dExperimentInfo1.setExperimentName(treeTableVo.getName());
        dExperimentInfo1.setParams(treeTableVo.getParams());
        dExperimentInfo1.setCreateBy(treeTableVo.getCreateBy());
        List<DExperimentInfo> dExperimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo1);
        for (DExperimentInfo dExperimentInfo : dExperimentInfos) {
            TreeTableVo vo = new TreeTableVo();
            vo.setId(dExperimentInfo.getExperimentId());
            vo.setName(dExperimentInfo.getExperimentName());
            vo.setTargetId(dExperimentInfo.getTargetId());
            vo.setStartTime(dExperimentInfo.getStartTime());
            vo.setTargetType(dExperimentInfo.getTargetType());
            vo.setCreateBy(dExperimentInfo.getCreateBy());
            vo.setCreateTime(dExperimentInfo.getCreateTime());
            vo.setLocation(dExperimentInfo.getLocation());
            vo.setContentDesc(dExperimentInfo.getContentDesc());
            vo.setPath(dExperimentInfo.getPath());
            vo.setParentId(dExperimentInfo.getProjectId());
            vo.setAncestors("0," + dExperimentInfo.getProjectId());
            vo.setType("experiment");
            int parentIndex = hashtable.getOrDefault(vo.getParentId(),-1);
            // 如果父节点存在，将当前节点添加到父节点的子节点列表中,否则添加到根节点列表中
            if(parentIndex!=-1){
                ((List<TreeTableVo>) treeTables.get(parentIndex).getChildren()).add(vo);
            }else{
                treeTables.add(vo);
            }
        }
        return treeTables;
    }


    /**
     * 查询试验信息主
     *
     * @param experimentId 试验信息主主键
     * @return 试验信息主
     */
    @Override
    public DExperimentInfo selectDExperimentInfoByExperimentId(String experimentId) {
        return dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
    }

    /**
     * 构建试验信息主树结构
     */

    @Override
    public List<DProjectInfo> buildDProjectInfoTree() {
        //查询项目信息
        List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(new DProjectInfo());
        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        for (DProjectInfo dProjectInfo : dProjectInfos) {
            dExperimentInfo.setProjectId(dProjectInfo.getProjectId());
            List<DExperimentInfo> dExperimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
            for (DExperimentInfo dExperimentInfo1 : dExperimentInfos) {
                dExperimentInfo1.setParentId(dProjectInfo.getProjectId());
                dExperimentInfo1.setParentName(dProjectInfo.getProjectName());
            }
            dProjectInfo.setChildren(dExperimentInfos);
        }
        return dProjectInfos;
    }

    /**
     * 查询试验信息主列表
     *
     * @param dExperimentInfo 试验信息主
     * @return 试验信息主
     */
    @Override
    public List<DExperimentInfo> selectDExperimentInfoList(DExperimentInfo dExperimentInfo) {
        return dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
    }

    /**
     * 新增试验信息主
     *
     * @param dExperimentInfo 试验信息主
     * @return 结果
     */
    @Override
    public int insertDExperimentInfo(DExperimentInfo dExperimentInfo) {
        String ProjectName=dProjectInfoMapper.selectDProjectInfoByProjectId(dExperimentInfo.getProjectId()).getProjectName();
        String ExperimentPath= "/home/hyy1208/data/" + ProjectName + "/" + dExperimentInfo.getExperimentName();
        Path path= Paths.get(ExperimentPath);
        try{
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new ServiceException("创建试验目录失败：" + e.getMessage());
        }
        //存储相对路径
        dExperimentInfo.setPath("/"+dExperimentInfo.getExperimentName());
        return dExperimentInfoMapper.insertDExperimentInfo(dExperimentInfo);
    }

    /**
     * 修改试验信息主
     *
     * @param dExperimentInfo 试验信息主
     * @return 结果
     */
    @Override
    public int updateDExperimentInfo(DExperimentInfo dExperimentInfo) {
        dExperimentInfo.setUpdateTime(DateUtils.getNowDate());
        //检查路径是否合法
        if(!dExperimentInfo.getPath().startsWith("/")) {
            throw new ServiceException("路径格式错误，必须以'/'开头");
        }
        String ExperimentId=dExperimentInfo.getExperimentId();
        String oldPath=dExperimentInfoMapper.selectDExperimentInfoByExperimentId(ExperimentId).getPath();
        //修改文件路径
        String ParentPath=dProjectInfoMapper.selectDProjectInfoByProjectId(dExperimentInfo.getProjectId()).getPath();
        String ExperimentPath= "/home/hyy1208/data" + ParentPath + dExperimentInfo.getPath();
        Path newPath= Paths.get(ExperimentPath);
        try{
            //检查新路径是否已存在
            if(Files.exists(newPath)&&!newPath.equals(Paths.get("/home/hyy1208/data" + ParentPath + oldPath))){
                throw new ServiceException("新路径已存在，请重新输入");
            }
            Files.move(Paths.get("/home/hyy1208/data" + ParentPath + oldPath),newPath);
        } catch (IOException e) {
            throw new ServiceException("修改试验目录失败：" + e.getMessage());
        }
        return dExperimentInfoMapper.updateDExperimentInfo(dExperimentInfo);
    }

    /**
     * 批量删除试验信息主
     *
     * @param experimentIds 需要删除的试验信息主主键
     * @return 结果
     */
    @Override
    public int deleteDExperimentInfoByExperimentIds(String[] experimentIds) {
        if (experimentIds == null || experimentIds.length == 0) {
            return 0;
        }
        if(experimentIds[0].equals("0")){
            return 1;
        }
        List<DExperimentInfo> experimentInfos = dExperimentInfoMapper.selectDExperimentInfoByExperimentIds(Arrays.asList(experimentIds));
        if (experimentInfos.size() != experimentIds.length) {
            throw new ServiceException("部分试验信息不存在，请刷新后重试");
        }

        List<Path> dirPaths = new ArrayList<>();
        for (DExperimentInfo expInfo : experimentInfos) {
            Long projectId = expInfo.getProjectId();
            if (projectId == null) {
                throw new ServiceException("试验所属项目不存在，试验ID：" + expInfo.getExperimentId());
            }
            String parentPath = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId).getPath();
            String dirPathStr = "/home/hyy1208/data" + parentPath + "/" + expInfo.getPath();
            Path dirPath = Paths.get(dirPathStr);
            dirPaths.add(dirPath);

            try {
                if (Files.exists(dirPath) && Files.list(dirPath).findAny().isPresent()) {
                    throw new ServiceException("试验目录下有文件，不能删除，试验路径：" + expInfo.getPath());
                }
            } catch (IOException e) {
                throw new ServiceException("检查试验目录失败：" + e.getMessage());
            }
        }

        for (Path dirPath : dirPaths) {
            try {
                Files.deleteIfExists(dirPath);
            } catch (IOException e) {
                throw new ServiceException("删除试验目录失败：" + e.getMessage());
            }
        }

        return dExperimentInfoMapper.deleteDExperimentInfoByExperimentIds(experimentIds);
    }

    /**
     * 删除试验信息主信息
     *
     * @param experimentId 试验信息主主键
     * @return 结果
     */
    @Override
    public int deleteDExperimentInfoByExperimentId(String experimentId) {
        return dExperimentInfoMapper.deleteDExperimentInfoByExperimentId(experimentId);
    }

    /**
     * 查询试验信息主树结构
     *
     * @return 试验信息主树结构
     */
    @Override
    public List<TreeTableVo> getExperimentInfoTree() {
        return selectDExperimentInfoTree(new TreeTableVo());
    }
}
