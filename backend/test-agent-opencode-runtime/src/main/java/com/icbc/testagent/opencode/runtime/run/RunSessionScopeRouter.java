package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前 Run 的 session scope 路由器。opencode-client 只输出 raw/mapped draft，本类在 runtime 层维护 root/child 归属。
 */
class RunSessionScopeRouter {

    private static final String SOURCE_TASK_PART = "TASK_PART";
    private static final String SOURCE_SESSION_EVENT = "SESSION_EVENT";
    private static final String SOURCE_BOOTSTRAP = "BOOTSTRAP";

    private final RunSessionScopeRepository repository;
    private final RunSessionScopeRuntimeCache runtimeCache;
    private final RunRuntimeStore runRuntimeStore;
    private final Map<PendingTaskKey, Deque<PendingTaskCandidate>> pendingTaskCandidates = new ConcurrentHashMap<>();
    private final Map<RunId, SubscriptionScopeState> subscriptionStates = new ConcurrentHashMap<>();

    RunSessionScopeRouter(RunSessionScopeRepository repository) {
        this(repository, RunSessionScopeRuntimeCache.disabled(), null);
    }

    RunSessionScopeRouter(RunSessionScopeRepository repository, RunSessionScopeRuntimeCache runtimeCache) {
        this(repository, runtimeCache, null);
    }

    RunSessionScopeRouter(
            RunSessionScopeRepository repository,
            RunSessionScopeRuntimeCache runtimeCache,
            RunRuntimeStore runRuntimeStore) {
        this.repository = repository;
        this.runtimeCache = runtimeCache == null ? RunSessionScopeRuntimeCache.disabled() : runtimeCache;
        this.runRuntimeStore = runRuntimeStore;
    }

    /**
     * 将单条 mapped draft 归入 root 或 child scope；child 终态不会再派生 Run 终态。
     */
    List<RunEventDraft> route(RunEventScopeContext rootScope, RunEventDraft draft) {
        return route(rootScope, draft, RunStorageMode.LEGACY_FULL);
    }

    /**
     * 按 Run 固定 storageMode 路由事件。REDIS_SUMMARY 只使用订阅态与 Redis cache，禁止查询或写入 scope 表。
     */
    List<RunEventDraft> route(
            RunEventScopeContext rootScope,
            RunEventDraft draft,
            RunStorageMode storageMode) {
        return route(rootScope, draft, storageMode, true);
    }

