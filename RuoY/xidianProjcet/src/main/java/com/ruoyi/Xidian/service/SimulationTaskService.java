package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DTO.TaskCreateRequest;
import com.ruoyi.Xidian.domain.Task;

import java.util.List;

public interface SimulationTaskService {
    List<Task> selectList();

    Task insert(TaskCreateRequest request);

    List<Task> selectTaskList(Task task);

    Task selectById(Long id);

    void deleteTask(Long id);
}
