package com.ruoyi.web.controller.system;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;

import static com.ruoyi.common.core.domain.AjaxResult.success;

/**
 * 系统时间接口（统一时间源）
 */
@RestController
@RequestMapping("/system/time")
public class SysTimeController
{
    /**
     * 获取服务器当前时间
     */
    @GetMapping("/now")
    public AjaxResult now()
    {
        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.systemDefault();
        Map<String, Object> data = new HashMap<>();
        data.put("epochMillis", now.toEpochMilli());
        data.put("zoneId", zoneId.getId());
        data.put("offsetSeconds", zoneId.getRules().getOffset(now).getTotalSeconds());
        return success(data);
    }
}
