package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.system.management.auth.AuthApplicationService;
import jakarta.validation.Valid;
import java.util.List;
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
    private final DictionaryRepository dictionaryRepository;

    /**
     * 注入认证应用服务与字典仓储。
     * 字典仓储仅用于按 {@code dict_value} 查 {@code dict_label}，把 {@code roles} 转成中文展示名。
     */
    public AuthController(AuthApplicationService authApplicationService, DictionaryRepository dictionaryRepository) {
        this.authApplicationService = authApplicationService;
        this.dictionaryRepository = dictionaryRepository;
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
     * 通过统一认证号登录（AAM 跳转后使用）。成功后返回 Token，Token 在 Redis 中保存 1 天。
     */
    @PostMapping("/api/auth/login-by-unified-auth")
    public ResponseEntity<ApiResponse<AuthDtos.LoginResponse>> loginByUnifiedAuth(
            @Valid @RequestBody AuthDtos.UnifiedAuthLoginRequest request,
            ServerWebExchange exchange) {
        String traceId = traceIdFrom(exchange);

        AuthPrincipal principal = authApplicationService.loginByUnifiedAuthId(
                request.unifiedAuthId(),
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

        // 把 principal.roles() 翻译成 dictionaries.dict_label 的中文展示名，供右上角用户菜单直接渲染。
        // 字典缺失或 dict_key 不匹配时回退成角色 code，避免阻断 /api/auth/me 主链路。
        List<String> roleLabels = principal.roles().stream()
                .map(role -> dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, role)
                        .map(Dictionary::dictLabel)
                        .orElse(role))
                .toList();

        AuthDtos.CurrentUserResponse response = new AuthDtos.CurrentUserResponse(
                principal.userId().value(),
                principal.username(),
                principal.unifiedAuthId(),
                null, null, null,
                principal.roles(),
                roleLabels);

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
