package com.ruoyi.web.controller.system;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.domain.SysUserOnline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录验证
 *
 * @author ruoyi
 */
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;
    @Autowired
    private RedisCache redisCache;

    /**
     * 登录方法
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        String token = loginService.login(
                loginBody.getUsername(),
                loginBody.getPassword(),
                loginBody.getCode(),
                loginBody.getUuid()
        );
        SysUserOnline online =new SysUserOnline();

        online.setTokenId(token);
        online.setActiveTime(System.currentTimeMillis());
        online.setUserName(loginBody.getUsername());
        ajax.put(Constants.TOKEN, token);
        redisCache.setCacheObject(CacheConstants.LOGIN_USER_TOKEN + loginBody.getUsername(), online, 150, TimeUnit.SECONDS);
        return ajax;
    }

    /**
     * 获取用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        Set<String> roles = permissionService.getRolePermission(user);
        Set<String> permissions = permissionService.getMenuPermission(user);
        if (!loginUser.getPermissions().equals(permissions))
        {
            loginUser.setPermissions(permissions);
            tokenService.refreshToken(loginUser);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("isDefaultModifyPwd", initPasswordIsModify(user.getPwdUpdateDate()));
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    /**
     * 获取路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
    @PostMapping("/heartbeat")
    public AjaxResult heartbeat(HttpServletRequest request)
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if(loginUser == null){
            return AjaxResult.error("登录过期，请重新登录");
        }
        heartbeat(loginUser);
        // 验证令牌有效期，相差不足20分钟，自动刷新缓存
        tokenService.verifyToken(loginUser);
        return AjaxResult.success();
    }

    public boolean initPasswordIsModify(Date pwdUpdateDate)
    {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }

    public void heartbeat(LoginUser loginUser)
    {
        if (loginUser == null || loginUser.getUsername() == null)
        {
            return;
        }

        String userKey = CacheConstants.LOGIN_USER_TOKEN + loginUser.getUsername();
        SysUserOnline online = redisCache.getCacheObject(userKey);
        if (online == null)
        {
            online = new SysUserOnline();
            online.setTokenId(userKey);
            online.setActiveTime(System.currentTimeMillis());
            online.setUserName(loginUser.getUsername());
        }
        redisCache.setCacheObject(userKey, online, 150, TimeUnit.SECONDS);
    }
}
