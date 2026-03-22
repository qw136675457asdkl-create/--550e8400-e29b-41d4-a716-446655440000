package com.ruoyi.common.constant;

/**
 * 缓存的key 常量
 * 
 * @author ruoyi
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    public static final String LOGIN_USER_KEY = "login_users:";

    public static final String LOGIN_USER_TOKEN = "login_user_token:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /**
     * 试验信息主缓存 redis key
     */
    public static final String EXPERIMENT_INFO_KEY = "experiment_info:";
    /**
     * 项目信息主缓存 redis key
     */
    public static final String PROJECT_INFO_KEY = "project_info:";
    /**
     * 试验目标主缓存 redis key
     */
    public static final String EXPERIMENT_TARGET_KEY = "experiment_target:";
    /**
     * 数据主缓存 redis key
     */
    public static final String DATA_INFO_KEY = "data_info:";

    /**
     * 行政区域查询缓存 redis key（高德API结果缓存，减少外部调用）
     */
    public static final String SYS_REGION_KEY = "sys_region:";
}
