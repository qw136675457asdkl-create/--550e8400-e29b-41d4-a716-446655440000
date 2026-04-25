package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import java.util.List;

@Data
public class ICDRequest {
    private ProjectInfoDTO projectInfo;
    private TargetInfoDTO targetInfo;
    private ExperimentInfoDTO experimentInfo;
    private List<DataRelation> dataRelation;
}
