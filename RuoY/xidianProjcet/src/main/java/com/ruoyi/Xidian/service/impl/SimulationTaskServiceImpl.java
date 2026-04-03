package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.TaskCreateRequest;
import com.ruoyi.Xidian.domain.DTO.TaskDataGroupDTO;
import com.ruoyi.Xidian.domain.DTO.TaskDataItemDTO;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.Xidian.service.SimulationTaskService;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SimulationTaskServiceImpl implements SimulationTaskService {
    private static final String ROUTING_KEY = "simulation_task_routing";
    private static final String EXCHANGE_NAME = "simulation_task_exchange";

    private final TaskMapper taskMapper;
    private final TaskDataGroupMapper taskDataGroupMapper;
    private final DExperimentInfoServiceImpl dExperimentInfoService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisCache redisCache;

    public SimulationTaskServiceImpl(
            TaskMapper taskMapper,
            TaskDataGroupMapper taskDataGroupMapper,
            DExperimentInfoServiceImpl dExperimentInfoService,
            RabbitTemplate rabbitTemplate,
            RedisCache redisCache
    ) {
        this.taskMapper = taskMapper;
        this.taskDataGroupMapper = taskDataGroupMapper;
        this.dExperimentInfoService = dExperimentInfoService;
        this.rabbitTemplate = rabbitTemplate;
        this.redisCache = redisCache;
    }

    @Override
    public List<Task> selectList() {
        return taskMapper.selectList();
    }

    @Override
    public Task insert(TaskCreateRequest request) {
        if (!hasSubTaskConfig(request)) {
            throw new ServiceException("至少需要配置一个子任务");
        }

        Date now = new Date();
        Task task = buildTask(request, now);
        taskMapper.insert(task);

        if (task.getId() == null) {
            throw new ServiceException("主任务创建失败，未获取到任务ID");
        }

        List<TaskDataGroup> subTasks = buildSubTasks(task.getId(), request);
        for (TaskDataGroup group : subTasks) {
            taskDataGroupMapper.insert(group);
            if (group.getId() == null) {
                throw new ServiceException("子任务创建失败，未获取到子任务ID");
            }
            if (group.getTargetNum() < 3){
                throw new ServiceException("目标数量不少于三个");
            }
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, group);
        }

        redisCache.setCacheObject(task.getId().toString(), subTasks.size());
        task.setDataGroups(subTasks);
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
        redisCache.deleteObject(id.toString());
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
        task.setCreateBy(SecurityUtils.getUsername());
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
                group.setGroupName(StringUtils.isNotEmpty(itemDTO.getDataName()) ? itemDTO.getDataName() : groupDTO.getGroupName());
                group.setRequestId(itemDTO.getRequestId());
                group.setOutputType(itemDTO.getOutputType());
                group.setOutputDirectory(itemDTO.getOutputDirectory());
                group.setDataSourceType(itemDTO.getDataSourceType());
                group.setSourceFileName(itemDTO.getSourceFileName());
                group.setStartTimeMs(itemDTO.getStartTimeMs());
                group.setEndTimeMs(itemDTO.getEndTimeMs());
                group.setFrequencyHz(itemDTO.getFrequencyHz());
                group.setTargetNum(itemDTO.getTargetNum());
                group.setStartVelocity(itemDTO.getStartVelocity() != null ? itemDTO.getStartVelocity() : request.getStartVelocity());
                group.setStartAttitude(itemDTO.getStartAttitude() != null ? itemDTO.getStartAttitude() : request.getStartAttitude());
                group.setRandomSeeds(itemDTO.getRandomSeeds() != null ? itemDTO.getRandomSeeds() : request.getRandomSeeds());
                group.setStatus(TaskStatusEnum.DRAFT.name());
                subTasks.add(group);
            }
        }
        return subTasks;
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
