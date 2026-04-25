package com.ruoyi.Xidian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation.task.stream")
public class SimulationTaskStreamProperties
{
    private String taskStreamKey = "simulation_task_queue";

    private String retryStreamKey = "simulation_task_retry_queue";

    private String finalStreamKey = "final_queue";

    private String consumerGroup = "simulation_task_group";

    private int consumerCount = 4;

    private int maxRetryCount = 3;

    private long retryDelayMillis = 10000;

    private long readBlockTimeoutMillis = 2000;

    private long workerErrorBackoffMillis = 2000;

    private long retryDispatchIntervalMillis = 1000;

    private int retryDispatchBatchSize = 20;

    private String retryDispatchLockKey = "simulation_task_retry_dispatch_lock";

    private long retryDispatchLockMillis = 5000;

    public String getTaskStreamKey()
    {
        return taskStreamKey;
    }

    public void setTaskStreamKey(String taskStreamKey)
    {
        this.taskStreamKey = taskStreamKey;
    }

    public String getRetryStreamKey()
    {
        return retryStreamKey;
    }

    public void setRetryStreamKey(String retryStreamKey)
    {
        this.retryStreamKey = retryStreamKey;
    }

    public String getFinalStreamKey()
    {
        return finalStreamKey;
    }

    public void setFinalStreamKey(String finalStreamKey)
    {
        this.finalStreamKey = finalStreamKey;
    }

    public String getConsumerGroup()
    {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup)
    {
        this.consumerGroup = consumerGroup;
    }

    public int getConsumerCount()
    {
        return consumerCount;
    }

    public void setConsumerCount(int consumerCount)
    {
        this.consumerCount = consumerCount;
    }

    public int getMaxRetryCount()
    {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount)
    {
        this.maxRetryCount = maxRetryCount;
    }

    public long getRetryDelayMillis()
    {
        return retryDelayMillis;
    }

    public void setRetryDelayMillis(long retryDelayMillis)
    {
        this.retryDelayMillis = retryDelayMillis;
    }

    public long getReadBlockTimeoutMillis()
    {
        return readBlockTimeoutMillis;
    }

    public void setReadBlockTimeoutMillis(long readBlockTimeoutMillis)
    {
        this.readBlockTimeoutMillis = readBlockTimeoutMillis;
    }

    public long getWorkerErrorBackoffMillis()
    {
        return workerErrorBackoffMillis;
    }

    public void setWorkerErrorBackoffMillis(long workerErrorBackoffMillis)
    {
        this.workerErrorBackoffMillis = workerErrorBackoffMillis;
    }

    public long getRetryDispatchIntervalMillis()
    {
        return retryDispatchIntervalMillis;
    }

    public void setRetryDispatchIntervalMillis(long retryDispatchIntervalMillis)
    {
        this.retryDispatchIntervalMillis = retryDispatchIntervalMillis;
    }

    public int getRetryDispatchBatchSize()
    {
        return retryDispatchBatchSize;
    }

    public void setRetryDispatchBatchSize(int retryDispatchBatchSize)
    {
        this.retryDispatchBatchSize = retryDispatchBatchSize;
    }

    public String getRetryDispatchLockKey()
    {
        return retryDispatchLockKey;
    }

    public void setRetryDispatchLockKey(String retryDispatchLockKey)
    {
        this.retryDispatchLockKey = retryDispatchLockKey;
    }

    public long getRetryDispatchLockMillis()
    {
        return retryDispatchLockMillis;
    }

    public void setRetryDispatchLockMillis(long retryDispatchLockMillis)
    {
        this.retryDispatchLockMillis = retryDispatchLockMillis;
    }
}
