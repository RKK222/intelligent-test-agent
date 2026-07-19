package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 通用参数 JVM 内存值查询与手工刷新 API，仅允许超级管理员访问。 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management/common-parameters/memory-values")
public class CommonParameterMemoryController {

    private final CommonParameterMemoryBackendRoutingService routingService;

    /** 注入统一的本机及跨 Java 路由服务。 */
    CommonParameterMemoryController(CommonParameterMemoryBackendRoutingService routingService) {
        this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
    }

    /** 聚合查询全部在线 Java 的显式内存参数。 */
    @GetMapping
    public Mono<ApiResponse<Object>> queryAll(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return routingService.queryAll(exchange)
                .map(response -> ApiResponse.ok(response, traceId));
    }

    /** 查询指定 Java 的显式内存参数。 */
    @GetMapping("/{backendProcessId}")
    public Mono<ApiResponse<Object>> queryOne(
            @PathVariable String backendProcessId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return routingService.queryOne(exchange, parseProcessId(backendProcessId))
                .map(response -> ApiResponse.ok(response, traceId));
    }

    /** 按数据库当前值刷新全部在线 Java 的显式内存参数。 */
    @PostMapping("/refresh")
    public Mono<ApiResponse<Object>> refreshAll(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return routingService.refreshAll(exchange, traceId)
                .map(response -> ApiResponse.ok(response, traceId));
    }

    /** 按数据库当前值刷新指定 Java 的显式内存参数。 */
    @PostMapping("/{backendProcessId}/refresh")
    public Mono<ApiResponse<Object>> refreshOne(
            @PathVariable String backendProcessId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return routingService.refreshOne(exchange, parseProcessId(backendProcessId), traceId)
                .map(response -> ApiResponse.ok(response, traceId));
    }

    private BackendProcessId parseProcessId(String value) {
        try {
            return new BackendProcessId(value);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "backendProcessId 无效",
                    Map.of("backendProcessId", value == null ? "" : value),
                    exception);
        }
    }

    private void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }
}
