package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityTokenService;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 服务端 OpenCode 插件专用入口；不接受普通平台 Token，也不写入 RunEvent/SSE。 */
@RestController
public class OpencodeObservabilityEventsController {

    private final OpencodeObservabilityTokenService tokenService;
    private final TraceArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public OpencodeObservabilityEventsController(
            OpencodeObservabilityTokenService tokenService,
            TraceArchiveService archiveService,
            ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(OpencodeObservabilityTokenService.ENDPOINT_PATH)
    public Mono<ApiResponse<OpencodeObservabilityModels.BatchAck>> ingest(
            @RequestBody PluginBatchRequest request,
            ServerWebExchange exchange) {
        String generation = exchange.getRequest()
                .getHeaders()
                .getFirst(OpencodeObservabilityTokenService.GENERATION_HEADER_NAME);
        var principal = tokenService.authenticate(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), generation);
        var identity = new OpencodeObservabilityModels.IngestionIdentity(
                principal.user(), principal.process().processId().value(), null, principal.generation());
        String requestTraceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        archiveService.ingestPluginBatch(identity, request.toModel(objectMapper)),
                        requestTraceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * HTTP 边界由 Spring Boot 4 的 Jackson 3 解码为普通对象，再显式转换为服务层 Jackson 2 树。
     * 这样既不让两套 JsonNode 抽象类型互相反序列化，也不把原始正文写入日志或中间存储。
     */
    record PluginBatchRequest(
            String schemaVersion,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            Instant coverageStartAt,
            long droppedCount,
            boolean complete,
            List<Object> events) {

        OpencodeObservabilityModels.PluginBatch toModel(ObjectMapper mapper) {
            List<com.fasterxml.jackson.databind.JsonNode> converted = events == null
                    ? List.of()
                    : events.stream()
                            .<com.fasterxml.jackson.databind.JsonNode>map(mapper::valueToTree)
                            .toList();
            return new OpencodeObservabilityModels.PluginBatch(
                    schemaVersion, runtime, coverageStartAt, droppedCount, complete, converted);
        }
    }
}
