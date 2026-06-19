package com.example.testagent.api.web;

import com.example.testagent.opencode.runtime.run.RunApplicationService;
import com.example.testagent.opencode.runtime.run.RunDiffApplicationService;
import com.example.testagent.opencode.runtime.run.RunMessageRecoveryService;
import com.example.testagent.common.api.ApiResponse;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.event.RunEventSseMapper;
import com.example.testagent.event.RunEventSsePayload;
import com.example.testagent.event.RunEventSseStreamService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Run HTTP/SSE Controller，启动、查询、取消和事件流均委托应用服务或 event 模块。
 */
@RestController
public class RunController {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);
    private static final int DEFAULT_BATCH_LIMIT = 100;

    private final RunApplicationService runService;
    private final RunDiffApplicationService runDiffService;
    private final RunEventSseStreamService eventStreamService;
    private final RunMessageRecoveryService messageRecoveryService;
    private final RunEventSseMapper sseMapper;

    @Autowired
    public RunController(
            RunApplicationService runService,
            RunDiffApplicationService runDiffService,
            RunEventSseStreamService eventStreamService,
            RunMessageRecoveryService messageRecoveryService,
            RunEventSseMapper sseMapper) {
        this.runService = runService;
        this.runDiffService = runDiffService;
        this.eventStreamService = eventStreamService;
        this.messageRecoveryService = messageRecoveryService;
        this.sseMapper = Objects.requireNonNull(sseMapper, "sseMapper must not be null");
    }

    public RunController(
            RunApplicationService runService,
            RunDiffApplicationService runDiffService,
            RunEventSseStreamService eventStreamService) {
        this(runService, runDiffService, eventStreamService, null, new RunEventSseMapper());
    }

    @PostMapping({"/api/runs", "/api/internal/platform/opencode-runtime/runs"})
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> startRun(
            @Valid @RequestBody RuntimeDtos.StartRunRequest request,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId -> RuntimeDtos.RunResponse.from(runService.startRun(
                request.toInput(), traceId)));
    }

    @GetMapping({"/api/runs/{runId}", "/api/internal/platform/opencode-runtime/runs/{runId}"})
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> getRun(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, ignored -> RuntimeDtos.RunResponse.from(runService.getRun(new RunId(runId))));
    }

    @PostMapping({"/api/runs/{runId}/cancel", "/api/internal/platform/opencode-runtime/runs/{runId}/cancel"})
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> cancelRun(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunResponse.from(runService.cancelRun(new RunId(runId), traceId)));
    }

    @GetMapping({"/api/runs/{runId}/diff", "/api/internal/platform/opencode-runtime/runs/{runId}/diff"})
    public Mono<ApiResponse<RuntimeDtos.RunDiffResponse>> getDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffResponse.from(runDiffService.getDiff(new RunId(runId), traceId)));
    }

    @PostMapping({"/api/runs/{runId}/diff/accept", "/api/internal/platform/opencode-runtime/runs/{runId}/diff/accept"})
    public Mono<ApiResponse<RuntimeDtos.RunDiffActionResponse>> acceptDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffActionResponse.from(runDiffService.acceptDiff(new RunId(runId), traceId)));
    }

    @PostMapping({"/api/runs/{runId}/diff/reject", "/api/internal/platform/opencode-runtime/runs/{runId}/diff/reject"})
    public Mono<ApiResponse<RuntimeDtos.RunDiffActionResponse>> rejectDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffActionResponse.from(runDiffService.rejectDiff(new RunId(runId), traceId)));
    }

    @GetMapping(
            value = {"/api/runs/{runId}/events", "/api/internal/platform/opencode-runtime/runs/{runId}/events"},
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RunEventSsePayload>> events(
            @PathVariable String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "lastEventId", required = false) String lastEventIdQuery,
            ServerWebExchange exchange) {
        String resumeEventId = lastEventId != null ? lastEventId : lastEventIdQuery;
        RunId currentRunId = new RunId(runId);
        String traceId = RuntimeApiSupport.traceId(exchange);
        Flux<ServerSentEvent<RunEventSsePayload>> snapshotEvents = messageRecoveryService == null
                ? Flux.empty()
                : messageRecoveryService.recover(currentRunId, traceId)
                        .map(sseMapper::toTransientSse);
        return Flux.concat(
                snapshotEvents,
                eventStreamService.streamAfter(currentRunId, resumeEventId, DEFAULT_POLL_INTERVAL, DEFAULT_BATCH_LIMIT));
    }

    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        // Run/Diff 编排当前包含阻塞式持久化和 opencode 调用，必须脱离 WebFlux event-loop 执行。
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
