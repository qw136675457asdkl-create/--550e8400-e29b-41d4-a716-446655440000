package com.ruoyi.framework.web.service;

import java.util.Collection;
import javax.annotation.Resource;

import org.apache.juli.OneLineFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.exception.user.BlackListException;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.CaptchaExpireException;
import com.ruoyi.common.exception.user.UserAlreadyLoginException;
import com.ruoyi.common.exception.user.UserNotExistsException;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import static com.ruoyi.common.constant.CacheConstants.LOGIN_TOKEN_KEY;

/**
 * 登录校验方法
 *
 * @author ruoyi
 */
@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    /**
     * 登录验证
     */
    public String login(String username, String password, String code, String uuid)
    {
        Authentication authentication = null;
        try
        {
            validateCaptcha(username, code, uuid);
            loginPreCheck(username, password);

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, password);
            AuthenticationContextHolder.setContext(authenticationToken);
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (BadCredentialsException e)
        {
            AsyncManager.me().execute(
                    AsyncFactory.recordLogininfor(
                            username,
                            Constants.LOGIN_FAIL,
                            MessageUtils.message("user.password.not.match")
                    )
            );
            throw new UserPasswordNotMatchException();
        }
        catch (UserAlreadyLoginException e){
            AsyncManager.me().execute(
                    AsyncFactory.recordLogininfor(
                            username,
                            Constants.LOGIN_FAIL,
                            MessageUtils.message("user.already.login"),
                            true
                    )
            );
            throw new UserAlreadyLoginException();
        }
        catch (Exception e)
        {
            AsyncManager.me().execute(
                    AsyncFactory.recordLogininfor(
                            username,
                            Constants.LOGIN_FAIL,
                            e.getMessage()
                    )
            );
            throw new ServiceException(e.getMessage(), 200);
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        boolean concurrentLoginRisk = hasAnotherLoginUser(loginUser.getUsername());
        AsyncManager.me().execute(
                AsyncFactory.recordLogininfor(
                        loginUser.getUsername(),
                        Constants.LOGIN_SUCCESS,
                        MessageUtils.message("user.login.success"),
                        concurrentLoginRisk
                )
        );

        recordLoginInfo(loginUser.getUserId());
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验验证码
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
            String captcha = redisCache.getCacheObject(verifyKey);
            if (captcha == null)
            {
                AsyncManager.me().execute(
                        AsyncFactory.recordLogininfor(
                                username,
                                Constants.LOGIN_FAIL,
                                MessageUtils.message("user.jcaptcha.expire")
                        )
                );
                throw new CaptchaExpireException();
            }
            redisCache.deleteObject(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
            {
                AsyncManager.me().execute(
                        AsyncFactory.recordLogininfor(
                                username,
                                Constants.LOGIN_FAIL,
                                MessageUtils.message("user.jcaptcha.error")
                        )
                );
                throw new CaptchaException();
            }
        }
    }

    /**
     * 登录前置校验
     */
    public void loginPreCheck(String username, String password)
    {
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }

        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
        if (!IpUtils.internalIp(IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, "IP不是内部IP"));
            throw new BlackListException();
        }
        if (isLogin(username) && isOnline(username))
        {
//            AsyncManager.me().execute(
//                    AsyncFactory.recordLogininfor(
//                            username,
//                            Constants.LOGIN_FAIL,
//                            MessageUtils.message("user.already.login"),
//                            true
//                    )
//            );
            throw new UserAlreadyLoginException();
        }
    }

    /**
     * 记录登录信息
     */
    public void recordLoginInfo(Long userId)
    {
        userService.updateLoginInfo(userId, IpUtils.getIpAddr(), DateUtils.getNowDate());
    }

    private boolean isLogin(String username)
    {
        return hasOnlineUser(username, false);
    }

    private boolean hasAnotherLoginUser(String username)
    {
        return hasOnlineUser(username, true);
    }

    private boolean hasOnlineUser(String username, boolean excludeCurrentUser)
    {
        if (StringUtils.isBlank(username))
        {
            return false;
        }

        Collection<String> keys = redisCache.keys(LOGIN_TOKEN_KEY + "*");
        if (keys == null)
        {
            return false;
        }

        String normalizedUsername = StringUtils.trim(username);
        for (String key : keys)
        {
            LoginUser loginUser = redisCache.getCacheObject(key);
            if (loginUser == null || StringUtils.isBlank(loginUser.getUsername()))
            {
                continue;
            }

            boolean sameUser = StringUtils.equalsIgnoreCase(normalizedUsername, StringUtils.trim(loginUser.getUsername()));
            if (!excludeCurrentUser && sameUser)
            {
                return true;
            }
            if (excludeCurrentUser && !sameUser)
            {
                return true;
            }
        }
        return false;
    }
    private Boolean isOnline(String username)
    {
        String userKey = CacheConstants.LOGIN_USER_TOKEN + username;
        return redisCache.getCacheObject(userKey) != null;
    }
}
