package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.InternalModelCatalogManagementApplicationService.UpdateCatalogCommand;
import com.enterprise.testagent.configuration.management.InternalModelCatalogManagementService;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.model.gateway.ModelCapabilityProbe;
import com.enterprise.testagent.model.gateway.ModelCapabilityProbeResult;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 内部供应商模型目录与逐能力探测管理 API。 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management/internal-model-providers/{providerId}/models")
public class InternalModelCatalogManagementController {

    private final InternalModelCatalogManagementService catalogService;
    private final ModelCapabilityProbe probeService;

    public InternalModelCatalogManagementController(
            InternalModelCatalogManagementService catalogService,
            ModelCapabilityProbe probeService) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.probeService = Objects.requireNonNull(probeService);
    }

    @GetMapping
    public Mono<ApiResponse<Object>> get(
            @PathVariable String providerId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok((Object) catalogService.current(providerId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping
    public Mono<ApiResponse<Object>> put(
            @PathVariable String providerId,
            @RequestBody(required = false) UpdateCatalogCommand request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        UpdateCatalogCommand command = request == null ? new UpdateCatalogCommand(null) : request;
        return Mono.fromCallable(() -> ApiResponse.ok(
                        (Object) catalogService.save(providerId, command, traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{modelId}/probe")
    public Mono<ApiResponse<ModelCapabilityProbeResult>> probe(
            @PathVariable String providerId,
            @PathVariable String modelId,
            @RequestBody(required = false) ProbeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        if (request == null || request.capability() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "探测 capability 不能为空");
        }
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.defer(() -> probeService.probe(
                        providerId,
                        modelId,
                        request.capability(),
                        principal.unifiedAuthId(),
                        traceId))
                .map(result -> ApiResponse.ok(result, traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    public record ProbeRequest(ModelCapability capability) {
    }
}