    private List<RunEventDraft> route(
            RunEventScopeContext rootScope,
            RunEventDraft draft,
            RunStorageMode storageMode,
            boolean useRuntimeCache) {
        Objects.requireNonNull(rootScope, "rootScope must not be null");
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        if (!rootScope.runId().equals(draft.runId())) {
            throw new IllegalArgumentException("draft runId must match root scope");
        }
        state(rootScope, storageMode);

        List<RunEventDraft> routed = new ArrayList<>();
        Optional<TaskChildCandidate> taskCandidate = taskChildCandidate(rootScope, draft.payload());
        String eventSessionId = eventSessionId(draft.payload()).orElse(null);
        String dedupSessionId = eventSessionId == null ? rootScope.rootSessionId() : eventSessionId;
        if (useRuntimeCache && rawEventId(draft.payload())
                .filter(rawEventId -> !claimRawEvent(
                        rootScope.runId(), dedupSessionId, rawEventId, storageMode))
                .isPresent()) {
            return List.of();
        }
        if (taskCandidate.isEmpty()) {
            recordPendingTaskCandidate(rootScope, draft, eventSessionId, storageMode);
        }

        routed.addAll(discoverBootstrapChildren(rootScope, draft, storageMode));
        if (eventSessionId == null && shouldDropGlobalNonRunEvent(draft)) {
            return List.copyOf(routed);
        }

        if (taskCandidate.isPresent()) {
            ChildRoute child = discoverChild(rootScope, draft, taskCandidate.get().toDiscovery(), storageMode);
            routed.addAll(child.discoveryEvents());
            routed.addAll(drainPending(rootScope, child, storageMode));
            // task part 是 root assistant 的导航入口；child discovery 只建立子会话索引，不能把 root part 改成 child scope。
            routed.add(recontextualize(draft, rootContext(rootScope, draft)));
            return List.copyOf(routed);
        }

        if (eventSessionId == null || rootScope.rootSessionId().equals(eventSessionId)) {
            RunEventScopeContext scope = rootContext(rootScope, draft);
            routed.add(recontextualize(draft, scope));
            return List.copyOf(routed);
        }

        Optional<RunSessionScopeSession> known = findSession(rootScope, eventSessionId, storageMode);
        if (known.isPresent()) {
            if (isRunTerminal(draft)) {
                return List.copyOf(routed);
            }
            routed.add(recontextualize(draft, childContext(
                    rootScope,
                    known.get(),
                    state(rootScope).scopeVersion())));
            return List.copyOf(routed);
        }

        Optional<String> parentSessionId = parentSessionId(draft.payload());
        if (parentSessionId.filter(parent -> isKnownParent(rootScope, parent, storageMode)).isPresent()) {
            String parent = parentSessionId.orElse(rootScope.rootSessionId());
            PendingTaskCandidate pendingTask = pollPendingTaskCandidate(rootScope.runId(), parent).orElse(null);
            Map<String, Object> metadata = childSessionMetadata(draft.payload());
            if (pendingTask != null) {
                metadata.putIfAbsent("taskMessageId", pendingTask.messageId());
                metadata.putIfAbsent("taskPartId", pendingTask.partId());
                metadata.putIfAbsent("taskCallId", pendingTask.callId());
            }
            metadata.putIfAbsent("rawType", valueAsText(draft.payload().get("rawType")).orElse(""));
            ChildRoute child = discoverChild(rootScope, draft, new ChildDiscovery(
                    eventSessionId,
                    parent,
                    SOURCE_SESSION_EVENT,
                    pendingTask == null ? null : pendingTask.messageId(),
                    pendingTask == null ? null : pendingTask.partId(),
                    pendingTask == null ? null : pendingTask.callId(),
                    Map.copyOf(metadata)), storageMode);
            if (isRunTerminal(draft)) {
                return List.copyOf(routed);
            }
            routed.addAll(child.discoveryEvents());
            routed.addAll(drainPending(rootScope, child, storageMode));
            routed.add(recontextualize(draft, child.scopeContext()));
            return List.copyOf(routed);
        }

        // 显式属于未知 session 的事件不能按 root 入库；非终态 session 事件短时进入 Redis pending。
        if (isRunTerminal(draft) || explicitSessionEvent(draft.payload())) {
            if (useRuntimeCache && eventSessionId != null && !isRunTerminal(draft)) {
                if (storageMode == RunStorageMode.REDIS_SUMMARY) {
                    requireRuntimeStore().appendPending(eventSessionId, draft);
                } else {
                    runtimeCache.appendPending(eventSessionId, draft);
                }
            }
            return List.copyOf(routed);
        }
        routed.add(recontextualize(draft, rootContext(rootScope, draft)));
        return List.copyOf(routed);
    }

