package com.ruoyi.web.service;

import java.util.function.Supplier;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.lab.task.TaskActorContext;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class BusinessTaskActorContext implements TaskActorContext
{
    private final ISysUserService users;
    private final SysPermissionService permissions;
    public BusinessTaskActorContext(ISysUserService users, SysPermissionService permissions)
    { this.users = users; this.permissions = permissions; }

    @Override public <T> T asCurrentActor(long userId, Supplier<T> action)
    {
        var user = users.selectUserById(userId);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))
            throw new AccessDeniedException("账号已停用或不存在");
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var login = new LoginUser(userId, user.getDeptId(), user, permissions.getMenuPermission(user));
        context.setAuthentication(new UsernamePasswordAuthenticationToken(login, null, login.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); }
        finally { SecurityContextHolder.setContext(previous); }
    }
}
