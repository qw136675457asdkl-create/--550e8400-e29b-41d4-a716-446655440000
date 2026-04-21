package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.TaskCreateRequest;
import com.ruoyi.Xidian.domain.DTO.TaskDataGroupDTO;
import com.ruoyi.Xidian.domain.DTO.TaskDataItemDTO;
import com.ruoyi.Xidian.domain.DTO.TaskDataMetricDTO;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.TaskDataMetric;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.Xidian.service.SimulationTaskService;
import com.ruoyi.Xidian.utils.NickNameUtil;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationTaskServiceImpl implements SimulationTaskService {
    private static final Logger log = LoggerFactory.getLogger(SimulationTaskServiceImpl.class);
    private static final String ROUTING_KEY = "simulation_task_routing";
    private static final String EXCHANGE_NAME = "simulation_task_exchange";

    private final TaskMapper taskMapper;
    private final TaskDataGroupMapper taskDataGroupMapper;
    private final DExperimentInfoServiceImpl dExperimentInfoService;
    private final RabbitTemplate rabbitTemplate;
    private final DdataMapper dataMapper;

    public SimulationTaskServiceImpl(
            TaskMapper taskMapper,
            TaskDataGroupMapper taskDataGroupMapper,
            DExperimentInfoServiceImpl dExperimentInfoService,
            RabbitTemplate rabbitTemplate,
            DdataMapper dataMapper
    ) {
        this.taskMapper = taskMapper;
        this.taskDataGroupMapper = taskDataGroupMapper;
        this.dExperimentInfoService = dExperimentInfoService;
        this.rabbitTemplate = rabbitTemplate;
        this.dataMapper = dataMapper;
    }

    @Override
    public List<Task> selectList() {
        return taskMapper.selectList();
    }

    @Override
    public Task insert(TaskCreateRequest request) {
        log.info("Start creating simulation task, taskName={}, experimentId={}, testId={}",
                request == null ? null : request.getTaskName(),
                request == null ? null : request.getExperimentId(),
                request == null ? null : request.getTestId());
        if (!hasSubTaskConfig(request)) {
            throw new ServiceException("至少需要配置一个子任务");
        }
        Date now = new Date();
        Task task = buildTask(request, now);
        log.info("Built main task entity, taskName={}, experimentId={}, taskCode={}",
                task.getTaskName(), task.getExperimentId(), task.getTaskCode());
        taskMapper.insert(task);
        log.info("Main task inserted, taskId={}", task.getId());
        if (task.getId() == null) {
            throw new ServiceException("主任务创建失败，未获取到任务ID");
        }
        List<TaskDataGroup> subTasks = buildSubTasks(task.getId(), request);
        log.info("Built sub tasks, taskId={}, subTaskCount={}", task.getId(), subTasks.size());
        for (TaskDataGroup group : subTasks) {
            log.info("Validating sub task before insert, taskId={}, groupName={}, dataName={}, outputType={}, targetNum={}",
                    task.getId(), group.getGroupName(), group.getDataName(), group.getOutputType(), group.getTargetNum());
            if (dataMapper.selectSameNameFile(task.getExperimentId(),"/" + group.getDataName() + group.getOutputType()) != null){
                log.warn("Duplicate data name detected, taskId={}, groupName={}, dataName={}, outputType={}",
                        task.getId(), group.getGroupName(), group.getDataName(), group.getOutputType());
                taskMapper.deleteById(task.getId());
                throw new ServiceException(group.getGroupName() + "数据名称重复，请重新输入");
            }
            if (group.getTargetNum() < 3 && (Objects.equals(group.getDataName(), "radar_track") || Objects.equals(group.getDataName(), "ads_b"))){
                log.warn("Target number validation failed, taskId={}, groupName={}, dataName={}, targetNum={}",
                        task.getId(), group.getGroupName(), group.getDataName(), group.getTargetNum());
                taskMapper.deleteById(task.getId());
                throw new ServiceException("目标数量不少于三个");
            }
        }
        taskDataGroupMapper.batchInsert(subTasks);
        log.info("Sub tasks batch inserted, taskId={}, subTaskCount={}", task.getId(), subTasks.size());
        task.setDataGroups(subTasks);
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, task);
        log.info("Simulation task sent to queue, taskId={}, routingKey={}, exchangeName={}",
                task.getId(), ROUTING_KEY, EXCHANGE_NAME);
        return task;
    }

    @Override
    public List<Task> selectTaskList(Task task) {
        return taskMapper.selectTaskList(task);
    }

    @Override
    public Task selectById(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        task.setDataGroups(taskDataGroupMapper.selectByTaskId(id));
        return task;
    }

    @Override
    public void deleteTask(Long id) {
        taskDataGroupMapper.deleteByTaskId(id);
        taskMapper.deleteById(id);
    }

    private Task buildTask(TaskCreateRequest request, Date now) {
        Task task = new Task();
        task.setTaskCode(UUID.randomUUID().toString());
        task.setTaskName(request.getTaskName());
        task.setProjectId(request.getProjectId() == null ? null : request.getProjectId().intValue());
        task.setExperimentId(resolveExperimentId(request));
        task.setCarrierType(request.getCarrierType());
        task.setMotionModel(request.getMotionModel());
        task.setStartCoordinate(request.getStartCoordinate());
        task.setEndCoordinate(request.getEndCoordinate());
        task.setStatus(TaskStatusEnum.DRAFT.name());
        task.setCreateBy(NickNameUtil.getNickName());
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setPath(dExperimentInfoService.getExperimentRelativePath(task.getExperimentId()));
        task.setDataCategorySummary(buildDataCategorySummary(request));
        return task;
    }

    private List<TaskDataGroup> buildSubTasks(Long taskId, TaskCreateRequest request) {
        List<TaskDataGroup> subTasks = new ArrayList<>();
        for (TaskDataGroupDTO groupDTO : defaultIfNull(request.getDataGroups())) {
            if (!Boolean.TRUE.equals(groupDTO.getEnabled())) {
                continue;
            }
            for (TaskDataItemDTO itemDTO : defaultIfNull(groupDTO.getItems())) {
                TaskDataGroup group = new TaskDataGroup();
                group.setTaskId(taskId);
                group.setGroupName(groupDTO.getGroupName());
                group.setSortNo(groupDTO.getSortNo());
                group.setDataName(StringUtils.isNotEmpty(itemDTO.getDataName()) ? itemDTO.getDataName() : groupDTO.getGroupName());
                group.setRequestId(itemDTO.getRequestId());
                group.setOutputType(itemDTO.getOutputType());
                group.setDataSourceType(itemDTO.getDataSourceType());
                group.setSourceFileName(itemDTO.getSourceFileName());
                group.setStartTimeMs(itemDTO.getStartTimeMs());
                group.setEndTimeMs(itemDTO.getEndTimeMs());
                group.setFrequencyHz(itemDTO.getFrequencyHz());
                group.setTargetNum(itemDTO.getTargetNum());
                group.setMetric(buildMetrics(itemDTO.getMetrics()));
                group.setStatus(TaskStatusEnum.DRAFT.name());
                subTasks.add(group);
            }
        }
        return subTasks;
    }

    private List<TaskDataMetric> buildMetrics(List<TaskDataMetricDTO> metricDTOs) {
        List<TaskDataMetric> metrics = new ArrayList<>();
        for (TaskDataMetricDTO metricDTO : defaultIfNull(metricDTOs)) {
            TaskDataMetric metric = new TaskDataMetric();
            metric.setFieldName(metricDTO.getFieldName());
            metric.setDataType(metricDTO.getDataType());
            metric.setRecommendedValue(metricDTO.getRecommendedValue());
            metric.setFluctuationRange(metricDTO.getFluctuationRange());
            metric.setDescription(metricDTO.getDescription());
            metric.setSortNo(metricDTO.getSortNo());
            metrics.add(metric);
        }
        return metrics;
    }

    private String buildDataCategorySummary(TaskCreateRequest request) {
        return defaultIfNull(request.getDataGroups()).stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(TaskDataGroupDTO::getGroupName)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String resolveExperimentId(TaskCreateRequest request) {
        if (StringUtils.isNotEmpty(request.getExperimentId())) {
            return request.getExperimentId();
        }
        return request.getTestId() == null ? null : String.valueOf(request.getTestId());
    }

    private boolean hasSubTaskConfig(TaskCreateRequest request) {
        return defaultIfNull(request.getDataGroups()).stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .anyMatch(group -> !defaultIfNull(group.getItems()).isEmpty());
    }

    private <T> List<T> defaultIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

}
