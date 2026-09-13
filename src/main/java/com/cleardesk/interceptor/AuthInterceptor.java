package com.cleardesk.interceptor;

import com.cleardesk.annotation.AuthCheck;
import com.cleardesk.common.ErrorCode;
import com.cleardesk.context.UserHolder;
import com.cleardesk.exception.BusinessException;
import com.cleardesk.model.domain.User;
import com.cleardesk.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static com.cleardesk.constant.UserConstant.ADMIN_ROLE;
import static com.cleardesk.constant.UserConstant.USER_LOGIN_STATE;
import static com.cleardesk.constant.UserConstant.USER_STATUS_NORMAL;

/**
 * 根据 @AuthCheck 校验登录态；管理员角色每次从数据库读取，避免 Session 中的旧角色生效。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 与 UserService 循环依赖，延迟注入。
     */
    @Lazy
    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        AuthCheck authCheck = ((HandlerMethod) handler).getMethodAnnotation(AuthCheck.class);
        if (authCheck == null) {
            return true;
        }
        HttpSession session = request.getSession(false);
        Object userIdObj = session == null ? null : session.getAttribute(USER_LOGIN_STATE);
        if (!(userIdObj instanceof Long)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        User user = userService.getById((Long) userIdObj);
        // 用户已删或已禁用时清 Session，避免继续拿着失效 id 访问
        if (user == null) {
            session.invalidate();
            throw new BusinessException(ErrorCode.NOT_LOGIN, "登录态无效");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != USER_STATUS_NORMAL) {
            session.invalidate();
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        UserHolder.set(user);
        if (authCheck.mustAdmin() && (user.getUserRole() == null || user.getUserRole() != ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // ThreadLocal 不会随请求结束自动清，不 remove 会在线程池里串到下一个请求
        UserHolder.remove();
    }
}
