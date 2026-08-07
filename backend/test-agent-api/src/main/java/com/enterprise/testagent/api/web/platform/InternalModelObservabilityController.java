package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelObservabilityQueryService;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelProviderProbeService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 内部模型代理调用可观测性查询 API，仅限 SUPER_ADMIN。
 * 只返回结构化观测字段，不暴露任何请求/响应正文。
 */
@RestController
@RequestMapping("/api/internal/platform/opencode-runtime/internal-model-observability")
public class InternalModelObservabilityController {

    private final InternalModelObservabilityQueryService queryService;
    private final InternalModelProviderProbeService probeService;

    public InternalModelObservabilityController(
            InternalModelObservabilityQueryService queryService,
            InternalModelProviderProbeService probeService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.probeService = Objects.requireNonNull(probeService, "probeService must not be null");
    }

    @GetMapping("/call-records")
    public Mono<ApiResponse<Object>> callRecords(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) InternalModelCallOutcome outcome,
            @RequestParam(required = false) InternalModelCallSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
            PageResponse<com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord> result =
                    queryService.queryCallRecords(
                            providerId, outcome, source, from, to, new PageRequest(page, size));
            return ApiResponse.ok((Object) result, traceId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/stats")
    public Mono<ApiResponse<Object>> stats(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) InternalModelCallSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        (Object) queryService.queryHourlyStats(providerId, source, from, to), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/probe-status")
    public Mono<ApiResponse<Object>> probeStatus(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        (Object) queryService.findProbeStatus(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/probe")
    public Mono<ApiResponse<Object>> probe(
            @RequestBody(required = false) ProbeRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
            InternalModelProviderProbeService.Result result = request == null || request.providerId() == null
                    ? probeService.probeAll(traceId)
                    : probeService.probeProvider(request.providerId(), traceId);
            return ApiResponse.ok((Object) result.outcomes(), traceId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    public record ProbeRequest(String providerId) {
        public ProbeRequest {
            if (providerId != null && providerId.isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "providerId 不能为空");
            }
        }
    }
}
