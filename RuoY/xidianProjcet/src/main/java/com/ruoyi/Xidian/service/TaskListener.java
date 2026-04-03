package com.ruoyi.Xidian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ruoyi.Xidian.config.SimulationPythonProperties;
import com.ruoyi.Xidian.domain.Attitude;
import com.ruoyi.Xidian.domain.Coordinate;
import com.ruoyi.Xidian.domain.RandomSeeds;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.Vector3;
import com.ruoyi.Xidian.domain.DTO.TaskToPy;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.WebSocketServer;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.ServerException;
import java.util.*;
import java.util.stream.Stream;

@Component
public class TaskListener {
    private static final String TASK_SUMMARY_NOTIFY_KEY_PREFIX = "simulation_task_summary_notified:";

    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskDataGroupMapper taskDataGroupMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private PythonSimulationService pythonSimulationService;
    @Autowired
    private SimulationPythonProperties simulationPythonProperties;
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
    public void handleTask(TaskDataGroup taskDataGroup, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        TaskDataGroup currentTaskDataGroup = taskDataGroupMapper.selectById(taskDataGroup.getId());
        if (currentTaskDataGroup == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (TaskStatusEnum.SUCCESS.toString().equals(currentTaskDataGroup.getStatus())) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            Task task = taskMapper.selectById(taskDataGroup.getTaskId());
            if (task == null) {
                throw new ServiceException("Parent task not found, taskId=" + taskDataGroup.getTaskId());
            }

            taskDataGroup.setStatus(TaskStatusEnum.RUNNING.toString());
            taskDataGroupMapper.update(taskDataGroup);

            task.setStatus(TaskStatusEnum.RUNNING.toString());
            task.setUpdateTime(new Date());
            taskMapper.update(task);

            TaskToPy taskToPy = buildPythonRequest(task, taskDataGroup);
            JsonNode taskResponse = pythonSimulationService.submitAndWait(taskToPy);
            String directory = getFilePath(taskResponse, "directory");
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
                storedFileNames.add(copyFile(sourceFile,targetPath,sourceName));
                deleteFile(sourceFile);
            }
            deleteFile(directory);
            //将数据插入数据库
            iDdataService.syncSimulationResultFiles(
                    task.getExperimentId(),
                    storedFileNames,
                    Filelists,
                    resolveSampleFrequency(taskDataGroup),
                    task.getCreateBy(),
                    task.getDataCategorySummary());

            taskDataGroup.setStatus(TaskStatusEnum.SUCCESS.toString());
            taskDataGroupMapper.update(taskDataGroup);

            Integer remainingSubTaskCount = redisCache.getCacheObject(taskDataGroup.getTaskId().toString());
            if (remainingSubTaskCount != null && remainingSubTaskCount > 1) {
                redisCache.setCacheObject(taskDataGroup.getTaskId().toString(), remainingSubTaskCount - 1);
            } else if (remainingSubTaskCount != null) {
                task.setStatus(TaskStatusEnum.SUCCESS.toString());
                task.setUpdateTime(new Date());
                taskMapper.update(task);
                redisCache.deleteObject(task.getId().toString());
            }
            notifyTaskSummaryIfCompleted(task.getId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            int retryCount = getRetryCount(message);
            if (retryCount < 3) {
                channel.basicNack(deliveryTag, false, false);
            } else {
                taskDataGroup.setStatus(TaskStatusEnum.FAILED.toString());
                taskDataGroupMapper.update(taskDataGroup);
                markParentTaskFailed(taskDataGroup.getTaskId());
                notifyTaskSummaryIfCompleted(taskDataGroup.getTaskId());
                rabbitTemplate.send("retry_exchange", "final_routing", message);
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    private TaskToPy buildPythonRequest(Task task, TaskDataGroup taskDataGroup) {
        TaskToPy taskToPy = new TaskToPy();
        taskToPy.setRequest_id(resolveRequestId(task, taskDataGroup));
        taskToPy.setStart_coords(requireCoordinate(task.getStartCoordinate(), "start coordinate"));
        taskToPy.setEnd_coords(requireCoordinate(task.getEndCoordinate(), "end coordinate"));
        taskToPy.setStart_velocity(resolveStartVelocity(taskDataGroup));
        taskToPy.setStart_attitude(resolveStartAttitude(taskDataGroup));
        taskToPy.setFlight_start_datetime(requireDate(taskDataGroup.getStartTimeMs(), "subtask start time"));
        taskToPy.setFlight_end_datetime(requireDate(taskDataGroup.getEndTimeMs(), "subtask end time"));
        taskToPy.setSample_rate_hz(requireValue(taskDataGroup.getFrequencyHz(), "sample rate"));
        taskToPy.setNum(requireValue(taskDataGroup.getTargetNum(), "target count"));
        taskToPy.setHost_trajectory_type(resolveHostTrajectoryType(task.getMotionModel()));
        taskToPy.setOutput_directory(resolveOutputDirectory(task, taskDataGroup));
        taskToPy.setRandomSeeds(resolveRandomSeeds(taskDataGroup));
        return taskToPy;
    }

    private String resolveRequestId(Task task, TaskDataGroup taskDataGroup) {
        if (StringUtils.isNotEmpty(taskDataGroup.getRequestId())) {
            return taskDataGroup.getRequestId().trim();
        }
        return buildPythonRequestId(task, taskDataGroup);
    }

    private String buildPythonRequestId(Task task, TaskDataGroup taskDataGroup) {
        String baseRequestId = StringUtils.isNotEmpty(task.getTaskCode())
                ? task.getTaskCode()
                : "task-" + task.getId();
        return baseRequestId + "-group-" + taskDataGroup.getId();
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

    private Vector3 createMockStartVelocity() {
        return new Vector3(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Vector3 resolveStartVelocity(TaskDataGroup taskDataGroup) {
        Vector3 fallback = createMockStartVelocity();
        Vector3 configured = taskDataGroup.getStartVelocity();
        if (configured == null) {
            return fallback;
        }

        Vector3 resolved = new Vector3();
        resolved.setVx(defaultBigDecimal(configured.getVx(), fallback.getVx()));
        resolved.setVy(defaultBigDecimal(configured.getVy(), fallback.getVy()));
        resolved.setVz(defaultBigDecimal(configured.getVz(), fallback.getVz()));
        return resolved;
    }

    private Attitude createMockStartAttitude() {
        Attitude attitude = new Attitude();
        attitude.setRoll(BigDecimal.ZERO);
        attitude.setPitch(BigDecimal.ZERO);
        attitude.setYaw(BigDecimal.ZERO);
        return attitude;
    }

    private Attitude resolveStartAttitude(TaskDataGroup taskDataGroup) {
        Attitude fallback = createMockStartAttitude();
        Attitude configured = taskDataGroup.getStartAttitude();
        if (configured == null) {
            return fallback;
        }

        Attitude resolved = new Attitude();
        resolved.setRoll(defaultBigDecimal(configured.getRoll(), fallback.getRoll()));
        resolved.setPitch(defaultBigDecimal(configured.getPitch(), fallback.getPitch()));
        resolved.setYaw(defaultBigDecimal(configured.getYaw(), fallback.getYaw()));
        return resolved;
    }

    private RandomSeeds createDefaultRandomSeeds() {
        return new RandomSeeds(42, 43, 44, 45);
    }

    private RandomSeeds resolveRandomSeeds(TaskDataGroup taskDataGroup) {
        RandomSeeds fallback = createDefaultRandomSeeds();
        RandomSeeds configured = taskDataGroup.getRandomSeeds();
        if (configured == null) {
            return fallback;
        }

        RandomSeeds resolved = new RandomSeeds();
        resolved.setHost(defaultInteger(configured.getHost(), fallback.getHost()));
        resolved.setEnemy(defaultInteger(configured.getEnemy(), fallback.getEnemy()));
        resolved.setWingman(defaultInteger(configured.getWingman(), fallback.getWingman()));
        resolved.setAttitude(defaultInteger(configured.getAttitude(), fallback.getAttitude()));
        return resolved;
    }

    private BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private Integer defaultInteger(Integer value, Integer fallback) {
        return value == null ? fallback : value;
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

    private String resolveOutputDirectory(Task task, TaskDataGroup taskDataGroup) {
        if (StringUtils.isNotEmpty(taskDataGroup.getOutputDirectory())) {
            String configuredDirectory = normalizeDirectory(taskDataGroup.getOutputDirectory());
            if (StringUtils.isNotEmpty(configuredDirectory)) {
                return configuredDirectory;
            }
        }

        String relativePath = StringUtils.isNotEmpty(task.getPath())
                ? task.getPath()
                : resolveExperimentRelativePath(task);
        String normalizedRelativePath = normalizeRelativeOutputPath(relativePath);
        if (StringUtils.isEmpty(normalizedRelativePath)) {
            return simulationPythonProperties.getDefaultOutputDirectory();
        }

        String outputBaseDir = normalizeDirectory(simulationPythonProperties.getOutputBaseDir());
        if (StringUtils.isEmpty(outputBaseDir)) {
            return normalizedRelativePath;
        }
        return outputBaseDir + "/" + normalizedRelativePath;
    }

    private String resolveExperimentRelativePath(Task task) {
        if (StringUtils.isEmpty(task.getExperimentId())) {
            return null;
        }
        return dExperimentInfoService.getExperimentRelativePath(task.getExperimentId());
    }

    private String normalizeRelativeOutputPath(String path) {
        String normalized = normalizeDirectory(path);
        if (StringUtils.isEmpty(normalized)) {
            return normalized;
        }

        String localDataRoot = normalizeDirectory(simulationPythonProperties.getLocalDataRoot());
        if (StringUtils.isNotEmpty(localDataRoot)) {
            if (normalized.equals(localDataRoot)) {
                return "";
            }
            if (normalized.startsWith(localDataRoot + "/")) {
                normalized = normalized.substring(localDataRoot.length() + 1);
            }
        }

        if (normalized.equals("./data")) {
            return "";
        }
        if (normalized.startsWith("./data/")) {
            normalized = normalized.substring("./data/".length());
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeDirectory(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }

        String normalized = value.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void markParentTaskFailed(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(TaskStatusEnum.FAILED.toString());
        task.setUpdateTime(new Date());
        taskMapper.update(task);
        redisCache.deleteObject(taskId.toString());
    }

    private void notifyTaskSummaryIfCompleted(Long taskId) {
        if (taskId == null) {
            return;
        }

        String notifyKey = TASK_SUMMARY_NOTIFY_KEY_PREFIX + taskId;
        if (Boolean.TRUE.equals(redisCache.hasKey(notifyKey))) {
            return;
        }

        List<TaskDataGroup> taskDataGroups = taskDataGroupMapper.selectByTaskId(taskId);
        if (taskDataGroups == null || taskDataGroups.isEmpty()) {
            return;
        }

        long successCount = taskDataGroups.stream()
                .filter(group -> TaskStatusEnum.SUCCESS.toString().equals(group.getStatus()))
                .count();
        long failedCount = taskDataGroups.stream()
                .filter(group -> TaskStatusEnum.FAILED.toString().equals(group.getStatus()))
                .count();
        if (successCount + failedCount != taskDataGroups.size()) {
            return;
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null || StringUtils.isEmpty(task.getCreateBy())) {
            redisCache.setCacheObject(notifyKey, Boolean.TRUE);
            return;
        }

        SysUser user = sysUserService.selectUserByUserName(task.getCreateBy());
        if (user == null || user.getUserId() == null) {
            redisCache.setCacheObject(notifyKey, Boolean.TRUE);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "simulation_task_summary");
        payload.put("taskId", taskId);
        payload.put("taskName", task.getTaskName());
        payload.put("totalCount", taskDataGroups.size());
        payload.put("successCount", successCount);
        payload.put("failedCount", failedCount);
        payload.put("status", failedCount > 0 ? TaskStatusEnum.FAILED.toString() : TaskStatusEnum.SUCCESS.toString());
        payload.put("message", String.format(
                "\u4efb\u52a1\"%s\"\u5df2\u5b8c\u6210\uff0c\u6210\u529f %d \u6761\uff0c\u5931\u8d25 %d \u6761",
                task.getTaskName(),
                successCount,
                failedCount));

        try {
            webSocketServer.sendText(user.getUserId(), objectMapper.writeValueAsString(payload));
            redisCache.setCacheObject(notifyKey, Boolean.TRUE);
        } catch (Exception ignored) {
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
            Path targetFile = target.resolve(sourceName + "_" + UUID.randomUUID() + suffix);
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile.getFileName().toString();
        }catch(Exception e) {
            throw new ServerException("文件导入失败");
        }
    }

    private Integer resolveSampleFrequency(TaskDataGroup taskDataGroup) {
        if (taskDataGroup == null || taskDataGroup.getFrequencyHz() == null) {
            return null;
        }
        return taskDataGroup.getFrequencyHz().intValue();
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
