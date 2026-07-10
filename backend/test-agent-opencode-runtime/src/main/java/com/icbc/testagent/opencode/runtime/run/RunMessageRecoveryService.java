package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.event.RunEventSsePayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * SSE 建连时从 agent projected messages 恢复消息内容；平台本地不再用 run_events 补存消息正文。
 */
@Service
public class RunMessageRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunMessageRecoveryService.class);
    private static final int RECOVERY_MESSAGE_LIMIT = 100;
    private static final int RECENT_RUN_LIMIT = 100;
    private static final String RECOVERY_ORDER = "asc";

    private final RunRepository runRepository;
    private final SessionRepository sessionRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final RunSessionScopeRepository runSessionScopeRepository;
    private final RunRuntimeStore runRuntimeStore;
    private final RunSummaryPersistencePort runSummaryPersistencePort;

    /**
     * 创建消息恢复服务，恢复过程只依赖领域仓储和 agent runtime registry。
     */
    @Autowired
    public RunMessageRecoveryService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunSessionScopeRepository runSessionScopeRepository,
            RunRuntimeStore runRuntimeStore,
            RunSummaryPersistencePort runSummaryPersistencePort) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.runSessionScopeRepository = runSessionScopeRepository;
        this.runRuntimeStore = runRuntimeStore;
        this.runSummaryPersistencePort = runSummaryPersistencePort;
    }

    /**
     * 兼容只注入 legacy scope repository 的构造路径；生产构造器额外注入 Redis 和摘要端口。
     */
    public RunMessageRecoveryService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunSessionScopeRepository runSessionScopeRepository) {
        this(
                runRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runSessionScopeRepository,
                null,
                null);
    }

    /**
     * 兼容旧测试构造路径；未传 scope repository 时恢复逻辑退回 root-only。
     */
    public RunMessageRecoveryService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository) {
        this(
                runRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                null,
                null,
                null);
    }

    /**
     * 异步恢复 Run 的默认 agent projected messages，失败时返回空流而不影响 SSE 建连。
     */
    public Flux<RunEventSsePayload> recover(RunId runId, String traceId) {
        return recoverLegacyOpenCodeRun(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 异步恢复指定 agent 的 projected messages，失败时返回空流而不影响 SSE 建连。
     */
    public Flux<RunEventSsePayload> recover(String agentId, RunId runId, String traceId) {
        return recoverLegacyOpenCodeRun(agentRuntimeRegistry.normalize(agentId), runId, traceId);
    }

    /**
     * 按 Redis 详情、OpenCode 完整会话、PostgreSQL 双摘要顺序恢复单 Run 历史。
     */
    public Mono<RunHistoryRecoveryResult> recoverHistory(RunId runId, String traceId) {
        return recoverHistory(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 恢复指定 agent 的单 Run 历史，并返回完整度与详情过期元数据。
     */
    public Mono<RunHistoryRecoveryResult> recoverHistory(String agentId, RunId runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        return Mono.fromCallable(() -> recoverHistorySync(resolvedAgentId, runId, traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover run history, agentId={}, runId={}, traceId={}",
                            resolvedAgentId, runId.value(), traceId, error);
                    return Mono.just(RunHistoryRecoveryResult.empty());
                });
    }

    /**
     * 异步恢复平台 Session root 下的全量历史 session tree，失败时返回空流。
     */
    public Flux<RunEventSsePayload> recoverSessionTree(SessionId sessionId, String traceId) {
        return recoverLegacyOpenCodeSession(agentRuntimeRegistry.defaultAgentId(), sessionId, traceId);
    }

    /**
     * 异步恢复指定 agent 的 Session 级历史树，包含该 root 下跨 Run 已发现的 child session。
     */
    public Flux<RunEventSsePayload> recoverSessionTree(String agentId, SessionId sessionId, String traceId) {
        return recoverLegacyOpenCodeSession(agentRuntimeRegistry.normalize(agentId), sessionId, traceId);
    }

    /** legacy SSE 初始快照继续排除 user，避免与平台已持久化的乐观 user 消息重复。 */
    private Flux<RunEventSsePayload> recoverLegacyOpenCodeRun(String agentId, RunId runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        return Mono.fromCallable(() -> recoverOpenCodeRunSync(agentId, runId, traceId, false).orElse(List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover legacy Run SSE snapshot, agentId={}, runId={}, traceId={}",
                            agentId, runId.value(), traceId, error);
                    return Mono.just(List.of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    /** legacy Session tree 兼容方法保持旧的 assistant-only 行为；HTTP 历史主入口使用 metadata 方法。 */
    private Flux<RunEventSsePayload> recoverLegacyOpenCodeSession(
            String agentId,
            SessionId sessionId,
            String traceId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        return Mono.fromCallable(() ->
                        recoverOpenCodeSessionSync(agentId, sessionId, traceId, false).orElse(List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover legacy Session snapshot, agentId={}, sessionId={}, traceId={}",
                            agentId, sessionId.value(), traceId, error);
                    return Mono.just(List.of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    /** 按固定来源顺序恢复默认 agent 的 Session 历史树。 */
    public Mono<RunHistoryRecoveryResult> recoverSessionTreeHistory(SessionId sessionId, String traceId) {
        return recoverSessionTreeHistory(agentRuntimeRegistry.defaultAgentId(), sessionId, traceId);
    }

    /** 按固定来源顺序恢复指定 agent 的 Session 历史树。 */
    public Mono<RunHistoryRecoveryResult> recoverSessionTreeHistory(
            String agentId,
            SessionId sessionId,
            String traceId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        return Mono.fromCallable(() -> recoverSessionTreeHistorySync(resolvedAgentId, sessionId, traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover session tree history, agentId={}, sessionId={}, traceId={}",
                            resolvedAgentId, sessionId.value(), traceId, error);
                    return Mono.just(RunHistoryRecoveryResult.empty());
                });
    }

    private RunHistoryRecoveryResult recoverHistorySync(String agentId, RunId runId, String traceId) {
        Optional<RunHistoryRecoveryResult> redis = recoverRedisRun(runId);
        if (redis.isPresent()) {
            return redis.orElseThrow();
        }
        Optional<RunDetailsLocator> locator = findDetailsLocator(runId);
        Optional<List<RunEventSsePayload>> openCode = recoverOpenCodeRun(
                agentId, runId, traceId, locator);
        if (openCode.isPresent()) {
            RunHistoryRecoverySource source = locator
                    .filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY)
                    .map(ignored -> RunHistoryRecoverySource.OPENCODE_REDIS_SUMMARY)
                    .orElse(RunHistoryRecoverySource.OPENCODE);
            return RunHistoryRecoveryResult.full(
                    openCode.orElseThrow(),
                    null,
                    source);
        }
        return recoverRunSummaries(runId, traceId);
    }

    private Optional<RunDetailsLocator> findDetailsLocator(RunId runId) {
        if (runSummaryPersistencePort == null) {
            return Optional.empty();
        }
        try {
            return runSummaryPersistencePort.findDetailsLocator(runId);
        } catch (RuntimeException exception) {
            LOGGER.warn("PostgreSQL run locator unavailable, runId={}", runId.value(), exception);
            return Optional.empty();
        }
    }

    private RunHistoryRecoveryResult recoverSessionTreeHistorySync(
            String agentId,
            SessionId sessionId,
            String traceId) {
        Optional<RedisSessionHistory> redis = recoverRedisSession(sessionId);
        if (redis.isPresent()) {
            RedisSessionHistory recent = redis.orElseThrow();
            // Session 级 Redis 索引受 24 小时 TTL 和 100 Run 上限约束，不能单独宣称覆盖全部历史。
            Optional<List<RunEventSsePayload>> openCode = recoverOpenCodeSession(agentId, sessionId, traceId);
            if (openCode.filter(events -> !events.isEmpty()).isPresent()) {
                return RunHistoryRecoveryResult.full(
                        openCode.orElseThrow(),
                        recent.result().detailsAvailableUntil(),
                        RunHistoryRecoverySource.OPENCODE_REDIS_SUMMARY);
            }
            return mergeRedisSessionWithSummaries(sessionId, traceId, recent);
        }
        Optional<List<RunEventSsePayload>> openCode = recoverOpenCodeSession(agentId, sessionId, traceId);
        if (openCode.isPresent()) {
            return RunHistoryRecoveryResult.full(
                    openCode.orElseThrow(),
                    null,
                    RunHistoryRecoverySource.OPENCODE);
        }
        return recoverSessionSummaries(sessionId, traceId);
    }

    /** Redis 命中后只读取物化快照与 barrier 之后的增量，不再触发任何关系库查询。 */
    private Optional<RunHistoryRecoveryResult> recoverRedisRun(RunId runId) {
        if (runRuntimeStore == null) {
            return Optional.empty();
        }
        try {
            Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(runId)
                    .filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (manifest.isEmpty()) {
                return Optional.empty();
            }
            RunRuntimeManifest resolved = manifest.orElseThrow();
            RunRuntimeReplay replay = runRuntimeStore.replayAfter(
                    runId,
                    0L,
                    RunRuntimeStore.MAX_DURABLE_EVENTS);
            return Optional.of(RunHistoryRecoveryResult.full(
                    redisSnapshotEvents(replay),
                    resolved.detailsExpiresAt(),
                    RunHistoryRecoverySource.REDIS,
                    !resolved.detailsTruncated()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis run history unavailable, runId={}", runId.value(), exception);
            return Optional.empty();
        }
    }

    /** Session Redis 索引按最新优先存储，响应前恢复成 Run 创建时间正序。 */
    private Optional<RedisSessionHistory> recoverRedisSession(SessionId sessionId) {
        if (runRuntimeStore == null) {
            return Optional.empty();
        }
        try {
            List<RunRuntimeManifest> manifests = runRuntimeStore.findRecentBySession(sessionId, RECENT_RUN_LIMIT)
                    .stream()
                    .filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY)
                    .sorted(Comparator.comparing(RunRuntimeManifest::createdAt))
                    .toList();
            if (manifests.isEmpty()) {
                return Optional.empty();
            }
            List<RunEventSsePayload> events = new ArrayList<>();
            for (RunRuntimeManifest manifest : manifests) {
                RunRuntimeReplay replay = runRuntimeStore.replayAfter(
                        manifest.runId(),
                        0L,
                        RunRuntimeStore.MAX_DURABLE_EVENTS);
                events.addAll(redisSnapshotEvents(replay));
            }
            Instant detailsAvailableUntil = manifests.stream()
                    .map(RunRuntimeManifest::detailsExpiresAt)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            RunHistoryRecoveryResult result = RunHistoryRecoveryResult.full(
                    events,
                    detailsAvailableUntil,
                    RunHistoryRecoverySource.REDIS,
                    manifests.stream().noneMatch(RunRuntimeManifest::detailsTruncated));
            Set<String> runIds = manifests.stream()
                    .map(manifest -> manifest.runId().value())
                    .collect(Collectors.toUnmodifiableSet());
            return Optional.of(new RedisSessionHistory(result, runIds));
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis session history unavailable, sessionId={}", sessionId.value(), exception);
            return Optional.empty();
        }
    }

    /**
     * OpenCode 不可用时用关系库摘要补齐 Redis TTL 窗口之外的轮次；近期 Run 的摘要按 summaryKey 去重。
     */
    private RunHistoryRecoveryResult mergeRedisSessionWithSummaries(
            SessionId sessionId,
            String traceId,
            RedisSessionHistory recent) {
        if (runSummaryPersistencePort == null) {
            return recent.result();
        }
        try {
            List<RunConversationSummary> olderSummaries = runSummaryPersistencePort
                    .findSummariesBySessionId(sessionId)
                    .stream()
                    .filter(summary -> recent.runIds().stream()
                            .noneMatch(runId -> summary.summaryKey().startsWith(runId + ":")))
                    .toList();
            if (olderSummaries.isEmpty()) {
                return recent.result();
            }
            List<RunEventSsePayload> combined = new ArrayList<>(summaryEvents(
                    olderSummaries,
                    "session_snapshot:" + sessionId.value(),
                    sessionId.value(),
                    traceId));
            combined.addAll(recent.result().events());
            // TimSort 保持同一时间下 message.updated 在 message.part.updated 之前的既有顺序。
            combined.sort(Comparator.comparing(
                    RunEventSsePayload::occurredAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            return RunHistoryRecoveryResult.redisWithSummaries(
                    combined,
                    recent.result().detailsAvailableUntil());
        } catch (RuntimeException exception) {
            LOGGER.warn("PostgreSQL session summaries unavailable, sessionId={}", sessionId.value(), exception);
            return recent.result();
        }
    }

    /**
     * snapshot 是 barrier 时刻的物化结果；只追加 barrier 之后的 durable 增量以避免同一消息重复回放。
     */
    private List<RunEventSsePayload> redisSnapshotEvents(RunRuntimeReplay replay) {
        List<RunEventSsePayload> events = new ArrayList<>();
        int index = 0;
        for (RunEventDraft draft : replay.snapshot().events()) {
            events.add(redisSnapshotPayload(replay.manifest(), draft, index++));
        }
        replay.durableEvents().stream()
                .filter(event -> event.seq() > replay.snapshot().barrierSeq())
                .map(RunEventSsePayload::from)
                .forEach(events::add);
        return List.copyOf(events);
    }

    private RunEventSsePayload redisSnapshotPayload(
            RunRuntimeManifest manifest,
            RunEventDraft draft,
            int index) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        if (draft.scopeContext() != null) {
            draft.scopeContext().toPayloadMetadata().forEach(payload::putIfAbsent);
        } else {
            // 首次创建远端 session 时 user input 早于 bindRemoteSession 入快照，恢复时用 manifest 补齐 root scope。
            String rootSessionId = manifest.rootRemoteSessionId() == null
                    ? manifest.sessionId().value()
                    : manifest.rootRemoteSessionId();
            payload.putIfAbsent("rootSessionId", rootSessionId);
            payload.putIfAbsent("sessionId", rootSessionId);
            payload.putIfAbsent("isChildSession", false);
        }
        return new RunEventSsePayload(
                "evt_history_redis_" + draft.runId().value() + "_" + index,
                draft.runId().value(),
                0L,
                draft.type().wireName(),
                draft.traceId(),
                draft.occurredAt(),
                Map.copyOf(payload));
    }

    /** OpenCode 完整会话读取失败时返回 empty，让调用方继续降级到关系库摘要。 */
    private Optional<List<RunEventSsePayload>> recoverOpenCodeRun(
            String agentId,
            RunId runId,
            String traceId,
            Optional<RunDetailsLocator> locator) {
        try {
            Optional<RunDetailsLocator> redisSummaryLocator = locator
                    .filter(item -> item.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (redisSummaryLocator.isPresent()) {
                return recoverOpenCodeRunFromLocator(
                        agentId, runId, traceId, redisSummaryLocator.orElseThrow());
            }
            return recoverOpenCodeRunSync(agentId, runId, traceId, true);
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenCode run history unavailable, agentId={}, runId={}, traceId={}",
                    agentId, runId.value(), traceId, exception);
            return Optional.empty();
        }
    }

    /** 新模式 Redis 过期后直接使用无原文锚点定位 OpenCode，避免重读 Run/Session/binding/scope。 */
    private Optional<List<RunEventSsePayload>> recoverOpenCodeRunFromLocator(
            String agentId,
            RunId runId,
            String traceId,
            RunDetailsLocator locator) {
        if (locator.rootRemoteSessionId() == null || locator.executionNodeId() == null) {
            return Optional.empty();
        }
        ExecutionNode node = executionNodeRepository
                .findById(new ExecutionNodeId(locator.executionNodeId()))
                .orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        AgentRuntime runtime = agentRuntimeRegistry.require(agentId);
        return Optional.of(recoverScopes(
                runtime,
                node,
                runId.value(),
                traceId,
                List.of(SnapshotSessionScope.root(locator.rootRemoteSessionId())),
                true));
    }

    /**
     * 同步执行 OpenCode 恢复查询；前置映射缺失表示本来源不可用，远端成功返回空消息仍视为完整历史。
     */
    private Optional<List<RunEventSsePayload>> recoverOpenCodeRunSync(
            String agentId,
            RunId runId,
            String traceId,
            boolean includeUser) {
        Run run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return Optional.empty();
        }
        Session session = sessionRepository.findById(run.sessionId()).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        AgentSessionBinding binding = findAgentBinding(agentId, session, traceId).orElse(null);
        if (binding == null) {
            return Optional.empty();
        }
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId()).orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        AgentRuntime runtime = agentRuntimeRegistry.require(agentId);
        return Optional.of(recoverScopes(
                runtime,
                node,
                runId.value(),
                traceId,
                runScopes(runId, binding.remoteSessionId()),
                includeUser));
    }

    private Optional<List<RunEventSsePayload>> recoverOpenCodeSession(
            String agentId,
            SessionId sessionId,
            String traceId) {
        try {
            return recoverOpenCodeSessionSync(agentId, sessionId, traceId, true);
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenCode session history unavailable, agentId={}, sessionId={}, traceId={}",
                    agentId, sessionId.value(), traceId, exception);
            return Optional.empty();
        }
    }

    /** 同步恢复 OpenCode Session root 子树；没有 legacy scope 记录时降级为 root-only。 */
    private Optional<List<RunEventSsePayload>> recoverOpenCodeSessionSync(
            String agentId,
            SessionId sessionId,
            String traceId,
            boolean includeUser) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        AgentSessionBinding binding = findAgentBinding(agentId, session, traceId).orElse(null);
        if (binding == null) {
            return Optional.empty();
        }
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId()).orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        AgentRuntime runtime = agentRuntimeRegistry.require(agentId);
        return Optional.of(recoverScopes(
                runtime,
                node,
                "session_snapshot:" + sessionId.value(),
                traceId,
                historyScopes(binding.remoteSessionId()),
                includeUser));
    }

    private RunHistoryRecoveryResult recoverRunSummaries(RunId runId, String traceId) {
        if (runSummaryPersistencePort == null) {
            return RunHistoryRecoveryResult.empty();
        }
        try {
            return RunHistoryRecoveryResult.summary(summaryEvents(
                    runSummaryPersistencePort.findSummariesByRunId(runId),
                    runId.value(),
                    "summary:" + runId.value(),
                    traceId));
        } catch (RuntimeException exception) {
            LOGGER.warn("PostgreSQL run summaries unavailable, runId={}", runId.value(), exception);
            return RunHistoryRecoveryResult.empty();
        }
    }

    private RunHistoryRecoveryResult recoverSessionSummaries(SessionId sessionId, String traceId) {
        if (runSummaryPersistencePort == null) {
            return RunHistoryRecoveryResult.empty();
        }
        try {
            return RunHistoryRecoveryResult.summary(summaryEvents(
                    runSummaryPersistencePort.findSummariesBySessionId(sessionId),
                    "session_snapshot:" + sessionId.value(),
                    sessionId.value(),
                    traceId));
        } catch (RuntimeException exception) {
            LOGGER.warn("PostgreSQL session summaries unavailable, sessionId={}", sessionId.value(), exception);
            return RunHistoryRecoveryResult.empty();
        }
    }

    /** 将双摘要映射为前端既有 reducer 可直接消费的 message/part transient 事件。 */
    private List<RunEventSsePayload> summaryEvents(
            List<RunConversationSummary> summaries,
            String snapshotRunId,
            String scopeSessionId,
            String traceId) {
        List<RunEventSsePayload> events = new ArrayList<>();
        int index = 0;
        for (RunConversationSummary summary : summaries == null ? List.<RunConversationSummary>of() : summaries) {
            String messageId = summary.messageId().value();
            String role = summary.role() == SessionMessageRole.USER ? "user" : "assistant";
            LinkedHashMap<String, Object> message = new LinkedHashMap<>();
            message.put("id", messageId);
            message.put("messageID", messageId);
            message.put("messageId", messageId);
            message.put("role", role);
            message.put("text", summary.content());
            message.put("contentKind", "SUMMARY");
            message.put("summaryStatus", summary.summaryStatus().name());
            message.put("summaryVersion", summary.summaryVersion());
            if (summary.remoteMessageId() != null) {
                message.put("remoteMessageId", summary.remoteMessageId());
            }
            LinkedHashMap<String, Object> messagePayload = summaryPayloadBase(scopeSessionId, summary);
            messagePayload.put("messageId", messageId);
            messagePayload.put("role", role);
            messagePayload.put("text", summary.content());
            messagePayload.put("message", Map.copyOf(message));
            events.add(summaryPayload(
                    snapshotRunId,
                    RunEventType.MESSAGE_UPDATED,
                    traceId,
                    summary.createdAt(),
                    index++,
                    messagePayload));

            String partId = "part_summary_" + messageId;
            LinkedHashMap<String, Object> part = new LinkedHashMap<>();
            part.put("id", partId);
            part.put("partID", partId);
            part.put("partId", partId);
            part.put("messageID", messageId);
            part.put("messageId", messageId);
            part.put("type", "text");
            part.put("text", summary.content());
            part.put("contentKind", "SUMMARY");
            part.put("summaryStatus", summary.summaryStatus().name());
            part.put("summaryVersion", summary.summaryVersion());
            LinkedHashMap<String, Object> partPayload = summaryPayloadBase(scopeSessionId, summary);
            partPayload.put("messageID", messageId);
            partPayload.put("messageId", messageId);
            partPayload.put("part", Map.copyOf(part));
            events.add(summaryPayload(
                    snapshotRunId,
                    RunEventType.MESSAGE_PART_UPDATED,
                    traceId,
                    summary.createdAt(),
                    index++,
                    partPayload));
        }
        return List.copyOf(events);
    }

    private LinkedHashMap<String, Object> summaryPayloadBase(
            String scopeSessionId,
            RunConversationSummary summary) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("rootSessionId", scopeSessionId);
        payload.put("sessionId", scopeSessionId);
        payload.put("isChildSession", false);
        payload.put("contentKind", "SUMMARY");
        payload.put("summaryStatus", summary.summaryStatus().name());
        payload.put("summaryVersion", summary.summaryVersion());
        return payload;
    }

    private record RedisSessionHistory(RunHistoryRecoveryResult result, Set<String> runIds) {
        private RedisSessionHistory {
            Objects.requireNonNull(result, "result must not be null");
            runIds = runIds == null ? Set.of() : Set.copyOf(runIds);
        }
    }

    private RunEventSsePayload summaryPayload(
            String snapshotRunId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            int index,
            Map<String, Object> payload) {
        return new RunEventSsePayload(
                "evt_history_summary_" + index,
                snapshotRunId,
                0L,
                type.wireName(),
                traceId,
                occurredAt,
                Map.copyOf(payload));
    }

    private List<RunEventSsePayload> recoverScopes(
            AgentRuntime runtime,
            ExecutionNode node,
            String snapshotRunId,
            String traceId,
            List<SnapshotSessionScope> scopes,
            boolean includeUser) {
        List<RunEventSsePayload> events = new ArrayList<>();
        LinkedHashMap<String, SnapshotSessionScope> scopesBySessionId = new LinkedHashMap<>();
        List<SnapshotSessionScope> orderedScopes = new ArrayList<>();
        for (SnapshotSessionScope scopedSession : scopes) {
            if (!scopesBySessionId.containsKey(scopedSession.sessionId())) {
                scopesBySessionId.put(scopedSession.sessionId(), scopedSession);
                orderedScopes.add(scopedSession);
            }
        }
        for (int index = 0; index < orderedScopes.size(); index++) {
            SnapshotSessionScope scopedSession = orderedScopes.get(index);
            AgentSessionMessagesResult result = loadMessages(runtime, node, scopedSession.sessionId(), traceId);
            if (result != null) {
                List<AgentSessionMessage> messages = result.messages() == null ? List.of() : result.messages();
                events.addAll(toSnapshotEvents(
                        snapshotRunId, traceId, messages, scopedSession, includeUser));
                for (SnapshotSessionScope discovered : discoverChildScopesFromMessages(scopedSession, messages)) {
                    if (!scopesBySessionId.containsKey(discovered.sessionId())) {
                        scopesBySessionId.put(discovered.sessionId(), discovered);
                        orderedScopes.add(discovered);
                    }
                }
            }
        }
        return List.copyOf(events);
    }

    private List<SnapshotSessionScope> runScopes(RunId runId, String rootSessionId) {
        List<RunSessionScopeSession> scopedSessions = runSessionScopeRepository == null
                ? List.of()
                : runSessionScopeRepository.findSessionsByRunId(runId);
        return snapshotScopes(rootSessionId, scopedSessions);
    }

    private List<SnapshotSessionScope> historyScopes(String rootSessionId) {
        List<RunSessionScopeSession> scopedSessions = runSessionScopeRepository == null
                ? List.of()
                : runSessionScopeRepository.findSessionsByRootSessionId(rootSessionId);
        return snapshotScopes(rootSessionId, scopedSessions);
    }

    private List<SnapshotSessionScope> snapshotScopes(
            String rootSessionId,
            List<RunSessionScopeSession> scopedSessions) {
        LinkedHashMap<String, SnapshotSessionScope> scopesBySessionId = new LinkedHashMap<>();
        scopesBySessionId.put(rootSessionId, SnapshotSessionScope.root(rootSessionId));
        for (RunSessionScopeSession scopedSession : scopedSessions) {
            SnapshotSessionScope snapshotScope = SnapshotSessionScope.from(scopedSession);
            scopesBySessionId.putIfAbsent(snapshotScope.sessionId(), snapshotScope);
        }
        return List.copyOf(scopesBySessionId.values());
    }

    private AgentSessionMessagesResult loadMessages(
            AgentRuntime runtime,
            ExecutionNode node,
            String remoteSessionId,
            String traceId) {
        return runtime.sessionMessages(new AgentSessionMessagesCommand(
                        node,
                        remoteSessionId,
                        RECOVERY_MESSAGE_LIMIT,
                        RECOVERY_ORDER,
                        null,
                        traceId))
                .block();
    }

    /**
     * 将 projected messages 转换为 transient SSE snapshot 事件，seq 固定为 0 表示不参与持久化续传。
     */
    private List<RunEventSsePayload> toSnapshotEvents(
            String snapshotRunId,
            String traceId,
            List<AgentSessionMessage> messages,
            SnapshotSessionScope scopedSession,
            boolean includeUser) {
        Instant occurredAt = Instant.now();
        List<RunEventSsePayload> events = new ArrayList<>();
        for (AgentSessionMessage message : messages) {
            Map<String, Object> messagePayload = normalizeMessage(message.message());
            String role = text(messagePayload.get("role"));
            if (!"assistant".equalsIgnoreCase(role) && !(includeUser && "user".equalsIgnoreCase(role))) {
                // legacy SSE 已有平台 user 消息，只回放 assistant；历史 API 则保留 OpenCode 完整 user/assistant。
                continue;
            }
            String messageId = text(messagePayload.get("id"));
            LinkedHashMap<String, Object> messageEventPayload = new LinkedHashMap<>();
            appendScopePayload(messageEventPayload, scopedSession);
            messageEventPayload.put("message", messagePayload);
            events.add(transientPayload(
                    snapshotRunId,
                    RunEventType.MESSAGE_UPDATED,
                    traceId,
                    occurredAt,
                    Map.copyOf(messageEventPayload)));
            for (Map<String, Object> part : message.parts()) {
                Map<String, Object> partPayload = normalizePart(part, messageId);
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                appendScopePayload(payload, scopedSession);
                String partMessageId = text(partPayload.get("messageID"));
                if (partMessageId != null) {
                    payload.put("messageID", partMessageId);
                    payload.put("messageId", partMessageId);
                }
                payload.put("part", partPayload);
                events.add(transientPayload(
                        snapshotRunId,
                        RunEventType.MESSAGE_PART_UPDATED,
                        traceId,
                        occurredAt,
                        Map.copyOf(payload)));
            }
        }
        return List.copyOf(events);
    }

    /**
     * 查询通用 agent 绑定；opencode 旧字段只作为兼容回填来源。
     */
    private Optional<AgentSessionBinding> findAgentBinding(String agentId, Session session, String traceId) {
        Optional<AgentSessionBinding> binding =
                agentSessionBindingRepository.findBySessionIdAndAgentId(session.sessionId(), agentId);
        if (binding.isPresent()) {
            return binding;
        }
        if (AgentRuntimeRegistry.DEFAULT_AGENT_ID.equals(agentRuntimeRegistry.normalize(agentId))
                && session.hasOpencodeSessionMapping()) {
            AgentSessionBinding legacy = new AgentSessionBinding(
                    session.sessionId(),
                    agentId,
                    session.opencodeSessionId(),
                    session.opencodeExecutionNodeId(),
                    session.createdAt(),
                    session.updatedAt(),
                    traceId);
            // 历史读取不能产生关系库写放大；legacy 字段只用于本次只读恢复，不再顺带回填 binding。
            return Optional.of(legacy);
        }
        return Optional.empty();
    }

    /**
     * 构造 transient SSE payload，eventId 使用 live 前缀避免和 durable 事件混淆。
     */
    private RunEventSsePayload transientPayload(
            String snapshotRunId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        return new RunEventSsePayload(
                transientEventId(),
                snapshotRunId,
                0L,
                type.wireName(),
                traceId,
                occurredAt,
                payload);
    }

    /**
     * 规范化 message 字段，补齐 messageID/messageId 和 role 兼容字段。
     */
    private Map<String, Object> normalizeMessage(Map<String, Object> message) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(message);
        String messageId = text(normalized.get("id"));
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        String type = text(normalized.get("type"));
        normalized.putIfAbsent("role", "user".equals(type) ? "user" : "assistant");
        return Map.copyOf(normalized);
    }

    private void appendScopePayload(Map<String, Object> payload, SnapshotSessionScope scopedSession) {
        if (scopedSession == null) {
            return;
        }
        payload.put("rootSessionId", scopedSession.rootSessionId());
        payload.put("sessionId", scopedSession.sessionId());
        if (scopedSession.parentSessionId() != null) {
            payload.put("parentSessionId", scopedSession.parentSessionId());
        }
        payload.put("isChildSession", scopedSession.childSession());
        if (scopedSession.taskMessageId() != null) {
            payload.put("taskMessageId", scopedSession.taskMessageId());
        }
        if (scopedSession.taskPartId() != null) {
            payload.put("taskPartId", scopedSession.taskPartId());
        }
        if (scopedSession.taskCallId() != null) {
            payload.put("taskCallId", scopedSession.taskCallId());
        }
    }

    /**
     * 规范化 part 字段，补齐大小写兼容 ID 字段并继承 messageId。
     */
    private Map<String, Object> normalizePart(Map<String, Object> part, String messageId) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(part);
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        String partId = text(normalized.get("id"));
        if (partId != null) {
            normalized.putIfAbsent("partID", partId);
            normalized.putIfAbsent("partId", partId);
        }
        return Map.copyOf(normalized);
    }

    /**
     * 兼容旧 Run 缺少 scope 记录的场景：从已恢复的 task part metadata 临时补齐 child 查询范围。
     */
    private List<SnapshotSessionScope> discoverChildScopesFromMessages(
            SnapshotSessionScope parentScope,
            List<AgentSessionMessage> messages) {
        LinkedHashMap<String, SnapshotSessionScope> discoveredBySessionId = new LinkedHashMap<>();
        for (AgentSessionMessage message : messages) {
            Map<String, Object> messagePayload = normalizeMessage(message.message());
            if (!"assistant".equalsIgnoreCase(text(messagePayload.get("role")))) {
                continue;
            }
            String messageId = text(messagePayload.get("id"));
            List<Map<String, Object>> parts = message.parts() == null ? List.of() : message.parts();
            for (Map<String, Object> part : parts) {
                Map<String, Object> partPayload = normalizePart(part, messageId);
                metadataSessionId(partPayload)
                        .filter(sessionId -> !parentScope.rootSessionId().equals(sessionId))
                        .filter(sessionId -> !parentScope.sessionId().equals(sessionId))
                        .ifPresent(sessionId -> discoveredBySessionId.putIfAbsent(
                                sessionId,
                                new SnapshotSessionScope(
                                        parentScope.rootSessionId(),
                                        sessionId,
                                        parentScope.sessionId(),
                                        true,
                                        firstText(partPayload, "messageID", "messageId").orElse(messageId),
                                        firstText(partPayload, "partID", "partId", "id").orElse(null),
                                        firstText(partPayload, "callID", "callId").orElse(null))));
            }
        }
        return List.copyOf(discoveredBySessionId.values());
    }

    private Optional<String> metadataSessionId(Map<String, Object> source) {
        return mapValue(source.get("metadata")).flatMap(this::childSessionId)
                .or(() -> mapValue(source.get("state"))
                        .flatMap(state -> mapValue(state.get("metadata")))
                        .flatMap(this::childSessionId));
    }

    private Optional<String> childSessionId(Map<String, Object> source) {
        return firstText(source, "sessionID", "sessionId", "id");
    }

    private Optional<String> firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Optional.of((Map<String, Object>) map);
        }
        return Optional.empty();
    }

    /**
     * 生成 transient live 事件 ID，不进入持久化事件序列。
     */
    private String transientEventId() {
        return "evt_live_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 提取非空字符串字段，空白字符串按缺失处理。
     */
    private String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private record SnapshotSessionScope(
            String rootSessionId,
            String sessionId,
            String parentSessionId,
            boolean childSession,
            String taskMessageId,
            String taskPartId,
            String taskCallId) {

        private static SnapshotSessionScope root(String rootSessionId) {
            return new SnapshotSessionScope(rootSessionId, rootSessionId, null, false, null, null, null);
        }

        private static SnapshotSessionScope from(RunSessionScopeSession session) {
            return new SnapshotSessionScope(
                    session.rootSessionId(),
                    session.sessionId(),
                    session.parentSessionId(),
                    session.childSession(),
                    session.taskMessageId(),
                    session.taskPartId(),
                    session.taskCallId());
        }
    }
}
