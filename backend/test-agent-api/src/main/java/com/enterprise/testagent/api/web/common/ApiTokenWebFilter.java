package com.enterprise.testagent.api.web.common;

import com.enterprise.testagent.common.api.ApiErrorResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * API token 鉴权占位。未配置 token 时放行，配置后要求 Bearer token，便于后续替换为正式鉴权。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiTokenWebFilter implements WebFilter {

    private static final String INTERNAL_MODEL_PROXY_PATH =
            "/api/internal/platform/opencode-runtime/internal-model-proxy/v1/";
    private static final String INTERNAL_MODEL_PROXY_ROOT_PATH =
            "/api/internal/platform/opencode-runtime/internal-model-proxy/v1";
    private static final String NIGHT_EXECUTION_INTERNAL_DISPATCH_PATH =
            "/api/internal/platform/opencode-runtime/night-execution/internal-dispatch";
    private static final String PERSONAL_WORKSPACE_RELOCATION_TICKET_PATH =
            "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer-tickets";
    private static final String PERSONAL_WORKSPACE_RELOCATION_WEB_SOCKET_PATH =
            "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer/ws";
    private static final String WORKSPACE_GIT_TOOL_PATH =
            "/api/internal/agent/opencode/workspace-git-tool";
    private static final String UI_TEST_TOOL_CONFIG_PATH =
            "/api/internal/agent/opencode/ui-test-tool/config";
    private static final String LOBEHUB_SSO_REDEEM_PATH =
            "/api/internal/platform/lobehub-sso/tickets/redeem";
    private static final String LOBEHUB_SSO_REVOKE_PATH =
            "/api/internal/platform/lobehub-sso/grants/revoke";
    private static final String MODEL_GATEWAY_ROOT_PATH =
            "/api/internal/platform/model-gateway/v1";
    private static final String MODEL_GATEWAY_PATH = MODEL_GATEWAY_ROOT_PATH + "/";

    private final String apiToken;
    private final ObjectMapper objectMapper;

    /**
     * 使用配置中的 API token 创建过滤器，空 token 表示本地/测试环境放行。
     */
    @Autowired
    public ApiTokenWebFilter(@Value("${test-agent.security.api-token:}") String apiToken) {
        this(apiToken, new ObjectMapper());
    }

    /**
     * 创建可注入 ObjectMapper 的过滤器，便于单元测试校验错误响应。
     */
    ApiTokenWebFilter(String apiToken, ObjectMapper objectMapper) {
        this.apiToken = apiToken == null || apiToken.isBlank() ? null : apiToken;
        this.objectMapper = objectMapper;
    }

    /**
     * 对 /api/ 路径执行 Bearer token 校验，未配置 token 或非 API 路径直接放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/api/")
                || path.equals(INTERNAL_MODEL_PROXY_ROOT_PATH)
                || path.startsWith(INTERNAL_MODEL_PROXY_PATH)
                || path.equals(NIGHT_EXECUTION_INTERNAL_DISPATCH_PATH)
                || path.equals(PERSONAL_WORKSPACE_RELOCATION_TICKET_PATH)
                || path.equals(PERSONAL_WORKSPACE_RELOCATION_WEB_SOCKET_PATH)
                || path.equals(WORKSPACE_GIT_TOOL_PATH)
                || path.equals(UI_TEST_TOOL_CONFIG_PATH)
                || path.equals(LOBEHUB_SSO_REDEEM_PATH)
                || path.equals(LOBEHUB_SSO_REVOKE_PATH)
                || path.equals(MODEL_GATEWAY_ROOT_PATH)
                || path.startsWith(MODEL_GATEWAY_PATH)
                || apiToken == null) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (("Bearer " + apiToken).equals(authorization)) {
            return chain.filter(exchange);
        }
        return unauthorized(exchange);
    }

    /**
     * 写出统一 401 错误响应，并确保响应头包含 traceId。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        String traceId = traceId(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.UNAUTHENTICATED, traceId);
            byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 从 exchange 或请求头恢复 traceId，缺失或非法时生成新 traceId。
     */
    private String traceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
