package com.ruoyi.Xidian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ruoyi.Xidian.domain.Coordinate;
import com.ruoyi.Xidian.domain.DTO.TaskToPy;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.WebSocketServer;
import com.ruoyi.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.ServerException;
import java.util.*;
import java.util.stream.Stream;

@Component
public class TaskListener {
    private static final Logger log = LoggerFactory.getLogger(TaskListener.class);

    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskDataGroupMapper taskDataGroupMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private PythonSimulationService pythonSimulationService;
    @Autowired
    private IDdataService iDdataService;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(
            queues = "simulation_task_queue",
            containerFactory = "simulationTaskListenerContainerFactory"
    )
    public void handleTask(Task task, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Received simulation task message, taskId={}, deliveryTag={}", task == null ? null : task.getId(), deliveryTag);

        Task currentTask = taskMapper.selectById(task.getId());
        if (currentTask == null || TaskStatusEnum.SUCCESS.toString().equals(currentTask.getStatus())) {
            log.info("Task already completed or missing, ack directly, taskId={}, currentStatus={}",
                    task == null ? null : task.getId(),
                    currentTask == null ? null : currentTask.getStatus());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            log.info("Start processing simulation task, taskId={}, taskCode={}, experimentId={}",
                    task.getId(), task.getTaskCode(), task.getExperimentId());
            //更新任务状态
            task.setStatus(TaskStatusEnum.RUNNING.toString());
            task.setUpdateTime(new Date());
            taskMapper.update(task);
            log.info("Task marked as RUNNING, taskId={}", task.getId());
            task.setDataGroups(taskDataGroupMapper.selectByTaskId(task.getId()));
            log.info("Loaded task data groups, taskId={}, groupCount={}",
                    task.getId(), task.getDataGroups() == null ? 0 : task.getDataGroups().size());
            TaskToPy taskToPy = buildPythonRequest(task, task.getDataGroups());
            log.info("Built python request, taskId={}, requestId={}", task.getId(), taskToPy.getRequestId());
            JsonNode taskResponse = pythonSimulationService.submitAndWait(taskToPy);
            log.info("Python simulation completed, taskId={}, response={}", task.getId(), taskResponse);
            String directory = getFilePath(taskResponse, "directory");
            log.info("Python output directory resolved, taskId={}, directory={}", task.getId(), directory);
            List<String> storedFileNames = new ArrayList<>();
            String targetPath = dExperimentInfoService.getExperimentPath(task.getExperimentId());
            List<String> Filelists = new ArrayList<>();
            //获取仿真的数据文件路径
            try (Stream<Path> stream = Files.list(Paths.get(directory)))
            {
                stream.filter(Files::isRegularFile)
                        .forEach(path -> Filelists.add(path.getFileName().toString()));
            }
            for (String sourceName : Filelists){
                String sourceFile = directory + "/" +sourceName;
                log.info("Copy simulation file, taskId={}, sourceFile={}, targetPath={}", task.getId(), sourceFile, targetPath);
                storedFileNames.add(copyFile(sourceFile,targetPath,sourceName));
                deleteFile(sourceFile);
            }
            deleteFile(directory);
            log.info("Simulation files copied and source directory removed, taskId={}, copiedCount={}",
                    task.getId(), storedFileNames.size());
            //将数据插入数据库
            iDdataService.syncSimulationResultFiles(
                    task.getExperimentId(),
                    storedFileNames,
                    Filelists,
                    resolveSampleFrequency(task.getDataGroups()),
                    task.getCreateBy(),
                    task.getDataCategorySummary());
            log.info("Simulation result files synced to database, taskId={}", task.getId());

            task.setStatus(TaskStatusEnum.SUCCESS.toString());
            task.setUpdateTime(new Date());
            taskMapper.update(task);
            log.info("Task marked as SUCCESS, taskId={}", task.getId());
            notifyTaskSummaryIfCompleted(task.getId());
            channel.basicAck(deliveryTag, false);
            log.info("Task processing completed and acknowledged, taskId={}", task.getId());
        } catch (Exception exception) {
            log.error("Simulation task processing failed, taskId={}", task == null ? null : task.getId(), exception);
            int retryCount = getRetryCount(message);
            log.warn("Simulation task retry count evaluated, taskId={}, retryCount={}", task == null ? null : task.getId(), retryCount);
            if (retryCount < 3) {
                log.info("Retrying simulation task later, taskId={}", task == null ? null : task.getId());
                channel.basicNack(deliveryTag, false, false);
            } else {
                task.setStatus(TaskStatusEnum.FAILED.toString());
                notifyTaskSummaryIfCompleted(task.getId());
                taskMapper.update(task);
                taskDataGroupMapper.deleteByTaskId(task.getId());
                rabbitTemplate.send("retry_exchange", "final_routing", message);
                channel.basicAck(deliveryTag, false);
                log.error("Simulation task reached max retry count and moved to final retry queue, taskId={}", task.getId());
            }
        }
    }

