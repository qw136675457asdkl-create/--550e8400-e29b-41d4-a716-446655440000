package com.ruoyi.framework.manager.factory;

import java.util.Collection;
import java.util.Date;
import java.util.TimerTask;
import javax.servlet.http.HttpServletRequest;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.LogUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.HttpUtils;
import com.ruoyi.common.utils.http.UserAgentUtils;
import com.ruoyi.common.utils.ip.AddressUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.audit.AuditLogTagSupport;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.service.ISysLogininforService;
import com.ruoyi.system.service.ISysOperLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步工厂
 *
 * @author ruoyi
 */
public class AsyncFactory
{
    private static final Logger sys_user_logger = LoggerFactory.getLogger("sys-user");

    /**
     * 记录登录信息
     */
    public static TimerTask recordLogininfor(final String username, final String status, final String message,
                                             final Object... args)
    {
        return buildLoginLogTask(username, status, message, false, args);
    }

    /**
     * 记录登录信息，并支持额外风控提级。
     */
    public static TimerTask recordLogininfor(final String username, final String status, final String message,
                                             final boolean concurrentLoginRisk)
    {
        return buildLoginLogTask(username, status, message, concurrentLoginRisk, new Object[0]);
    }

    private static TimerTask buildLoginLogTask(final String username, final String status, final String message,
                                               final boolean concurrentLoginRisk, final Object[] args)
    {
        final String userAgent = resolveUserAgent();
        final String ip = IpUtils.getIpAddr();
        final Integer retryCount = getLoginRetryCount(username);
        final Object[] logArgs = args == null ? new Object[0] : args;
        return new TimerTask()
        {
            @Override
            public void run()
            {
                try
                {
                    String address = AddressUtils.getRealAddressByIP(ip);
                    String browser = UserAgentUtils.getBrowser(userAgent);
                    String os = UserAgentUtils.getOperatingSystem(userAgent);

                    logLoginEvent(ip, address, username, status, message, logArgs);

                    SysOperLog operLog = buildLoginOperLog(username, status, message, ip, address, browser, os, retryCount);
                    SysLogininfor logininfor = buildLoginInfo(username, status, message, ip, address, browser, os, retryCount);

                    if (concurrentLoginRisk)
                    {
                        AuditLogTagSupport.tagConcurrentLogin(operLog);
                        AuditLogTagSupport.tagConcurrentLogin(logininfor);
                    }

                    insertOperLog(username, status, message, operLog);
                    insertLoginInfo(username, status, message, logininfor);
                }
                catch (Exception e)
                {
                    sys_user_logger.error("build login audit logs failed: username={}, status={}, message={}",
                            username, status, message, e);
                }
            }
        };
    }

    /**
     * 操作日志记录
     */
    public static TimerTask recordOper(final SysOperLog operLog)
    {
        return new TimerTask()
        {
            @Override
            public void run()
            {
                operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
                SpringUtils.getBean(ISysOperLogService.class).insertOperlog(operLog);
                try
                {
                    String targetUrl = "url";
                    String jsonLog = JSON.toJSONString(operLog);
                    HttpUtils.sendPost(targetUrl, jsonLog);
                }
                catch (Exception e)
                {
                    sys_user_logger.error("push oper log to external system failed: {}", e.getMessage(), e);
                }
            }
        };
    }

    private static String resolveUserAgent()
    {
        try
        {
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? StringUtils.EMPTY : StringUtils.defaultString(request.getHeader("User-Agent"));
        }
        catch (Exception e)
        {
            return StringUtils.EMPTY;
        }
    }

