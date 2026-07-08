package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.configuration.management.InternalModelProviderManagementApplicationService;
import com.icbc.testagent.configuration.management.InternalModelProviderManagementApplicationService.UpdateInternalModelProvidersCommand;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 内部模型供应商配置 API，仅限 SUPER_ADMIN；不暴露模型清单维护能力。
 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management/internal-model-providers")
public class InternalModelProviderManagementController {

    private final InternalModelProviderManagementApplicationService service;
    private final InternalModelProviderRegistry registry;

    public InternalModelProviderManagementController(
            InternalModelProviderManagementApplicationService service,
            InternalModelProviderRegistry registry) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @GetMapping
    public Mono<ApiResponse<Object>> get(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, service::current);
    }

    @PutMapping
    public Mono<ApiResponse<Object>> put(
            @RequestBody(required = false) UpdateInternalModelProvidersCommand request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        UpdateInternalModelProvidersCommand command = request == null
                ? new UpdateInternalModelProvidersCommand(null, null)
                : request;
        return blocking(traceId, () -> service.save(command, traceId));
    }

    @GetMapping("/refresh-status")
    public Mono<ApiResponse<Object>> refreshStatus(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, () -> registry.currentSnapshot().toResponse());
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<Object>> refresh(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, () -> {
            service.requestRefresh(traceId);
            return registry.currentSnapshot().toResponse();
        });
    }

    private void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private Mono<ApiResponse<Object>> blocking(String traceId, java.util.concurrent.Callable<Object> supplier) {
        return Mono.fromCallable(() -> ApiResponse.ok(supplier.call(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
