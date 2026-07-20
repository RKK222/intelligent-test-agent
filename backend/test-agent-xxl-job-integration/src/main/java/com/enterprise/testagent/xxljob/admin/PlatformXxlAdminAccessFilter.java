package com.enterprise.testagent.xxljob.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 以扩展过滤器禁用上游原生登录、改密和 XXL 用户写操作，不修改上游 Controller。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformXxlAdminAccessFilter extends OncePerRequestFilter {

    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/auth/login",
            "/auth/doLogin",
            "/auth/updatePwd",
            "/user/insert",
            "/user/update",
            "/user/delete");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = pathWithinContext(request);
        if (BLOCKED_PATHS.contains(path)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"NATIVE_LOGIN_DISABLED\",\"message\":\"仅允许平台 SSO 登录\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String pathWithinContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