    private TaskToPy buildPythonRequest(Task task, List<TaskDataGroup> taskDataGroups) {
        TaskToPy taskToPy = new TaskToPy();
        taskToPy.setRequestId(resolveRequestId(task));
        taskToPy.setBasic(buildBasicConfig(task));
        taskToPy.setDatasets(buildDatasetConfigs(taskDataGroups));
        return taskToPy;
    }

    private TaskToPy.BasicConfig buildBasicConfig(Task task) {
        TaskToPy.BasicConfig basicConfig = new TaskToPy.BasicConfig();
        basicConfig.setMotionModel(resolveHostTrajectoryType(task.getMotionModel()));
        basicConfig.setStartCoords(requireCoordinate(task.getStartCoordinate(), "start coordinate"));
        basicConfig.setEndCoords(requireCoordinate(task.getEndCoordinate(), "end coordinate"));
        return basicConfig;
    }

    private Map<String, TaskToPy.DatasetConfig> buildDatasetConfigs(List<TaskDataGroup> taskDataGroups) {
        List<TaskDataGroup> groups = requireTaskDataGroups(taskDataGroups);
        List<TaskDataGroup> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator
                .comparing(TaskListener::resolveSortNoOrderValue)
                .thenComparing(TaskDataGroup::getId, Comparator.nullsLast(Long::compareTo)));
        Map<String, TaskToPy.DatasetConfig> datasets = new LinkedHashMap<>();
        for (TaskDataGroup taskDataGroup : sortedGroups) {
            String datasetKey = resolveDatasetKey(taskDataGroup);
            TaskToPy.DatasetConfig previous = datasets.put(datasetKey, buildDatasetConfig(taskDataGroup, datasetKey));
            if (previous != null) {
                throw new ServiceException("duplicate dataset key: " + datasetKey);
            }
        }
        return datasets;
    }

    private static Integer resolveSortNoOrderValue(TaskDataGroup taskDataGroup) {
        if (taskDataGroup == null || taskDataGroup.getSortNo() == null) {
            return Integer.MAX_VALUE;
        }
        return taskDataGroup.getSortNo();
    }

    private TaskToPy.DatasetConfig buildDatasetConfig(TaskDataGroup taskDataGroup, String datasetKey) {
        TaskToPy.DatasetConfig datasetConfig = new TaskToPy.DatasetConfig();
        datasetConfig.setEnabled(Boolean.TRUE);
        datasetConfig.setFilename(buildDatasetFilename(taskDataGroup));
        datasetConfig.setFlightStartDatetime(requireDate(taskDataGroup.getStartTimeMs(), datasetKey + " start time"));
        datasetConfig.setFlightEndDatetime(requireDate(taskDataGroup.getEndTimeMs(), datasetKey + " end time"));
        datasetConfig.setSampleRateHz(requireValue(taskDataGroup.getFrequencyHz(), datasetKey + " sample rate"));
        applyTargetNum(datasetConfig, datasetKey, taskDataGroup.getTargetNum());
        return datasetConfig;
    }

    private List<TaskDataGroup> requireTaskDataGroups(List<TaskDataGroup> taskDataGroups) {
        if (taskDataGroups == null || taskDataGroups.isEmpty()) {
            throw new ServiceException("task data groups are required");
        }
        return taskDataGroups;
    }

    private String resolveRequestId(Task task) {
        if (StringUtils.isNotEmpty(task.getTaskCode())) {
            return task.getTaskCode().trim();
        }
        if (task.getId() != null) {
            return "task-" + task.getId();
        }
        throw new ServiceException("task code is required");
    }

    private String resolveDatasetKey(TaskDataGroup taskDataGroup) {
        if (StringUtils.isNotEmpty(taskDataGroup.getGroupName())) {
            return taskDataGroup.getGroupName().trim();
        }
        return requireText(taskDataGroup.getDataName(), "dataset key");
    }

    private String buildDatasetFilename(TaskDataGroup taskDataGroup) {
        String dataName = requireText(taskDataGroup.getDataName(), "dataset data name");
        String outputType = requireText(taskDataGroup.getOutputType(), "dataset output type");
        String normalizedOutputType = outputType.startsWith(".")
                ? outputType.substring(1).trim()
                : outputType.trim();
        String lowerCaseFileName = dataName.toLowerCase(Locale.ROOT);
        String lowerCaseSuffix = "." + normalizedOutputType.toLowerCase(Locale.ROOT);
        if (lowerCaseFileName.endsWith(lowerCaseSuffix)) {
            return dataName;
        }
        return dataName + "." + normalizedOutputType;
    }

    private void applyTargetNum(TaskToPy.DatasetConfig datasetConfig, String datasetKey, Integer targetNum) {
        if (targetNum == null) {
            return;
        }

        switch (datasetKey.toLowerCase(Locale.ROOT)) {
            case "radar_track":
                datasetConfig.setEnemyNum(targetNum);
                break;
            case "ads_b":
            case "adsb":
                datasetConfig.setFriendlyNum(targetNum);
                break;
            default:
                datasetConfig.setTargetNum(targetNum);
                break;
        }
    }

    private String requireText(String value, String fieldName) {
        if (StringUtils.isEmpty(value) || StringUtils.isEmpty(value.trim())) {
            throw new ServiceException(fieldName + " is required");
        }
        return value.trim();
    }

    private Coordinate requireCoordinate(Coordinate coordinate, String fieldName) {
        if (coordinate == null) {
            throw new ServiceException(fieldName + " is required");
        }
        return coordinate;
    }

    private Date requireDate(Long epochMillis, String fieldName) {
        if (epochMillis == null) {
            throw new ServiceException(fieldName + " is required");
        }
        return new Date(epochMillis);
    }

    private <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new ServiceException(fieldName + " is required");
        }
        return value;
    }

    private String resolveHostTrajectoryType(String motionModel) {
        if (StringUtils.isEmpty(motionModel)) {
            return "cubic";
        }

        switch (motionModel.trim()) {
            case "\u76f4\u7ebf\u6a21\u578b":
            case "LINEAR":
                return "straight";
            case "\u4e8c\u6b21\u66f2\u7ebf":
            case "QUADRATIC_CURVE":
                return "quadratic";
            case "\u4e09\u6b21\u66f2\u7ebf":
            case "CUBIC_CURVE":
                return "cubic";
            case "\u6298\u7ebf\u6a21\u578b":
            case "\u4e8c\u6298\u7ebf":
            case "POLYLINE_2":
                return "two_segment";
            case "\u4e09\u6298\u7ebf":
            case "POLYLINE_3":
                return "three_segment";
            case "\u76d8\u65cb\u6a21\u578b":
            case "\u673a\u52a8\u6a21\u578b":
            case "\u968f\u673a\u66f2\u7ebf":
            case "RANDOM_CURVE":
            default:
                return "cubic";
        }
    }

    private void notifyTaskSummaryIfCompleted(Long taskId) {
        if (taskId == null) {
            return;
        }

        log.info("Notify task summary check started, taskId={}", taskId);
        Task task = taskMapper.selectById(taskId);
        if (task == null || StringUtils.isEmpty(task.getCreateBy())) {
            log.info("Skip task summary notification because task or creator is missing, taskId={}", taskId);
            return;
        }

        SysUser user = sysUserService.selectUserByUserName(task.getCreateBy());
        if (user == null || user.getUserId() == null) {
            log.info("Skip task summary notification because user is missing, taskId={}, createBy={}", taskId, task.getCreateBy());
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "simulation_task_summary");
        payload.put("taskId", taskId);
        payload.put("taskName", task.getTaskName());
        payload.put("status", TaskStatusEnum.SUCCESS.toString());
        payload.put("message", String.format(
                "\u4efb\u52a1\"%s\"\u5df2\u5b8c\u6210",
                task.getTaskName()));

        try {
            webSocketServer.sendText(user.getUserId(), objectMapper.writeValueAsString(payload));
            log.info("Task summary notification sent, taskId={}, userId={}", taskId, user.getUserId());
        } catch (Exception ignored) {
            log.warn("Task summary notification failed, taskId={}, userId={}", taskId, user.getUserId());
        }
    }

    public int getRetryCount(Message message) {
        MessageProperties properties = message.getMessageProperties();
        Map<String, Object> headers = properties.getHeaders();
        Object xDeath = headers.get("x-death");
        if (xDeath instanceof List) {
            for (Object item : (List<?>) xDeath) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> deathInfo = (Map<?, ?>) item;
                Object queue = deathInfo.get("queue");
                if (!"simulation_task_queue".equals(String.valueOf(queue))) {
                    continue;
                }
                Object count = deathInfo.get("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
        }

        Object retryCount = headers.get("retry-count");
        if (retryCount instanceof Number) {
            return ((Number) retryCount).intValue();
        }
        return 0;
    }

    private String getFilePath(JsonNode taskResponse, String fileKey) {
        JsonNode filesNode = taskResponse.path("files");
        return filesNode.path(fileKey).asText(null);
    }

    private String copyFile(String sourcePath , String targetDir ,String sourceName) throws IOException {
        Path source = Paths.get(sourcePath);
        Path target = Paths.get(targetDir);
        try {
            if(!Files.exists(source)){
                throw new ServerException("源文件不存在");
            }
            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }
            String suffix = sourceName.substring(sourceName.lastIndexOf("."));
            sourceName = sourceName.substring(0,sourceName.lastIndexOf("."));
            Path targetFile = target.resolve(sourceName + suffix);
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile.getFileName().toString();
        }catch(Exception e) {
            throw new ServerException("文件导入失败");
        }
    }

    private Integer resolveSampleFrequency(List<TaskDataGroup> taskDataGroups) {
        if (taskDataGroups == null || taskDataGroups.isEmpty()) {
            return null;
        }

        for (TaskDataGroup taskDataGroup : taskDataGroups) {
            if (taskDataGroup != null && taskDataGroup.getFrequencyHz() != null) {
                return taskDataGroup.getFrequencyHz().intValue();
            }
        }
        return null;
    }

    private void deleteFile(String path) throws IOException {
        Path path1 = Paths.get(path);
        try {
            Files.deleteIfExists(path1);
        }catch (Exception e){
            throw new ServerException("文件删除失败");
        }
    }
}
