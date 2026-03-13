package com.ruoyi.Xidian.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import com.ruoyi.common.config.RuoYiConfig;
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

    private final String profile = RuoYiConfig.getProfile() + "/data";

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
        String projectPath = profile + "/" + dProjectInfo.getProjectName();
        String pathStr = dProjectInfo.getProjectName();
        Path path= Paths.get(projectPath);
        //检查路径是否包含无效字符
        if(!dProjectInfo.getProjectName().matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$")) {
            throw new ServiceException("项目名称只能包含字母、数字、下划线、短横线和中文字符");
        }
        //检查路径是否已存在
        if(Files.exists(path)){
            pathStr = dProjectInfo.getProjectName()+ UUID.randomUUID().toString().substring(0, 5);
            path= Paths.get(profile + "/" + pathStr);
        }
        try{
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new ServiceException("创建项目目录失败：" + e.getMessage());
        }
        //存储相对路径
        dProjectInfo.setPath("/"+pathStr);
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
        String oldProjectPath= profile + dProjectInfoMapper.selectDProjectInfoByProjectId(dProjectInfo.getProjectId()).getPath();
        String newProjectPath= profile + dProjectInfo.getPath();
        Path oldPath= Paths.get(oldProjectPath);
        Path newPath= Paths.get(newProjectPath);
        //检查新路径是否已存在
        if(Files.exists(newPath)){
            throw new ServiceException("新路径已存在，请重新输入");
        }
        if(!dProjectInfo.getPath().startsWith("/") || !dProjectInfo.getPath().substring(1).matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$")) {
            throw new ServiceException("新路径只能包含字母、数字、下划线、短横线和中文字符");
        }
        try{
            if(!newProjectPath.equals(oldProjectPath)) {
                Files.move(oldPath, newPath);
            }
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
            String ProjectPath= profile + dProjectInfo.getPath();
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
            String ProjectPath= profile + dProjectInfo.getPath();
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
