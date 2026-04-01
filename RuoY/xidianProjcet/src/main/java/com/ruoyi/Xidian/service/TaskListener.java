package com.ruoyi.Xidian.service;

import com.rabbitmq.client.Channel;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.TaskDataMetric;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class TaskListener {
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskDataGroupMapper taskDataGroupMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisCache redisCache;

    @RabbitListener(queues = "simulation_task_queue")
    public void handleTask(Task task, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        // 1. 幂等检查：如果任务已经成功了，直接签收掉
        Task currentTask = taskMapper.selectById(task.getId());
        if (TaskStatusEnum.SUCCESS.toString().equals(currentTask.getStatus())) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            //生成Json格式的数据发给Python
            //1 把task taskgroup 等封装为taskDto ,将taskDTO转为JSON
            //调用仿真接口，获取仿真结果，模拟运算时间
            Thread.sleep(5000);

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 5. 运算失败：
            // 第三个参数 false 表示不重回原队列，配合“死信队列”使用
            int retryCount = getRetryCount(message);
            if(retryCount < 3){
                // 重试次数小于3次，重新入队
                message.getMessageProperties().setHeader("retry-count", retryCount + 1);
                channel.basicNack(deliveryTag, false, false);
            }else{
                // 重试次数大于等于3次，标记为失败
                currentTask.setStatus(TaskStatusEnum.FAILED.toString());
                taskMapper.update(currentTask);
                rabbitTemplate.send("retry_exchange", "final_routing", message);
                channel.basicNack(deliveryTag, false, true);
                throw new ServiceException("任务执行失败，请稍后再试");
            }
        }
    }

    public int getRetryCount(Message message) {
        MessageProperties properties = message.getMessageProperties();
        Map<String, Object> headers = properties.getHeaders();
        Object count = headers.get("retry-count");
        if (count instanceof Integer) {
            return (Integer) count;
        }
        return 0;
    }
}
