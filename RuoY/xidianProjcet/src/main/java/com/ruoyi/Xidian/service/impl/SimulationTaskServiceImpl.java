package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.Xidian.service.SimulationTaskService;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SimulationTaskServiceImpl implements SimulationTaskService {
    private final TaskMapper taskMapper;
    private final TaskDataGroupMapper taskDataGroupMapper;
    private final DExperimentInfoServiceImpl dExperimentInfoService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisCache redisCache;
    public SimulationTaskServiceImpl(TaskMapper taskMapper, TaskDataGroupMapper taskDataGroupMapper,
                                     DExperimentInfoServiceImpl dExperimentInfoService , RabbitTemplate rabbitTemplate, RedisCache redisCache) {
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

    private final String QueueName = "simulation_task_queue";

    @Override
    public void insert(Task task) {
        //路由键
        String routingKey = "simulation_task_routing";
        //交换机
        String exchangeName = "simulation_task_exchange";
        //保存任务的初始状态
        task.setTaskCode(UUID.randomUUID().toString());
        task.setStatus(TaskStatusEnum.DRAFT.toString());
        task.setCreateBy(SecurityUtils.getUsername());
        task.setPath(dExperimentInfoService.getExperimentPath(task.getExperimentID()));
        taskMapper.insert(task);
//        Integer TaskGroups = task.getDataGroups().size();
//        redisCache.setCacheObject(task.getId().toString(), TaskGroups);
//        List<TaskDataGroup> dataGroups = task.getDataGroups();
//        dataGroups.forEach(group -> {
//            group.setTaskId(task.getId());
//            group.setStatus(TaskStatusEnum.DRAFT.toString());
//        });
//        taskDataGroupMapper.insertBatch(dataGroups);
//        for(TaskDataGroup group : dataGroups){
//            //更新子任务状态
//            group.setTaskId(task.getId());
//            group.setStatus(TaskStatusEnum.DRAFT.toString());
//        }
        //将总任务表发给
        rabbitTemplate.convertAndSend(exchangeName,routingKey,task);
    }

    @Override
    public List<Task> selectTaskList(Task task) {
        return taskMapper.selectTaskList(task);
    }

    @Override
    public Task selectById(Long id) {
        Task task = taskMapper.selectById(id);
        if(task == null){
            throw new RuntimeException("任务不存在");
        }
        task.setDataGroups(taskDataGroupMapper.selectByTaskId(id));
        return task;
    }

    @Override
    public void deleteTask(Long id) {
        taskMapper.deleteById(id);
        taskDataGroupMapper.deleteByTaskId(id);
    }
}
