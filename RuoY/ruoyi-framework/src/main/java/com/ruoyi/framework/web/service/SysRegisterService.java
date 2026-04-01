package com.ruoyi.framework.web.service;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.RegisterBody;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.CaptchaExpireException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 注册校验方法
 * 
 * @author ruoyi
 */
@Component
public class SysRegisterService
{
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private RedisCache redisCache;

    /**
     * 注册
     */
    public String register(RegisterBody registerBody)
    {
        String msg = "";
        String username = StringUtils.trim(registerBody.getUsername());
        String nickName = StringUtils.trim(registerBody.getNickName());
        String password = registerBody.getPassword();
        String email = StringUtils.trim(registerBody.getEmail());
        String phonenumber = StringUtils.trim(registerBody.getPhonenumber());
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        sysUser.setEmail(email);
        sysUser.setPhonenumber(phonenumber);

        // 验证码开关
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        if (StringUtils.isEmpty(username))
        {
            msg = "工号不能为空";
        }
        else if (StringUtils.isEmpty(nickName))
        {
            msg = "用户名称不能为空";
        }
        else if (StringUtils.isEmpty(phonenumber))
        {
            msg = "手机号不能为空";
        }
        else if (StringUtils.isEmpty(email))
        {
            msg = "邮箱不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "工号长度必须在2到20个字符之间";
        }
        else if (nickName.length() > 30)
        {
            msg = "用户名称长度不能超过30个字符";
        }
        else if (phonenumber.length() > 11)
        {
            msg = "手机号码长度不能超过11个字符";
        }
        else if (!PHONE_PATTERN.matcher(phonenumber).matches())
        {
            msg = MessageUtils.message("user.mobile.phone.number.not.valid");
        }
        else if (email.length() > 50)
        {
            msg = "邮箱长度不能超过50个字符";
        }
        else if (!EMAIL_PATTERN.matcher(email).matches())
        {
            msg = MessageUtils.message("user.email.not.valid");
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (!userService.checkUserNameUnique(sysUser))
        {
            msg = "保存工号'" + username + "'失败，工号已存在";
        }
        else if (!userService.checkPhoneUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，手机号已存在";
        }
        else if (!userService.checkEmailUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，邮箱账号已存在";
        }
        else
        {
            sysUser.setNickName(nickName);
            sysUser.setPwdUpdateDate(DateUtils.getNowDate());
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            boolean regFlag = userService.registerUser(sysUser);
            if (!regFlag)
            {
                msg = "注册失败，请联系系统管理人员";
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
            }
        }
        return msg;
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String captcha = redisCache.getCacheObject(verifyKey);
        redisCache.deleteObject(verifyKey);
        if (captcha == null)
        {
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha))
        {
            throw new CaptchaException();
        }
    }
}