    private List<RunEventDraft> discoverBootstrapChildren(
            RunEventScopeContext rootScope,
            RunEventDraft draft,
            RunStorageMode storageMode) {
        if (!"session.children".equals(valueAsText(draft.payload().get("rawType")).orElse(null))) {
            return List.of();
        }
        Collection<?> children = collectionValue(draft.payload().get("children"))
                .or(() -> mapValue(draft.payload().get("rawPayload"))
                        .flatMap(raw -> mapValue(raw.get("properties")))
                        .flatMap(properties -> collectionValue(properties.get("children"))))
                .orElse(List.of());
        List<RunEventDraft> discoveryEvents = new ArrayList<>();
        for (Object childValue : children) {
            mapValue(childValue).flatMap(childMap -> childSessionId(childMap)
                            .filter(sessionId -> !rootScope.rootSessionId().equals(sessionId))
                            .map(sessionId -> new ChildDiscovery(
                                    sessionId,
                                    parentSessionId(childMap).orElse(rootScope.rootSessionId()),
                                    SOURCE_BOOTSTRAP,
                                    null,
                                    null,
                                    null,
                                    Map.of("rawType", "session.children"))))
                    .ifPresent(discovery -> {
                        ChildRoute child = discoverChild(rootScope, draft, discovery, storageMode);
                        discoveryEvents.addAll(child.discoveryEvents());
                        discoveryEvents.addAll(drainPending(rootScope, child, storageMode));
                    });
        }
        return List.copyOf(discoveryEvents);
    }

    private ChildRoute discoverChild(
            RunEventScopeContext rootScope,
            RunEventDraft draft,
            ChildDiscovery discovery,
            RunStorageMode storageMode) {
        Optional<RunSessionScopeSession> existing = findSession(rootScope, discovery.sessionId(), storageMode);
        SubscriptionScopeState state = state(rootScope);
        long version = state.nextVersionForNewSession();
        String parentSessionId = discovery.parentSessionId() == null
                ? rootScope.rootSessionId()
                : discovery.parentSessionId();
        RunEventScopeContext scopeContext = new RunEventScopeContext(
                rootScope.runId(),
                rootScope.rootSessionId(),
                discovery.sessionId(),
                parentSessionId,
                true,
                discovery.taskMessageId(),
                discovery.taskPartId(),
                discovery.taskCallId(),
                version,
                true);
        if (existing.isPresent()) {
            RunSessionScopeSession session = existing.get();
            return new ChildRoute(childContext(rootScope, session, state.scopeVersion()), List.of());
        }

        Instant now = draft.occurredAt();
        RunSessionScope scope = new RunSessionScope(
                rootScope.runId(),
                rootScope.rootSessionId(),
                version,
                draft.traceId(),
                now,
                now,
                Map.of("source", discovery.source()));
        RunSessionScopeSession scopeSession = new RunSessionScopeSession(
                rootScope.runId(),
                discovery.sessionId(),
                rootScope.rootSessionId(),
                parentSessionId,
                true,
                discovery.source(),
                discovery.taskMessageId(),
                discovery.taskPartId(),
                discovery.taskCallId(),
                draft.traceId(),
                now,
                now,
                discovery.metadata());
        state.record(scopeSession, version);
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            requireRuntimeStore().saveScope(scope, scopeSession);
        } else {
            runtimeCache.recordScopeSession(scope, scopeSession);
        }
        if (storageMode == RunStorageMode.LEGACY_FULL && repository != null) {
            repository.upsertScope(scope);
            repository.upsertSession(scopeSession);
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", discovery.source());
        payload.putAll(scopeContext.toPayloadMetadata());
        payload.putAll(discovery.metadata());
        RunEventDraft discovered = new RunEventDraft(
                rootScope.runId(),
                RunEventType.SESSION_CHILD_DISCOVERED,
                draft.traceId(),
                draft.occurredAt(),
                payload,
                scopeContext);
        LinkedHashMap<String, Object> scopeUpdatedPayload = new LinkedHashMap<>();
        scopeUpdatedPayload.put("source", discovery.source());
        scopeUpdatedPayload.put("action", "SESSION_ADDED");
        scopeUpdatedPayload.put("changedSessionId", discovery.sessionId());
        scopeUpdatedPayload.putAll(scopeContext.toPayloadMetadata());
        scopeUpdatedPayload.putAll(discovery.metadata());
        RunEventDraft scopeUpdated = new RunEventDraft(
                rootScope.runId(),
                RunEventType.SESSION_SCOPE_UPDATED,
                draft.traceId(),
                draft.occurredAt(),
                scopeUpdatedPayload,
                scopeContext);
        return new ChildRoute(scopeContext, List.of(discovered, scopeUpdated));
    }

