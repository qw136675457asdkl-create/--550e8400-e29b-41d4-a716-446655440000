package com.ruoyi.Xidian.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.Xidian.config.SimulationTaskStreamProperties;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SimulationTaskStreamQueue
{
    private static final Logger log = LoggerFactory.getLogger(SimulationTaskStreamQueue.class);
    private static final String FIELD_TASK = "task";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_NEXT_RETRY_AT = "nextRetryAt";
    private static final String FIELD_LAST_ERROR = "lastError";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = buildReleaseLockScript();

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SimulationTaskStreamProperties properties;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public SimulationTaskStreamQueue(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            SimulationTaskStreamProperties properties
    )
    {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void initialize()
    {
        if (initialized.get())
        {
            return;
        }

        synchronized (this)
        {
            if (initialized.get())
            {
                return;
            }

            ensureStream(properties.getTaskStreamKey());
            ensureTaskConsumerGroup();
            ensureStream(properties.getRetryStreamKey());
            ensureStream(properties.getFinalStreamKey());
            initialized.set(true);
            log.info("Simulation task redis streams initialized, taskStream={}, retryStream={}, finalStream={}, consumerGroup={}",
                    properties.getTaskStreamKey(),
                    properties.getRetryStreamKey(),
                    properties.getFinalStreamKey(),
                    properties.getConsumerGroup());
        }
    }

    public String getTaskStreamKey()
    {
        return properties.getTaskStreamKey();
    }

    public RecordId enqueue(Task task)
    {
        return addTaskRecord(task, 0, null);
    }

    public RecordId addTaskRecord(Task task, int retryCount, String lastError)
    {
        initialize();
        return addRecord(properties.getTaskStreamKey(), buildFields(task, retryCount, null, lastError));
    }

    public RecordId scheduleRetry(Task task, int retryCount, String lastError)
    {
        initialize();
        long nextRetryAt = System.currentTimeMillis() + properties.getRetryDelayMillis();
        return addRecord(properties.getRetryStreamKey(), buildFields(task, retryCount, nextRetryAt, lastError));
    }

    public RecordId moveToFinalQueue(Task task, int retryCount, String lastError)
    {
        initialize();
        return addRecord(properties.getFinalStreamKey(), buildFields(task, retryCount, null, lastError));
    }

    public List<MapRecord<String, Object, Object>> readPendingTaskRecords(String consumerName, int count)
    {
        initialize();
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(properties.getConsumerGroup(), consumerName),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(properties.getTaskStreamKey(), ReadOffset.from("0")));
        return records == null ? Collections.emptyList() : records;
    }

    public List<MapRecord<String, Object, Object>> readNewTaskRecords(String consumerName, int count)
    {
        initialize();
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(properties.getConsumerGroup(), consumerName),
                StreamReadOptions.empty()
                        .count(count)
                        .block(Duration.ofMillis(properties.getReadBlockTimeoutMillis())),
                StreamOffset.create(properties.getTaskStreamKey(), ReadOffset.lastConsumed()));
        return records == null ? Collections.emptyList() : records;
    }

    public List<MapRecord<String, Object, Object>> readRetryRecords(int count)
    {
        initialize();
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().range(
                properties.getRetryStreamKey(),
                Range.unbounded());
        if (records == null || records.isEmpty())
        {
            return Collections.emptyList();
        }
        if (records.size() <= count)
        {
            return records;
        }
        return records.subList(0, count);
    }

    public void moveRetryRecordToTaskStream(MapRecord<String, Object, Object> retryRecord)
    {
        initialize();
        Map<String, String> mainFields = new LinkedHashMap<>();
        mainFields.put(FIELD_TASK, getRequiredField(retryRecord, FIELD_TASK));
        mainFields.put(FIELD_RETRY_COUNT, String.valueOf(readRetryCount(retryRecord)));
        mainFields.put(FIELD_CREATED_AT, String.valueOf(System.currentTimeMillis()));
        String lastError = readLastError(retryRecord);
        if (StringUtils.isNotEmpty(lastError))
        {
            mainFields.put(FIELD_LAST_ERROR, lastError);
        }

        List<Object> transactionResult = stringRedisTemplate.execute(new SessionCallback<List<Object>>()
        {
            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public List<Object> execute(RedisOperations operations) throws DataAccessException
            {
                operations.multi();
                operations.opsForStream().add(StreamRecords.mapBacked(mainFields).withStreamKey(properties.getTaskStreamKey()));
                operations.opsForStream().delete(properties.getRetryStreamKey(), retryRecord.getId());
                return operations.exec();
            }
        });

        if (transactionResult == null || transactionResult.isEmpty())
        {
            throw new IllegalStateException("failed to move retry record back to task stream");
        }
    }

    public void acknowledgeTaskRecord(RecordId recordId)
    {
        initialize();
        stringRedisTemplate.opsForStream().acknowledge(properties.getTaskStreamKey(), properties.getConsumerGroup(), recordId);
    }

    public void deleteTaskRecord(RecordId recordId)
    {
        initialize();
        stringRedisTemplate.opsForStream().delete(properties.getTaskStreamKey(), recordId);
    }

    public void deleteRetryRecord(RecordId recordId)
    {
        initialize();
        stringRedisTemplate.opsForStream().delete(properties.getRetryStreamKey(), recordId);
    }

    public Task readTask(MapRecord<String, Object, Object> record)
    {
        try
        {
            return objectMapper.readValue(getRequiredField(record, FIELD_TASK), Task.class);
        }
        catch (JsonProcessingException exception)
        {
            throw new IllegalStateException("failed to deserialize task stream message", exception);
        }
    }

    public int readRetryCount(MapRecord<String, Object, Object> record)
    {
        String retryCount = getField(record, FIELD_RETRY_COUNT);
        if (StringUtils.isEmpty(retryCount))
        {
            return 0;
        }
        return Integer.parseInt(retryCount);
    }

    public long readNextRetryAt(MapRecord<String, Object, Object> record)
    {
        String nextRetryAt = getField(record, FIELD_NEXT_RETRY_AT);
        if (StringUtils.isEmpty(nextRetryAt))
        {
            return 0L;
        }
        return Long.parseLong(nextRetryAt);
    }

    public String readLastError(MapRecord<String, Object, Object> record)
    {
        return getField(record, FIELD_LAST_ERROR);
    }

    public boolean tryAcquireRetryDispatchLock(String owner)
    {
        initialize();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                properties.getRetryDispatchLockKey(),
                owner,
                Duration.ofMillis(properties.getRetryDispatchLockMillis()));
        return Boolean.TRUE.equals(locked);
    }

    public void releaseRetryDispatchLock(String owner)
    {
        initialize();
        stringRedisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(properties.getRetryDispatchLockKey()),
                owner);
    }

    private void ensureTaskConsumerGroup()
    {
        try
        {
            stringRedisTemplate.opsForStream().createGroup(properties.getTaskStreamKey(), ReadOffset.latest(), properties.getConsumerGroup());
        }
        catch (Exception exception)
        {
            if (!isBusyGroupException(exception))
            {
                throw new IllegalStateException("failed to create redis stream consumer group", exception);
            }
        }
    }

    private boolean isBusyGroupException(Exception exception)
    {
        return exception.getMessage() != null && exception.getMessage().contains("BUSYGROUP");
    }

    private void ensureStream(String streamKey)
    {
        RecordId initRecordId = stringRedisTemplate.opsForStream()
                .add(StreamRecords.mapBacked(Collections.singletonMap("_init", "1")).withStreamKey(streamKey));
        if (initRecordId != null)
        {
            stringRedisTemplate.opsForStream().delete(streamKey, initRecordId);
        }
    }

    private RecordId addRecord(String streamKey, Map<String, String> fields)
    {
        RecordId recordId = stringRedisTemplate.opsForStream()
                .add(StreamRecords.mapBacked(fields).withStreamKey(streamKey));
        if (recordId == null)
        {
            throw new IllegalStateException("failed to append record to stream: " + streamKey);
        }
        return recordId;
    }

    private Map<String, String> buildFields(Task task, int retryCount, Long nextRetryAt, String lastError)
    {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_TASK, writeTask(task));
        fields.put(FIELD_RETRY_COUNT, String.valueOf(retryCount));
        fields.put(FIELD_CREATED_AT, String.valueOf(System.currentTimeMillis()));
        if (nextRetryAt != null)
        {
            fields.put(FIELD_NEXT_RETRY_AT, String.valueOf(nextRetryAt));
        }
        if (StringUtils.isNotEmpty(lastError))
        {
            fields.put(FIELD_LAST_ERROR, abbreviate(lastError, 1000));
        }
        return fields;
    }

    private String writeTask(Task task)
    {
        try
        {
            return objectMapper.writeValueAsString(task);
        }
        catch (JsonProcessingException exception)
        {
            throw new IllegalStateException("failed to serialize task stream message", exception);
        }
    }

    private String getRequiredField(MapRecord<String, Object, Object> record, String fieldName)
    {
        String value = getField(record, fieldName);
        if (StringUtils.isEmpty(value))
        {
            throw new IllegalStateException("missing stream field: " + fieldName);
        }
        return value;
    }

    private String getField(MapRecord<String, Object, Object> record, String fieldName)
    {
        Object value = record.getValue().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private String abbreviate(String text, int maxLength)
    {
        if (text == null || text.length() <= maxLength)
        {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static DefaultRedisScript<Long> buildReleaseLockScript()
    {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0");
        return script;
    }
}
