package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.system.management.auth.AuthApplicationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 认证控制器，提供登录、登出、当前用户查询和 Token 刷新接口。
 */
@RestController
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AuthApplicationService authApplicationService;

    /**
     * 注入认证应用服务。
     */
    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 用户登录。成功后返回 Token，Token 在 Redis 中保存 1 天。
     */
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<AuthDtos.LoginResponse>> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);

        AuthPrincipal principal = authApplicationService.login(
                request.username(),
                request.password(),
                ipFrom(exchange),
                userAgentFrom(exchange));

        AuthDtos.LoginResponse response = new AuthDtos.LoginResponse(
                principal.token(),
                principal.userId().value(),
                principal.username(),
                principal.unifiedAuthId(),
                principal.roles());

        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 用户登出。从 Redis 中删除当前 Token。
     */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        String token = AuthWebSupport.extractBearerToken(exchange);
        if (token != null) {
            authApplicationService.logout(token);
            LOGGER.info("User logged out, traceId={}", traceId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    /**
     * 获取当前登录用户信息。需要有效的 Token。
     */
    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<AuthDtos.CurrentUserResponse>> me(ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);

        // 从 principal 获取用户基本信息；当前版本只返回 principal 中存储的信息
        AuthDtos.CurrentUserResponse response = new AuthDtos.CurrentUserResponse(
                principal.userId().value(),
                principal.username(),
                principal.unifiedAuthId(),
                null, null, null,
                principal.roles());

        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 刷新 Token。根据当前有效 Token 生成新 Token，旧 Token 失效。
     */
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<ApiResponse<AuthDtos.LoginResponse>> refresh(ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);

        AuthPrincipal refreshed = authApplicationService.refreshToken(
                principal,
                ipFrom(exchange),
                userAgentFrom(exchange));

        AuthDtos.LoginResponse response = new AuthDtos.LoginResponse(
                refreshed.token(),
                refreshed.userId().value(),
                refreshed.username(),
                refreshed.unifiedAuthId(),
                refreshed.roles());

        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 从 exchange attribute 或请求头恢复 traceId。
     */
    private String traceIdFrom(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    /**
     * 获取客户端 IP 地址。
     */
    private static String ipFrom(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
    }

    /**
     * 获取客户端 User-Agent。
     */
    private static String userAgentFrom(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }
}