    private static void logLoginEvent(String ip, String address, String username, String status, String message, Object[] args)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(LogUtils.getBlock(ip));
        builder.append(address);
        builder.append(LogUtils.getBlock(username));
        builder.append(LogUtils.getBlock(status));
        builder.append(LogUtils.getBlock(message));
        sys_user_logger.info(builder.toString(), args);
    }

    private static SysOperLog buildLoginOperLog(String username, String status, String message, String ip, String address,
                                                String browser, String os, Integer retryCount)
    {
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle("\u7cfb\u7edf\u767b\u5f55");
        operLog.setBusinessType(BusinessType.OTHER.ordinal());
        operLog.setRequestMethod("POST");
        operLog.setOperName(username);
        operLog.setOperIp(ip);
        operLog.setOperLocation(address);
        operLog.setOperTime(new Date());
        operLog.setOperParam("Browser: " + browser + ", OS: " + os);
        operLog.setJsonResult(message);
        operLog.setOperatorType(OperatorType.MANAGE.ordinal());
        operLog.setMethod("com.ruoyi.framework.web.service.SysLoginService.login()");
        if (isLoginSuccessStatus(status))
        {
            operLog.setStatus(BusinessStatus.SUCCESS.ordinal());
        }
        else
        {
            operLog.setStatus(BusinessStatus.FAIL.ordinal());
            operLog.setErrorMsg(message);
        }
        AuditLogTagSupport.tagLoginOperLog(operLog, status, retryCount);
        return operLog;
    }

    private static SysLogininfor buildLoginInfo(String username, String status, String message, String ip, String address,
                                                String browser, String os, Integer retryCount)
    {
        SysLogininfor logininfor = new SysLogininfor();
        logininfor.setUserName(username);
        logininfor.setIpaddr(ip);
        logininfor.setLoginLocation(address);
        logininfor.setBrowser(browser);
        logininfor.setOs(os);
        logininfor.setMsg(message);
        logininfor.setStatus(isLoginSuccessStatus(status) ? Constants.SUCCESS : Constants.FAIL);
        AuditLogTagSupport.tagLoginInfo(logininfor, retryCount);
        return logininfor;
    }

    private static boolean isLoginSuccessStatus(String status)
    {
        return Constants.LOGIN_SUCCESS.equals(status)
                || Constants.LOGOUT.equals(status)
                || Constants.REGISTER.equals(status);
    }

    private static void insertOperLog(String username, String status, String message, SysOperLog operLog)
    {
        try
        {
            SpringUtils.getBean(ISysOperLogService.class).insertOperlog(operLog);
        }
        catch (Exception e)
        {
            sys_user_logger.error("insert login oper log failed: username={}, status={}, message={}",
                    username, status, message, e);
        }
    }

    private static void insertLoginInfo(String username, String status, String message, SysLogininfor logininfor)
    {
        try
        {
            SpringUtils.getBean(ISysLogininforService.class).insertLogininfor(logininfor);
        }
        catch (Exception e)
        {
            sys_user_logger.error("insert login info failed: username={}, status={}, message={}",
                    username, status, message, e);
        }
    }

    private static Integer getLoginRetryCount(String username)
    {
        if (StringUtils.isBlank(username))
        {
            return 0;
        }
        try
        {
            RedisCache redisCache = SpringUtils.getBean(RedisCache.class);
            String normalizedUsername = StringUtils.trim(username);

            Integer retryCount = readRetryCount(redisCache, CacheConstants.PWD_ERR_CNT_KEY + username);
            if (retryCount != null)
            {
                return retryCount;
            }

            if (!StringUtils.equals(username, normalizedUsername))
            {
                retryCount = readRetryCount(redisCache, CacheConstants.PWD_ERR_CNT_KEY + normalizedUsername);
                if (retryCount != null)
                {
                    return retryCount;
                }
            }

            Collection<String> cacheKeys = redisCache.keys(CacheConstants.PWD_ERR_CNT_KEY + "*");
            if (cacheKeys != null)
            {
                for (String cacheKey : cacheKeys)
                {
                    String cachedUsername = StringUtils.removeStart(cacheKey, CacheConstants.PWD_ERR_CNT_KEY);
                    if (StringUtils.equalsIgnoreCase(normalizedUsername, StringUtils.trim(cachedUsername)))
                    {
                        retryCount = readRetryCount(redisCache, cacheKey);
                        if (retryCount != null)
                        {
                            return retryCount;
                        }
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return 0;
    }

    private static Integer readRetryCount(RedisCache redisCache, String cacheKey)
    {
        Object cacheValue = redisCache.getCacheObject(cacheKey);
        if (cacheValue == null)
        {
            return null;
        }
        if (cacheValue instanceof Number)
        {
            return ((Number) cacheValue).intValue();
        }

        String retryCountText = StringUtils.trim(String.valueOf(cacheValue));
        return StringUtils.isNumeric(retryCountText) ? Integer.valueOf(retryCountText) : null;
    }
}
