package com.ruoyi.quartz.task;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.HttpUtils;
import com.ruoyi.system.mapper.SysOperLogMapper;
import com.ruoyi.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志推送定时任务
 */
@Component("auditLogPushTask")
public class AuditLogPushTask
{
    private static final Logger log = LoggerFactory.getLogger(AuditLogPushTask.class);

    private static final double ALERT_RATIO = 0.8D;

    private static final String APPLICATION_JSON = "application/json";

    @Value("${ruoyi.targetUrl:}")
    private String targetUrl;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysOperLogMapper operLogMapper;

    /**
     * 推送审计日志空间告警信息
     * Quartz 调用: auditLogPushTask.pushAlertLog()
     */
    public void pushAlertLog()
    {
        log.info("【审计日志推送】=== 开始执行推送任务 ===");
        try
        {
            if (StringUtils.isBlank(targetUrl))
            {
                log.warn("【审计日志推送】未配置 ruoyi.targetUrl，跳过推送");
                return;
            }

            double thresholdMb = resolveThresholdMb();
            if (thresholdMb <= 0)
            {
                log.warn("【审计日志推送】告警阈值无效，thresholdMb={}", thresholdMb);
                return;
            }

            Double currentSizeMb = operLogMapper.getAuditLogTableSize();
            if (currentSizeMb == null)
            {
                currentSizeMb = 0D;
            }

            double alertThresholdMb = thresholdMb * ALERT_RATIO;
            if (currentSizeMb < alertThresholdMb)
            {
                log.info("【审计日志推送】当前大小: {} MB，未达到推送阈值: {} MB，跳过推送", currentSizeMb, alertThresholdMb);
                return;
            }

            String message = String.format(
                    "【安全预警】系统审计日志存储空间已达临界值！当前大小: %.2f MB，设定的阈值: %.2f MB。系统已向外部目标地址推送告警日志。",
                    currentSizeMb,
                    thresholdMb
            );

            Map<String, Object> payload = buildPayload(currentSizeMb, thresholdMb, alertThresholdMb, message);
            String response = HttpUtils.sendPost(targetUrl, JSON.toJSONString(payload), APPLICATION_JSON);
            log.info("【审计日志推送】推送完成，targetUrl={}, response={}", targetUrl, response);
        }
        catch (Exception e)
        {
            log.error("【审计日志推送】执行失败", e);
        }
        log.info("【审计日志推送】=== 推送任务结束 ===");
    }

    private double resolveThresholdMb()
    {
        String thresholdStr = configService.selectConfigByKey("audit.log.max.size");
        double thresholdMb = RuoYiConfig.getThresholdMb();
        if (StringUtils.isBlank(thresholdStr))
        {
            return thresholdMb;
        }

        try
        {
            return Double.parseDouble(thresholdStr);
        }
        catch (NumberFormatException e)
        {
            log.warn("【审计日志推送】sys_config 中 audit.log.max.size 配置非法，使用 yml 默认值: {}", thresholdMb);
            return thresholdMb;
        }
    }

    private Map<String, Object> buildPayload(double currentSizeMb, double thresholdMb, double alertThresholdMb, String message)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "AUDIT_LOG_SPACE_ALERT");
        payload.put("currentSizeMb", currentSizeMb);
        payload.put("thresholdMb", thresholdMb);
        payload.put("alertThresholdMb", alertThresholdMb);
        payload.put("usageRate", currentSizeMb / thresholdMb);
        payload.put("profile", RuoYiConfig.getProfile());
        payload.put("message", message);
        payload.put("pushTime", new Date());
        return payload;
    }
}
