package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.TraceAccessAuditLogger;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.enterprise.testagent.opencode.runtime.observability.TraceQueryService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 独立 Trace 管理入口；菜单之外再次强制 SUPER_ADMIN 权限。 */
@RestController
@RequestMapping("/api/internal/platform/traces")
public class TraceController {

    private final TraceQueryService queryService;
    private final TraceArchiveService archiveService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final TraceAccessAuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public TraceController(
            TraceQueryService queryService,
            TraceArchiveService archiveService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            TraceAccessAuditLogger auditLogger,
            ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.archiveService = archiveService;
        this.routeResolver = routeResolver;
        this.forwarder = forwarder;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<PageResponse<TraceModels.Catalog>> list(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(queryService.search(
                parseInstant(startTime), parseInstant(endTime), user, organization, agentId,
                skill, tool, status, traceId, runId, page, pageSize), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<TraceModels.Catalog> detail(
            @PathVariable String traceId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(queryService.require(traceId), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/{traceId}/events")
    public Mono<Void> events(
            @PathVariable String traceId,
            @RequestParam(required = false, defaultValue = "0") long afterSequence,
            @RequestParam(required = false, defaultValue = "200") int limit,
            ServerWebExchange exchange) {
        String requestTraceId = RuntimeApiSupport.traceId(exchange);
        var actor = requireContentAccess(exchange, traceId, "VIEW", requestTraceId);
        TraceModels.Catalog catalog;
        try {
            catalog = queryService.require(traceId);
        } catch (RuntimeException exception) {
            auditLogger.record(actor.userId(), null, traceId, "VIEW", "FAILED", requestTraceId);
            throw exception;
        }
        Mono<Void> routed = routeContentIfRequired(catalog, exchange);
        if (routed != null) {
            // 成功与远端业务失败由归档节点记录；仅补充转发链路自身失败，避免双记成功审计。
            return routed.doOnError(error -> auditLogger.record(
                    actor.userId(), catalog.userId(), traceId, "VIEW", "FAILED", requestTraceId));
        }
        return Mono.fromCallable(() -> archiveService.readRawEvents(traceId, afterSequence, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(page -> writeJson(exchange, ApiResponse.ok(page, requestTraceId)))
                .doOnSuccess(ignored -> auditLogger.record(
                        actor.userId(), catalog.userId(), traceId, "VIEW", "SUCCESS", requestTraceId))
                .doOnError(error -> auditLogger.record(
                        actor.userId(), catalog.userId(), traceId, "VIEW", "FAILED", requestTraceId));
    }

    @GetMapping("/{traceId}/download")
    public Mono<Void> download(
            @PathVariable String traceId,
            ServerWebExchange exchange) {
        String requestTraceId = RuntimeApiSupport.traceId(exchange);
        var actor = requireContentAccess(exchange, traceId, "DOWNLOAD", requestTraceId);
        TraceModels.Catalog catalog;
        try {
            catalog = queryService.require(traceId);
        } catch (RuntimeException exception) {
            auditLogger.record(actor.userId(), null, traceId, "DOWNLOAD", "FAILED", requestTraceId);
            throw exception;
        }
        Mono<Void> routed = routeContentIfRequired(catalog, exchange);
        if (routed != null) {
            return routed.doOnError(error -> auditLogger.record(
                    actor.userId(), catalog.userId(), traceId, "DOWNLOAD", "FAILED", requestTraceId));
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.parseMediaType("application/gzip"));
        exchange.getResponse().getHeaders().set(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + traceId + ".ndjson.gz\"");
        return exchange.getResponse().writeWith(DataBufferUtils.readInputStream(
                        () -> archiveService.openCompressedTrace(traceId),
                        exchange.getResponse().bufferFactory(),
                        64 * 1024))
                .doOnSuccess(ignored -> auditLogger.record(
                        actor.userId(), catalog.userId(), traceId, "DOWNLOAD", "SUCCESS", requestTraceId))
                .doOnError(error -> auditLogger.record(
                        actor.userId(), catalog.userId(), traceId, "DOWNLOAD", "FAILED", requestTraceId));
    }

    private Mono<Void> routeContentIfRequired(TraceModels.Catalog catalog, ServerWebExchange exchange) {
        // Trace 正文归属于持久化存储节点而不是某次 Java 进程；JVM 重启后仍应由同节点的新进程读取。
        if (routeResolver.isCurrent(catalog.linuxServerId())) {
            return null;
        }
        if ("true".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER))) {
            throw new PlatformException(ErrorCode.TRACE_CONTENT_UNAVAILABLE, "Trace 归档节点不可用");
        }
        try {
            return forwarder.forwardRaw(exchange, routeResolver.requireBackend(catalog.linuxServerId()));
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.TRACE_CONTENT_UNAVAILABLE,
                    "Trace 归档节点不可用",
                    java.util.Map.of("linuxServerId", catalog.linuxServerId()),
                    exception);
        }
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, Object body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return Mono.error(new PlatformException(ErrorCode.INTERNAL_ERROR, "Trace 事件响应序列化失败"));
        }
    }

    private com.enterprise.testagent.domain.auth.AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private com.enterprise.testagent.domain.auth.AuthPrincipal requireContentAccess(
            ServerWebExchange exchange,
            String traceId,
            String action,
            String requestTraceId) {
        try {
            return requireSuperAdmin(exchange);
        } catch (RuntimeException exception) {
            auditLogger.recordDenied(
                    AuthWebSupport.getOptionalAuthPrincipal(exchange).map(principal -> principal.userId()),
                    traceId,
                    action,
                    requestTraceId);
            throw exception;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "时间参数必须是 ISO-8601 Instant 格式");
        }
    }
}
