package com.ruoyi.system.service.impl;

import java.util.List;

import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.mapper.SysOperLogMapper;
import com.ruoyi.system.service.ISysOperLogService;

/**
 * 操作日志 服务层处理
 * 
 * @author ruoyi
 */
@Service
public class SysOperLogServiceImpl implements ISysOperLogService
{
    private static final int TITLE_MAX_LENGTH = 50;
    private static final int METHOD_MAX_LENGTH = 100;
    private static final int REQUEST_METHOD_MAX_LENGTH = 10;

    private static final int OPER_NAME_MAX_LENGTH = 50;
    private static final int DEPT_NAME_MAX_LENGTH = 50;
    private static final int OPER_URL_MAX_LENGTH = 255;
    private static final int OPER_IP_MAX_LENGTH = 128;
    private static final int OPER_LOCATION_MAX_LENGTH = 255;
    private static final int OPER_PARAM_MAX_LENGTH = 1000;
    private static final int JSON_RESULT_MAX_LENGTH = 1000;
    private static final int ERROR_MSG_MAX_LENGTH = 1000;
    private static final int EVENT_TYPE_MAX_LENGTH = 64;
    private static final int BIZ_CATEGORY_MAX_LENGTH = 64;
    private static final int HIGHLIGHT_TAG_MAX_LENGTH = 64;

    @Autowired
    private SysOperLogMapper operLogMapper;

    /**
     * 新增操作日志
     * 
     * @param operLog 操作日志对象
     */
    @Override
    public void insertOperlog(SysOperLog operLog)
    {
        if (operLog == null)
        {
            return;
        }
        trimOperLogFields(operLog);
        operLogMapper.insertOperlog(operLog);
    }

    private void trimOperLogFields(SysOperLog operLog)
    {
        if (operLog == null)
        {
            return;
        }

        operLog.setTitle(trimToLength(operLog.getTitle(), TITLE_MAX_LENGTH));
        operLog.setMethod(trimToLength(operLog.getMethod(), METHOD_MAX_LENGTH));
        operLog.setRequestMethod(trimToLength(operLog.getRequestMethod(), REQUEST_METHOD_MAX_LENGTH));
        operLog.setOperName(trimToLength(operLog.getOperName(), OPER_NAME_MAX_LENGTH));
        operLog.setDeptName(trimToLength(operLog.getDeptName(), DEPT_NAME_MAX_LENGTH));
        operLog.setOperUrl(trimToLength(operLog.getOperUrl(), OPER_URL_MAX_LENGTH));
        operLog.setOperIp(trimToLength(operLog.getOperIp(), OPER_IP_MAX_LENGTH));
        operLog.setOperLocation(trimToLength(operLog.getOperLocation(), OPER_LOCATION_MAX_LENGTH));
        operLog.setOperParam(trimToLength(operLog.getOperParam(), OPER_PARAM_MAX_LENGTH));
        operLog.setJsonResult(trimToLength(operLog.getJsonResult(), JSON_RESULT_MAX_LENGTH));
        operLog.setErrorMsg(trimToLength(operLog.getErrorMsg(), ERROR_MSG_MAX_LENGTH));
        operLog.setEventType(trimToLength(operLog.getEventType(), EVENT_TYPE_MAX_LENGTH));
        operLog.setBizCategory(trimToLength(operLog.getBizCategory(), BIZ_CATEGORY_MAX_LENGTH));
        operLog.setHighlightTag(trimToLength(operLog.getHighlightTag(), HIGHLIGHT_TAG_MAX_LENGTH));
    }

    private String trimToLength(String value, int maxLength)
    {
        if (StringUtils.isEmpty(value) || maxLength <= 0)
        {
            return value;
        }
        return StringUtils.substring(value, 0, maxLength);
    }

    /**
     * 查询系统操作日志集合
     * 
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    @Override
    public List<SysOperLog> selectOperLogList(SysOperLog operLog)
    {
        return operLogMapper.selectOperLogList(operLog);
    }

    /**
     * 批量删除系统操作日志
     * 
     * @param operIds 需要删除的操作日志ID
     * @return 结果
     */
    @Override
    public int deleteOperLogByIds(Long[] operIds)
    {
        return operLogMapper.deleteOperLogByIds(operIds);
    }

    /**
     * 查询操作日志详细
     * 
     * @param operId 操作ID
     * @return 操作日志对象
     */
    @Override
    public SysOperLog selectOperLogById(Long operId)
    {
        return operLogMapper.selectOperLogById(operId);
    }

    /**
     * 清空操作日志
     */
    @Override
    public void cleanOperLog()
    {
        operLogMapper.cleanOperLog();
    }

    /**
     * 根据操作日志ID查询操作日志
     *
     * @param operIds 需要查询的操作日志ID
     * @return 操作日志集合
     */
    @Override
    public List<SysOperLog> selectOperLogByIds(Long[] operIds)
    {
        return operLogMapper.selectOperLogByIds(operIds);
    }

    @Override
    public double getAuditLogTableSize()
    {
        Double sizeMb = operLogMapper.getAuditLogTableSize();
        return sizeMb == null ? 0D : sizeMb;
    }

}
