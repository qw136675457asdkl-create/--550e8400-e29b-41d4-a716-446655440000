package com.ruoyi.Xidian.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.ruoyi.common.annotation.DataSource;
import com.ruoyi.common.enums.DataSourceType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.service.IDProjectInfoService;

/**
 * 项目信息Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-01-23
 */
@Service
public class DProjectInfoServiceImpl implements IDProjectInfoService 
{
    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    /**
     * 查询项目信息
     * 
     * @param projectId 项目信息主键
     * @return 项目信息
     */
    @Override
    public DProjectInfo selectDProjectInfoByProjectId(Long projectId)
    {
        return dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
    }

    /**
     * 查询项目信息列表
     * 
     * @param dProjectInfo 项目信息
     * @return 项目信息
     */
    @Override
    public List<DProjectInfo> selectDProjectInfoList(DProjectInfo dProjectInfo)
    {
        return dProjectInfoMapper.selectDProjectInfoList(dProjectInfo);
    }

    /**
     * 新增项目信息
     * 
     * @param dProjectInfo 项目信息
     * @return 结果
     */
    @Override
    public int insertDProjectInfo(DProjectInfo dProjectInfo)
    {
        String projectPath = "/home/hyy1208/data/" + dProjectInfo.getProjectName();
        Path path= Paths.get(projectPath);
        try{
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new ServiceException("创建项目目录失败：" + e.getMessage());
        }
        //存储相对路径
        dProjectInfo.setPath("/"+dProjectInfo.getProjectName());
        return dProjectInfoMapper.insertDProjectInfo(dProjectInfo);
    }

    /**
     * 修改项目信息
     * 
     * @param dProjectInfo 项目信息
     * @return 结果
     */
    @Override
    public int updateDProjectInfo(DProjectInfo dProjectInfo)
    {
        dProjectInfo.setUpdateTime(DateUtils.getNowDate());
        //修改项目目录
        String oldProjectPath= "/home/hyy1208/data" + dProjectInfoMapper.selectDProjectInfoByProjectId(dProjectInfo.getProjectId()).getPath();
        String newProjectPath= "/home/hyy1208/data" + dProjectInfo.getPath();
        Path oldPath= Paths.get(oldProjectPath);
        Path newPath= Paths.get(newProjectPath);
        try{
            //检查新路径是否已存在
            if(Files.exists(newPath)){
                throw new ServiceException("新路径已存在，请重新输入");
            }
            Files.move(oldPath,newPath);
        } catch (IOException e) {
            throw new ServiceException("修改项目目录失败：" + e.getMessage());
        }
        return dProjectInfoMapper.updateDProjectInfo(dProjectInfo);
    }

    /**
     * 批量删除项目信息
     * 
     * @param projectIds 需要删除的项目信息主键
     * @return 结果
     */
    @Override
    public int deleteDProjectInfoByProjectIds(Long[] projectIds)
    {
        if (projectIds[0] == 0) {
            return 1;
        }
        //检查项目目录下是否有文件
        for (Long projectId : projectIds) {
            DProjectInfo dProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
            String ProjectPath= "/home/hyy1208/data" + dProjectInfo.getPath();
            Path path= Paths.get(ProjectPath);
            try{
                if(Files.list(path).findAny().isPresent()){
                    throw new ServiceException("项目目录下有文件，不能删除");
                }
            } catch (IOException e) {
                throw new ServiceException("检查项目目录失败：" + e.getMessage());
            }
        }
        //删除项目目录
        for (Long projectId : projectIds) {
            DProjectInfo dProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
            String ProjectPath= "/home/hyy1208/data/" + dProjectInfo.getProjectName();
            Path path= Paths.get(ProjectPath);
            try{
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new ServiceException("删除项目目录失败：" + e.getMessage());
            }
        }
        return dProjectInfoMapper.deleteDProjectInfoByProjectIds(projectIds);
    }

    /**
     * 删除项目信息信息
     * 
     * @param projectId 项目信息主键
     * @return 结果
     */
    @Override
    public int deleteDProjectInfoByProjectId(Long projectId)
    {
        return dProjectInfoMapper.deleteDProjectInfoByProjectId(projectId);
    }

    @Override
    public List<DProjectInfo> selectAllDProjectInfo(){
        return dProjectInfoMapper.selectAllDProjectInfo();
    }
}
