package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.BackupData;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IDdataService {
    /**
     * 查询数据列表
     * @param ddataInfo
     * @return
     */
    List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo);

    DdataInfo selectDdataInfoByDdataId(Integer id);

    Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file);
    Integer insertDdataInfos(DdataInfo ddataInfo, List<MultipartFile> files);
    Integer insertDdataInfoByPath(DdataInfo ddataInfo);
    Integer transportDdataFile(DdataInfo ddataInfo);
    Integer updateDdataInfo(DdataInfo ddataInfo);

    Integer deleteDdataInfos(List<Integer> ids);

    List<Map<String, Object>> getMovePathTree();

    void uploadFiles(List<MultipartFile> files, String experimentId);

    void syncSimulationResultFiles(
            String experimentId,
            List<String> storedFileNames,
            List<String> sourceFileNames,
            String createBy,
            String targetCategory, List<TaskDataGroup> taskDataGroup);

    default void uploadFiles(List<MultipartFile> files, List<String> relativePaths, String experimentId)
    {
        uploadFiles(files, experimentId);
    }

    int renameDataName(List<DdataInfo> ddataInfo);

    int backupDataById(Integer id);

    List<BackupData> selectBackupDataList(BackupData backupData);

    String restoreDataFile(Integer BackDataId);
}
