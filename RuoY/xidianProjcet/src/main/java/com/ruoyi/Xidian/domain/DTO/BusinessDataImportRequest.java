package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class BusinessDataImportRequest {
    private String dataName;

    @NotBlank(message = "experimentId is required")
    private String experimentId;

    @NotBlank(message = "targetId is required")
    private String targetId;

    private String targetType;

    @NotBlank(message = "dataType is required")
    private String dataType;

    @NotNull(message = "isSimulation is required")
    private Boolean isSimulation;

    @Valid
    @NotEmpty(message = "files is required")
    private List<BusinessDataImportFile> files;
}
