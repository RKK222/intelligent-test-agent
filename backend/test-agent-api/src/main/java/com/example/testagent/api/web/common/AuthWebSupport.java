package com.example.testagent.api.web.common;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.auth.AuthPrincipal;
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
