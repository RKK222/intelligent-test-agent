package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.configuration.management.InternalModelTokenManagementApplicationService;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 可复用内部模型 Token 管理 API。响应只包含安全元数据，绝不回传 Token 明文。
 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management/internal-model-tokens")
public class InternalModelTokenManagementController {

    private final InternalModelTokenManagementApplicationService service;

    public InternalModelTokenManagementController(InternalModelTokenManagementApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping
    public Mono<ApiResponse<Object>> list(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, service::list);
    }

    @PostMapping
    public Mono<ApiResponse<Object>> create(
            @RequestBody(required = false) CreateInternalModelTokenRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        CreateInternalModelTokenRequest command = request == null
                ? new CreateInternalModelTokenRequest(null, null)
                : request;
        return blocking(traceId, () -> service.create(command.name(), command.token(), traceId));
    }

    @PatchMapping("/{tokenId}")
    public Mono<ApiResponse<Object>> update(
            @PathVariable long tokenId,
            @RequestBody(required = false) UpdateInternalModelTokenRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        UpdateInternalModelTokenRequest command = request == null
                ? new UpdateInternalModelTokenRequest(null, null)
                : request;
        return blocking(traceId, () -> service.update(tokenId, command.name(), command.token(), traceId));
    }

    @DeleteMapping("/{tokenId}")
    public Mono<ApiResponse<Object>> delete(
            @PathVariable long tokenId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, () -> {
            service.delete(tokenId, traceId);
            return Map.of("tokenId", tokenId, "deleted", true);
        });
    }

    private void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private Mono<ApiResponse<Object>> blocking(String traceId, java.util.concurrent.Callable<Object> supplier) {
        return Mono.fromCallable(() -> ApiResponse.ok(supplier.call(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 新建请求中的 token 是外部系统提供的原值，仅用于写入，不进入响应 DTO。 */
    public record CreateInternalModelTokenRequest(String name, String token) {
    }

    /** token 为空或缺失时保留已记录的值。 */
    public record UpdateInternalModelTokenRequest(String name, String token) {
    }
}
