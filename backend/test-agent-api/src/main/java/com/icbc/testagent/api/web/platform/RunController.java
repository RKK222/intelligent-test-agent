package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.opencode.runtime.run.RunApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunDiffApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunHistoryRecoveryResult;
import com.icbc.testagent.opencode.runtime.run.RunHistoryRecoverySource;
import com.icbc.testagent.opencode.runtime.run.RunMessageRecoveryService;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.event.RunEventSseMapper;
import com.icbc.testagent.event.RunEventSsePayload;
import com.icbc.testagent.event.RunEventSseStreamService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 注入运行、diff、SSE 与消息恢复服务，兼容生产构造路径。
     */
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

    /**
     * 测试兼容构造器，允许只验证运行接口而不装配恢复服务。
     */
    public RunController(
            RunApplicationService runService,
            RunDiffApplicationService runDiffService,
            RunEventSseStreamService eventStreamService) {
        this(runService, runDiffService, eventStreamService, null, new RunEventSseMapper());
    }

    /**
     * 启动一次运行，支持 prompt 字符串和 Phase 11 prompt parts 两种输入形态。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/runs",
            "/api/internal/agent/{agentId}/runs"
    })
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> startRun(
            @PathVariable(name = "agentId", required = false) String agentId,
            @Valid @RequestBody RuntimeDtos.StartRunRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> toRunResponse(
                hasAgentId(agentId)
                        ? runService.startRun(userId, agentId, request.toInput(), traceId)
                        : runService.startRun(userId, request.toInput(), traceId)));
    }

    /**
     * 查询运行详情，当前只做 runId 边界转换并委托应用层。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/runs/{runId}",
            "/api/internal/agent/{agentId}/runs/{runId}"
    })
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> getRun(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, ignored -> toRunResponse(runService.getRun(new RunId(runId))));
    }

    /**
     * 取消运行，traceId 用于记录取消事件和后续排障。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/runs/{runId}/cancel",
            "/api/internal/agent/{agentId}/runs/{runId}/cancel"
    })
    public Mono<ApiResponse<RuntimeDtos.RunResponse>> cancelRun(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                toRunResponse(hasAgentId(agentId)
                        ? runService.cancelRun(agentId, new RunId(runId), traceId)
                        : runService.cancelRun(new RunId(runId), traceId)));
    }

    private RuntimeDtos.RunResponse toRunResponse(com.icbc.testagent.domain.run.Run run) {
        return runService.storageMetadata(run.runId())
                .map(metadata -> RuntimeDtos.RunResponse.from(
                        run,
                        metadata.storageMode(),
                        metadata.clientRequestId(),
                        metadata.detailsAvailableUntil()))
                .orElseGet(() -> RuntimeDtos.RunResponse.from(run));
    }

    /**
     * 读取运行 diff，diff 来源和 fallback 逻辑由 RunDiffApplicationService 封装。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/runs/{runId}/diff",
            "/api/internal/agent/{agentId}/runs/{runId}/diff"
    })
    public Mono<ApiResponse<RuntimeDtos.RunDiffResponse>> getDiff(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffResponse.from(hasAgentId(agentId)
                        ? runDiffService.getDiff(agentId, new RunId(runId), traceId)
                        : runDiffService.getDiff(new RunId(runId), traceId)));
    }

    /**
     * 接受运行产生的 diff，并把动作结果包装为统一响应。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/runs/{runId}/diff/accept",
            "/api/internal/agent/{agentId}/runs/{runId}/diff/accept"
    })
    public Mono<ApiResponse<RuntimeDtos.RunDiffActionResponse>> acceptDiff(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffActionResponse.from(hasAgentId(agentId)
                        ? runDiffService.acceptDiff(agentId, new RunId(runId), traceId)
                        : runDiffService.acceptDiff(new RunId(runId), traceId)));
    }

    /**
     * 拒绝运行产生的 diff，并保留与 accept 相同的响应结构。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/runs/{runId}/diff/reject",
            "/api/internal/agent/{agentId}/runs/{runId}/diff/reject"
    })
    public Mono<ApiResponse<RuntimeDtos.RunDiffActionResponse>> rejectDiff(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        return blockingResponse(exchange, traceId ->
                RuntimeDtos.RunDiffActionResponse.from(hasAgentId(agentId)
                        ? runDiffService.rejectDiff(agentId, new RunId(runId), traceId)
                        : runDiffService.rejectDiff(new RunId(runId), traceId)));
    }

    /**
     * 输出 RunEvent SSE，先补发 opencode 当前消息快照，再接续持久化事件流。
     */
    @GetMapping(
            value = {
                    "/api/internal/platform/opencode-runtime/runs/{runId}/events",
                    "/api/internal/agent/{agentId}/runs/{runId}/events"
            },
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RunEventSsePayload>> events(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "lastEventId", required = false) String lastEventIdQuery,
            ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set("X-Accel-Buffering", "no");
        String resumeEventId = lastEventId != null ? lastEventId : lastEventIdQuery;
        RunId currentRunId = new RunId(runId);
        String traceId = RuntimeApiSupport.traceId(exchange);
        boolean redisSummary = runService.eventStorageMode(currentRunId) == RunStorageMode.REDIS_SUMMARY;
        Flux<ServerSentEvent<RunEventSsePayload>> snapshotEvents = messageRecoveryService == null || redisSummary
                ? Flux.empty()
                : (hasAgentId(agentId)
                        ? messageRecoveryService.recover(agentId, currentRunId, traceId)
                        : messageRecoveryService.recover(currentRunId, traceId))
                        .map(sseMapper::toTransientSse);
        return eventStreamService.streamAfterWithSnapshot(
                currentRunId,
                resumeEventId,
                DEFAULT_POLL_INTERVAL,
                DEFAULT_BATCH_LIMIT,
                snapshotEvents);
    }

    /**
     * 查询当前 Run scope 的 root + child session message snapshot；agent-scoped 路径是主入口，旧路径仅兼容。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/runs/{runId}/session-tree/messages",
            "/api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages"
    })
    public Mono<ApiResponse<RuntimeDtos.RunSessionTreeMessagesResponse>> getSessionTreeMessages(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable("runId") String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        RunId currentRunId = new RunId(runId);
        return Mono.fromCallable(() -> {
                    RunHistoryRecoveryResult recovery = messageRecoveryService == null
                            ? RunHistoryRecoveryResult.full(
                                    List.of(), null, RunHistoryRecoverySource.OPENCODE)
                            : (hasAgentId(agentId)
                                    ? messageRecoveryService.recoverHistory(agentId, currentRunId, traceId)
                                    : messageRecoveryService.recoverHistory(currentRunId, traceId))
                                    .block(Duration.ofSeconds(30));
                    List<RunEventSsePayload> allEvents = new ArrayList<>(recovery.events());
                    if (recovery.source() == RunHistoryRecoverySource.OPENCODE) {
                        // legacy OpenCode 快照仍补齐数据库 durable 状态；Redis/摘要来源禁止回查旧事件表。
                        allEvents.addAll(durableSnapshotPayloads(currentRunId));
                    }
                    return ApiResponse.ok(
                            RuntimeDtos.RunSessionTreeMessagesResponse.from(
                                    runId,
                                    allEvents,
                                    recovery.historyRepresentation(),
                                    recovery.replayAvailable(),
                                    recovery.detailsAvailableUntil()),
                            traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<RunEventSsePayload> durableSnapshotPayloads(RunId runId) {
        if (eventStreamService == null) {
            return List.of();
        }
        List<RunEventSsePayload> payloads =
                eventStreamService.snapshotDurablePayloads(runId, 0L, DEFAULT_BATCH_LIMIT);
        return payloads == null ? List.of() : payloads;
    }

    /**
     * 将阻塞式应用服务调用移到 boundedElastic，避免占用 WebFlux event-loop。
     */
    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        // Run/Diff 编排当前包含阻塞式持久化和 opencode 调用，必须脱离 WebFlux event-loop 执行。
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private boolean hasAgentId(String agentId) {
        return agentId != null && !agentId.isBlank();
    }
}
