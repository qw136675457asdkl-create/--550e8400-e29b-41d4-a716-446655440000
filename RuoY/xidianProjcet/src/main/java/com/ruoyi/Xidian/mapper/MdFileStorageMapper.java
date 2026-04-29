package com.ruoyi.Xidian.mapper;

import com.ruoyi.Xidian.domain.MdFileStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MdFileStorageMapper {
    int insertMdFileStorage(MdFileStorage fileStorage);

    int updateMdFileStorage(MdFileStorage fileStorage);

    MdFileStorage selectById(@Param("id") Long id);

    MdFileStorage selectByBucketAndObjectName(@Param("bucketName") String bucketName,
                                              @Param("objectName") String objectName);

    int updateFileStorgeStatus(@Param("list") List<MdFileStorage> mdFileStorageList);

    MdFileStorage selectByBussinessId(@Param("dataInfoId") String dataInfoId);
}
