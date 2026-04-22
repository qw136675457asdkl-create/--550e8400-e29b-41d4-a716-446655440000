package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DTO.TaskCreateRequest;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.SimulationMetricTemplateService;
import com.ruoyi.Xidian.service.SimulationTaskService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/data/simulation")
public class DataSimulationController extends BaseController {
    private final SimulationTaskService simulationTaskService;
    private final IDExperimentInfoService experimentInfoService;
    private final SimulationMetricTemplateService simulationMetricTemplateService;

    public DataSimulationController(
            SimulationTaskService simulationTaskService,
            IDExperimentInfoService experimentInfoService,
            SimulationMetricTemplateService simulationMetricTemplateService
    ) {
        this.simulationTaskService = simulationTaskService;
        this.experimentInfoService = experimentInfoService;
        this.simulationMetricTemplateService = simulationMetricTemplateService;
    }

    @GetMapping("/task/list")
    public TableDataInfo taskList(Task task) {
        startPage();
        return getDataTable(simulationTaskService.selectTaskList(task));
    }

    @PreAuthorize("@ss.hasPermi('data:simulation:insert')")
    @PostMapping("/task/submit")
    @Log(title = "提交仿真任务",businessType = BusinessType.INSERT)
    public AjaxResult submitTask(@RequestBody TaskCreateRequest request) {
        return success(simulationTaskService.insert(request));
    }

    @GetMapping("/task/{id}")
    @Log(title = "查看仿真任务详情",businessType = BusinessType.OTHER)
    public AjaxResult taskDetail(@PathVariable Long id) {
        return success(simulationTaskService.selectById(id));
    }

    @PreAuthorize("@ss.hasPermi('data:simulation:delete')")
    @DeleteMapping("/task/{id}")
    @Log(title = "删除仿真任务",businessType = BusinessType.DELETE)
    public AjaxResult deleteTask(@PathVariable Long id) {
        simulationTaskService.deleteTask(id);
        return success();
    }

    @GetMapping("/metric/templates")
    public AjaxResult listMetricTemplates() {
        return success(simulationMetricTemplateService.listTemplates());
    }

    @GetMapping("/experiment/{experimentId}/files")
    public AjaxResult listExperimentFiles(@PathVariable String experimentId) throws IOException {
        Path experimentPath = Paths.get(experimentInfoService.getExperimentPath(experimentId));
        if (!Files.exists(experimentPath) || !Files.isDirectory(experimentPath)) {
            return success(Collections.emptyList());
        }

        try (Stream<Path> stream = Files.list(experimentPath)) {
            List<String> files = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
            return success(files);
        }
    }
}
