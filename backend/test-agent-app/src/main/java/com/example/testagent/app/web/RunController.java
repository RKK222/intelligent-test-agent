package com.example.testagent.app.web;

import com.example.testagent.app.run.RunApplicationService;
import com.example.testagent.app.run.RunDiffApplicationService;
import com.example.testagent.common.api.ApiResponse;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.event.RunEventSsePayload;
import com.example.testagent.event.RunEventSseStreamService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

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

    public RunController(
            RunApplicationService runService,
            RunDiffApplicationService runDiffService,
            RunEventSseStreamService eventStreamService) {
        this.runService = runService;
        this.runDiffService = runDiffService;
        this.eventStreamService = eventStreamService;
    }

    @PostMapping("/api/runs")
    public ApiResponse<RuntimeDtos.RunResponse> startRun(
            @Valid @RequestBody RuntimeDtos.StartRunRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.RunResponse.from(runService.startRun(
                new SessionId(request.sessionId()), request.prompt(), traceId)), traceId);
    }

    @GetMapping("/api/runs/{runId}")
    public ApiResponse<RuntimeDtos.RunResponse> getRun(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.RunResponse.from(runService.getRun(new RunId(runId))), traceId);
    }

    @PostMapping("/api/runs/{runId}/cancel")
    public ApiResponse<RuntimeDtos.RunResponse> cancelRun(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.RunResponse.from(runService.cancelRun(new RunId(runId), traceId)), traceId);
    }

    @GetMapping("/api/runs/{runId}/diff")
    public ApiResponse<RuntimeDtos.RunDiffResponse> getDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.RunDiffResponse.from(runDiffService.getDiff(new RunId(runId), traceId)), traceId);
    }

    @PostMapping("/api/runs/{runId}/diff/accept")
    public ApiResponse<RuntimeDtos.RunDiffActionResponse> acceptDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(
                RuntimeDtos.RunDiffActionResponse.from(runDiffService.acceptDiff(new RunId(runId), traceId)),
                traceId);
    }

    @PostMapping("/api/runs/{runId}/diff/reject")
    public ApiResponse<RuntimeDtos.RunDiffActionResponse> rejectDiff(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(
                RuntimeDtos.RunDiffActionResponse.from(runDiffService.rejectDiff(new RunId(runId), traceId)),
                traceId);
    }

    @GetMapping(value = "/api/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RunEventSsePayload>> events(
            @PathVariable String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return eventStreamService.streamAfter(new RunId(runId), lastEventId, DEFAULT_POLL_INTERVAL, DEFAULT_BATCH_LIMIT);
    }
}
