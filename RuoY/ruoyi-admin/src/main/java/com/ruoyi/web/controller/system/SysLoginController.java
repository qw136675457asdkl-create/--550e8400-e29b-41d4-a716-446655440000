package com.ruoyi.web.controller.system;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.http.UserAgentUtils;
import com.ruoyi.common.utils.ip.AddressUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.service.ISysLogininforService;
import com.ruoyi.system.service.ISysNoticeService;
import nl.basjes.parse.useragent.UserAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.devtools.classpath.ClassPathChangedEvent;
import org.springframework.context.ApplicationListener;
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
import com.ruoyi.framework.audit.AuditLogTagSupport;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;

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
    private ISysLogininforService logininforService;

    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private ApplicationListener<ClassPathChangedEvent> restartingClassPathChangedEventListener;

    /**
     * 登录方法
     * 
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        // 记录登录信息
        SysLogininfor logininfor = new SysLogininfor();
        logininfor.setUserName(loginBody.getUsername());
        logininfor.setIpaddr(IpUtils.getIpAddr());
        if (!IpUtils.internalIp(logininfor.getIpaddr()))
        {
            logininfor.setMsg("非内部IP");
            logininfor.setStatus(Constants.FAIL);
            AuditLogTagSupport.tagLoginInfo(logininfor, null);
            logininforService.insertLogininfor(logininfor);
            SysNotice notice = new SysNotice();
            notice.setNoticeTitle("异常登录");
            notice.setNoticeType("1"); // 1通知 2公告
            notice.setNoticeContent("异常IP："+logininfor.getIpaddr()+"登录");
            notice.setStatus("0");
            notice.setCreateBy("system");
            noticeService.insertNotice(notice);
            return AjaxResult.error("非内部IP");
        }
        logininfor.setLoginLocation(AddressUtils.getRealAddressByIP(logininfor.getIpaddr()));
        if(StringUtils.isEmpty(logininfor.getLoginLocation())||"unknown".equalsIgnoreCase(logininfor.getLoginLocation())) {
            logininfor.setLoginLocation("未知地址");
            logininfor.setStatus(Constants.FAIL);
            AuditLogTagSupport.tagLoginInfo(logininfor, null);
            logininforService.insertLogininfor(logininfor);
            SysNotice notice = new SysNotice();
            notice.setNoticeTitle("登录地址异常");
            notice.setNoticeType("1"); // 1通知 2公告
            notice.setNoticeContent("异常地址"+logininfor.getLoginLocation()+"登录");
            notice.setStatus("0");
            notice.setCreateBy("system");
            noticeService.insertNotice(notice);
            return AjaxResult.error("获取地址失败");
        }
        // 1. 从 ServletUtils 获取当前请求，并提取 User-Agent 字符串
        String userAgentStr = ServletUtils.getRequest().getHeader("User-Agent");

        // 3. 获取并设置浏览器和操作系统名称
        logininfor.setBrowser(UserAgentUtils.getBrowser(userAgentStr));
        logininfor.setOs(UserAgentUtils.getOperatingSystem(userAgentStr));
        logininfor.setMsg("登录成功");
        logininfor.setStatus(Constants.SUCCESS);
        AuditLogTagSupport.tagLoginInfo(logininfor, null);
        logininforService.insertLogininfor(logininfor);
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
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
     * 
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
    
    // 检查初始密码是否提醒修改
    public boolean initPasswordIsModify(Date pwdUpdateDate)
    {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }
}