    private List<RunEventDraft> drainPending(
            RunEventScopeContext rootScope,
            ChildRoute child,
            RunStorageMode storageMode) {
        if (child.discoveryEvents().isEmpty()) {
            return List.of();
        }
        List<RunEventDraft> pending = storageMode == RunStorageMode.REDIS_SUMMARY
                ? requireRuntimeStore().drainPending(rootScope.runId(), child.scopeContext().sessionId())
                : runtimeCache.drainPending(rootScope.runId(), child.scopeContext().sessionId());
        if (pending.isEmpty()) {
            return List.of();
        }
        List<RunEventDraft> drained = new ArrayList<>();
        for (RunEventDraft pendingDraft : pending) {
            drained.addAll(route(rootScope, pendingDraft, storageMode, false));
        }
        return List.copyOf(drained);
    }

    private RunEventDraft recontextualize(RunEventDraft draft, RunEventScopeContext scopeContext) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        payload.putAll(scopeContext.toPayloadMetadata());
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                payload,
                scopeContext);
    }

    private RunEventScopeContext rootContext(RunEventScopeContext rootScope, RunEventDraft draft) {
        return new RunEventScopeContext(
                rootScope.runId(),
                rootScope.rootSessionId(),
                rootScope.rootSessionId(),
                null,
                false,
                null,
                null,
                null,
                state(rootScope).scopeVersion(),
                true);
    }

    private RunEventScopeContext childContext(
            RunEventScopeContext rootScope,
            RunSessionScopeSession session,
            long scopeVersion) {
        return new RunEventScopeContext(
                rootScope.runId(),
                rootScope.rootSessionId(),
                session.sessionId(),
                session.parentSessionId(),
                true,
                session.taskMessageId(),
                session.taskPartId(),
                session.taskCallId(),
                scopeVersion,
                true);
    }

    private Optional<TaskChildCandidate> taskChildCandidate(RunEventScopeContext rootScope, Map<String, Object> payload) {
        Map<String, Object> part = mapValue(payload.get("part")).orElse(Map.of());
        Optional<String> sessionId = metadataSessionId(rootScope, part)
                .or(() -> metadataSessionId(rootScope, payload));
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        String messageId = firstText(payload, "messageID", "messageId")
                .or(() -> firstText(part, "messageID", "messageId"))
                .orElse(null);
        String partId = firstText(payload, "partID", "partId")
                .or(() -> firstText(part, "partID", "partId", "id"))
                .orElse(null);
        String callId = firstText(payload, "callID", "callId")
                .or(() -> firstText(part, "callID", "callId"))
                .orElse(null);
        return Optional.of(new TaskChildCandidate(sessionId.get(), messageId, partId, callId));
    }

    private void recordPendingTaskCandidate(
            RunEventScopeContext rootScope,
            RunEventDraft draft,
            String eventSessionId,
            RunStorageMode storageMode) {
        pendingTaskCandidate(draft.payload()).ifPresent(candidate -> {
            String parentSessionId = eventSessionId == null ? rootScope.rootSessionId() : eventSessionId;
            if (!isKnownParent(rootScope, parentSessionId, storageMode)) {
                return;
            }
            PendingTaskKey key = new PendingTaskKey(rootScope.runId(), parentSessionId);
            Deque<PendingTaskCandidate> queue =
                    pendingTaskCandidates.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            synchronized (queue) {
                boolean exists = queue.stream().anyMatch(existing ->
                        Objects.equals(existing.partId(), candidate.partId())
                                || (candidate.callId() != null && Objects.equals(existing.callId(), candidate.callId())));
                if (!exists) {
                    queue.addLast(candidate);
                    while (queue.size() > 20) {
                        queue.removeFirst();
                    }
                }
            }
        });
    }

    private Optional<PendingTaskCandidate> pendingTaskCandidate(Map<String, Object> payload) {
        if (!valueAsText(payload.get("rawType")).filter("message.part.updated"::equals).isPresent()) {
            return Optional.empty();
        }
        Map<String, Object> part = mapValue(payload.get("part")).orElse(Map.of());
        if (!"tool".equals(valueAsText(part.get("type")).orElse(null))
                || !"task".equals(valueAsText(part.get("tool")).orElse(null))) {
            return Optional.empty();
        }
        String partId = firstText(payload, "partID", "partId")
                .or(() -> firstText(part, "partID", "partId", "id"))
                .orElse(null);
        if (partId == null) {
            return Optional.empty();
        }
        String messageId = firstText(payload, "messageID", "messageId")
                .or(() -> firstText(part, "messageID", "messageId"))
                .orElse(null);
        String callId = firstText(payload, "callID", "callId")
                .or(() -> firstText(part, "callID", "callId"))
                .orElse(null);
        return Optional.of(new PendingTaskCandidate(messageId, partId, callId));
    }

    private Optional<PendingTaskCandidate> pollPendingTaskCandidate(RunId runId, String parentSessionId) {
        PendingTaskKey key = new PendingTaskKey(runId, parentSessionId);
        Deque<PendingTaskCandidate> queue = pendingTaskCandidates.get(key);
        if (queue == null) {
            return Optional.empty();
        }
        synchronized (queue) {
            PendingTaskCandidate candidate = queue.pollFirst();
            if (queue.isEmpty()) {
                pendingTaskCandidates.remove(key, queue);
            }
            return Optional.ofNullable(candidate);
        }
    }

    private Map<String, Object> childSessionMetadata(Map<String, Object> payload) {
        Map<String, Object> info = mapValue(payload.get("info"))
                .or(() -> mapValue(payload.get("rawPayload"))
                        .flatMap(raw -> mapValue(raw.get("properties")))
                        .flatMap(properties -> mapValue(properties.get("info"))))
                .orElse(Map.of());
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        firstText(info, "agent", "agentName").ifPresent(agent -> {
            metadata.put("agent", agent);
            metadata.put("agentName", agent);
        });
        firstText(info, "title").map(this::normalizeSubagentTitle)
                .filter(title -> !title.isBlank())
                .ifPresent(title -> metadata.put("title", title));
        return metadata;
    }

    private String normalizeSubagentTitle(String title) {
        return title.replaceFirst("\\s+\\(@[^)]+\\s+subagent\\)$", "").trim();
    }

    private Optional<String> metadataSessionId(RunEventScopeContext rootScope, Map<String, Object> source) {
        Optional<String> sessionId = mapValue(source.get("metadata")).flatMap(this::childSessionId)
                .or(() -> mapValue(source.get("state"))
                        .flatMap(state -> mapValue(state.get("metadata")))
                        .flatMap(this::childSessionId));
        return sessionId.filter(value -> !rootScope.rootSessionId().equals(value));
    }

    private Optional<String> eventSessionId(Map<String, Object> payload) {
        return mapValue(payload.get("rawPayload"))
                .flatMap(raw -> mapValue(raw.get("properties")))
                .flatMap(properties -> deepFirstText(properties, Set.of("sessionID", "sessionId")))
                .or(() -> firstText(payload, "sessionID"));
    }

    private Optional<String> parentSessionId(Map<String, Object> payload) {
        return mapValue(payload.get("rawPayload"))
                .flatMap(raw -> mapValue(raw.get("properties")))
                .flatMap(properties -> deepFirstText(properties, Set.of("parentID", "parentId", "parentSessionId")))
                .or(() -> firstText(payload, "parentID", "parentId", "parentSessionId"));
    }

    private Optional<String> childSessionId(Map<String, Object> source) {
        return firstText(source, "sessionID", "sessionId", "id");
    }

    private Optional<String> rawEventId(Map<String, Object> payload) {
        return valueAsText(payload.get("rawEventId"))
                .filter(rawEventId -> !"unknown".equals(rawEventId));
    }

    private boolean isKnownParent(
            RunEventScopeContext rootScope,
            String parentSessionId,
            RunStorageMode storageMode) {
        if (rootScope.rootSessionId().equals(parentSessionId)) {
            return true;
        }
        return findSession(rootScope, parentSessionId, storageMode).isPresent();
    }

    private Optional<RunSessionScopeSession> findSession(
            RunEventScopeContext rootScope,
            String sessionId,
            RunStorageMode storageMode) {
        SubscriptionScopeState state = state(rootScope);
        Optional<RunSessionScopeSession> inSubscription = state.find(sessionId);
        if (inSubscription.isPresent()) {
            return inSubscription;
        }
        Optional<RunSessionScopeSession> cached = storageMode == RunStorageMode.REDIS_SUMMARY
                ? requireRuntimeStore().findScopeSession(rootScope.runId(), sessionId)
                : runtimeCache.findScopeSession(rootScope.runId(), sessionId);
        if (cached.isPresent()) {
            state.recordExisting(cached.get());
            return cached;
        }
        if (storageMode != RunStorageMode.LEGACY_FULL || repository == null) {
            return Optional.empty();
        }
        Optional<RunSessionScopeSession> persisted = repository.findSession(rootScope.runId(), sessionId);
        persisted.ifPresent(state::recordExisting);
        return persisted;
    }

    /** 终态或启动失败后释放本机订阅状态，Redis/DB 事实仍由各自存储保留。 */
    void finishRun(RunId runId) {
        if (runId == null) {
            return;
        }
        subscriptionStates.remove(runId);
        pendingTaskCandidates.keySet().removeIf(key -> key.runId().equals(runId));
    }

    private boolean claimRawEvent(
            RunId runId,
            String sessionId,
            String rawEventId,
            RunStorageMode storageMode) {
        return storageMode == RunStorageMode.REDIS_SUMMARY
                ? requireRuntimeStore().claimRawEvent(runId, sessionId, rawEventId)
                : runtimeCache.claimRawEvent(runId, sessionId, rawEventId);
    }

    private RunRuntimeStore requireRuntimeStore() {
        if (runRuntimeStore == null) {
            throw new IllegalStateException("REDIS_SUMMARY scope routing requires RunRuntimeStore");
        }
        return runRuntimeStore;
    }

    private SubscriptionScopeState state(RunEventScopeContext rootScope) {
        return subscriptionStates.compute(rootScope.runId(), (runId, current) -> {
            if (current == null) {
                return new SubscriptionScopeState(rootScope.rootSessionId(), 1L);
            }
            if (!current.rootSessionId().equals(rootScope.rootSessionId())) {
                throw new IllegalStateException("run root session changed during subscription");
            }
            return current;
        });
    }

    private SubscriptionScopeState state(
            RunEventScopeContext rootScope,
            RunStorageMode storageMode) {
        return subscriptionStates.compute(rootScope.runId(), (runId, current) -> {
            if (current == null) {
                long initialVersion = storageMode == RunStorageMode.REDIS_SUMMARY
                        ? requireRuntimeStore().scopeVersion(runId)
                        : 1L;
                return new SubscriptionScopeState(rootScope.rootSessionId(), initialVersion);
            }
            if (!current.rootSessionId().equals(rootScope.rootSessionId())) {
                throw new IllegalStateException("run root session changed during subscription");
            }
            return current;
        });
    }

    private boolean isRunTerminal(RunEventDraft draft) {
        return draft.type() == RunEventType.RUN_SUCCEEDED || draft.type() == RunEventType.RUN_FAILED;
    }

    private boolean explicitSessionEvent(Map<String, Object> payload) {
        return eventSessionId(payload).isPresent();
    }

    private boolean shouldDropGlobalNonRunEvent(RunEventDraft draft) {
        if (draft.type() != RunEventType.OPENCODE_EVENT_UNKNOWN) {
            return false;
        }
        String rawType = valueAsText(draft.payload().get("rawType")).orElse("");
        return "heartbeat".equals(rawType)
                || "server.heartbeat".equals(rawType)
                || rawType.startsWith("heartbeat.")
                || rawType.startsWith("tui.")
                || rawType.startsWith("pty.")
                || rawType.startsWith("workspace.")
                || rawType.startsWith("worktree.")
                || rawType.startsWith("installation.")
                || rawType.startsWith("plugin.")
                || rawType.startsWith("catalog.");
    }

    private Optional<String> firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Optional<String> value = valueAsText(source.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> deepFirstText(Object source, Set<String> keys) {
        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && keys.contains(key)) {
                    Optional<String> direct = valueAsText(entry.getValue());
                    if (direct.isPresent()) {
                        return direct;
                    }
                }
                Optional<String> nested = deepFirstText(entry.getValue(), keys);
                if (nested.isPresent()) {
                    return nested;
                }
            }
            return Optional.empty();
        }
        if (source instanceof Collection<?> collection) {
            for (Object item : collection) {
                Optional<String> nested = deepFirstText(item, keys);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> valueAsText(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
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

    private Optional<Collection<?>> collectionValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return Optional.of(collection);
        }
        return Optional.empty();
    }

    private record ChildDiscovery(
            String sessionId,
            String parentSessionId,
            String source,
            String taskMessageId,
            String taskPartId,
            String taskCallId,
            Map<String, Object> metadata) {
    }

    private record ChildRoute(RunEventScopeContext scopeContext, List<RunEventDraft> discoveryEvents) {
    }

    /** 单次订阅内的已知 session 与 scopeVersion，稳定事件路由不再反查数据库。 */
    private static final class SubscriptionScopeState {

        private final String rootSessionId;
        private final Map<String, RunSessionScopeSession> sessions = new LinkedHashMap<>();
        private long scopeVersion;

        private SubscriptionScopeState(String rootSessionId, long scopeVersion) {
            this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId must not be null");
            this.scopeVersion = Math.max(1L, scopeVersion);
        }

        private String rootSessionId() {
            return rootSessionId;
        }

        private synchronized Optional<RunSessionScopeSession> find(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        private synchronized long nextVersionForNewSession() {
            return scopeVersion + 1L;
        }

        private synchronized void record(RunSessionScopeSession session, long version) {
            if (sessions.putIfAbsent(session.sessionId(), session) == null) {
                scopeVersion = Math.max(scopeVersion, version);
            }
        }

        private synchronized void recordExisting(RunSessionScopeSession session) {
            if (sessions.putIfAbsent(session.sessionId(), session) == null) {
                scopeVersion = Math.max(scopeVersion, sessions.size() + 1L);
            }
        }

        private synchronized long scopeVersion() {
            return scopeVersion;
        }
    }

    private record TaskChildCandidate(String sessionId, String messageId, String partId, String callId) {

        private ChildDiscovery toDiscovery() {
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            if (messageId != null) {
                metadata.put("messageId", messageId);
            }
            if (partId != null) {
                metadata.put("partId", partId);
            }
            if (callId != null) {
                metadata.put("callId", callId);
            }
            return new ChildDiscovery(
                    sessionId,
                    null,
                    SOURCE_TASK_PART,
                    messageId,
                    partId,
                    callId,
                    Map.copyOf(metadata));
        }
    }

    private record PendingTaskKey(RunId runId, String parentSessionId) {
    }

    private record PendingTaskCandidate(String messageId, String partId, String callId) {
    }
}
