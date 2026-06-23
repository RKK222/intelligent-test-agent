package com.icbc.testagent.api.web.common;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Web 入口认证共享工具，集中维护认证 attribute 和 Bearer Token 提取规则。
 */
public final class AuthWebSupport {

    /** 认证 attribute key，由 JwtAuthWebFilter 设置并由认证 Controller 读取。 */
    public static final String AUTH_ATTR = "test-agent.auth.principal";

    private AuthWebSupport() {
    }

    /**
     * 从 exchange attribute 中获取认证主体，缺失时统一转换为未认证错误。
     */
    public static AuthPrincipal getAuthPrincipal(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(AUTH_ATTR);
        if (attr instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new PlatformException(ErrorCode.UNAUTHENTICATED, "未认证");
    }

    /**
     * 校验当前认证主体是否具备指定全局角色，不满足时返回统一无权限错误。
     */
    public static AuthPrincipal requireRole(ServerWebExchange exchange, String role) {
        AuthPrincipal principal = getAuthPrincipal(exchange);
        if (hasRole(principal, role)) {
            return principal;
        }
        throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
    }

    /**
     * 判断全局角色权限，超级管理员继承应用管理员能力，但不改写认证主体中的实际角色。
     */
    public static boolean hasRole(AuthPrincipal principal, String role) {
        if (principal.roles().contains(role)) {
            return true;
        }
        return Dictionary.ROLE_APP_ADMIN.equals(role)
                && principal.roles().contains(Dictionary.ROLE_SUPER_ADMIN);
    }

    /**
     * 从 Authorization 请求头提取 Bearer Token；非 Bearer 格式返回 null。
     */
    public static String extractBearerToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
