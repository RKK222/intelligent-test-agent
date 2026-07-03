package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 当前 Run 的 session scope 路由器。opencode-client 只输出 raw/mapped draft，本类在 runtime 层维护 root/child 归属。
 */
class RunSessionScopeRouter {

    private static final String SOURCE_TASK_PART = "TASK_PART";
    private static final String SOURCE_SESSION_EVENT = "SESSION_EVENT";
    private static final String SOURCE_BOOTSTRAP = "BOOTSTRAP";

    private final RunSessionScopeRepository repository;
    private final RunSessionScopeRuntimeCache runtimeCache;

    RunSessionScopeRouter(RunSessionScopeRepository repository) {
        this(repository, RunSessionScopeRuntimeCache.disabled());
    }

    RunSessionScopeRouter(RunSessionScopeRepository repository, RunSessionScopeRuntimeCache runtimeCache) {
        this.repository = repository;
        this.runtimeCache = runtimeCache == null ? RunSessionScopeRuntimeCache.disabled() : runtimeCache;
    }

    /**
     * 将单条 mapped draft 归入 root 或 child scope；child 终态不会再派生 Run 终态。
     */
    List<RunEventDraft> route(RunEventScopeContext rootScope, RunEventDraft draft) {
        return route(rootScope, draft, true);
    }

    private List<RunEventDraft> route(RunEventScopeContext rootScope, RunEventDraft draft, boolean useRuntimeCache) {
        Objects.requireNonNull(rootScope, "rootScope must not be null");
        Objects.requireNonNull(draft, "draft must not be null");
        if (!rootScope.runId().equals(draft.runId())) {
            throw new IllegalArgumentException("draft runId must match root scope");
        }

        List<RunEventDraft> routed = new ArrayList<>();
        Optional<TaskChildCandidate> taskCandidate = taskChildCandidate(rootScope, draft.payload());
        String eventSessionId = eventSessionId(draft.payload()).orElse(null);
        String dedupSessionId = taskCandidate.map(TaskChildCandidate::sessionId)
                .orElse(eventSessionId == null ? rootScope.rootSessionId() : eventSessionId);
        if (useRuntimeCache && rawEventId(draft.payload())
                .filter(rawEventId -> !runtimeCache.claimRawEvent(rootScope.runId(), dedupSessionId, rawEventId))
                .isPresent()) {
            return List.of();
        }

        routed.addAll(discoverBootstrapChildren(rootScope, draft));
        if (eventSessionId == null && shouldDropGlobalNonRunEvent(draft)) {
            return List.copyOf(routed);
        }

        if (taskCandidate.isPresent()) {
            ChildRoute child = discoverChild(rootScope, draft, taskCandidate.get().toDiscovery());
            routed.addAll(child.discoveryEvents());
            routed.addAll(drainPending(rootScope, child));
            routed.add(recontextualize(draft, child.scopeContext()));
            return List.copyOf(routed);
        }

        if (eventSessionId == null || rootScope.rootSessionId().equals(eventSessionId)) {
            RunEventScopeContext scope = rootContext(rootScope, draft);
            routed.add(recontextualize(draft, scope));
            return List.copyOf(routed);
        }

        Optional<RunSessionScopeSession> known = findSession(rootScope.runId(), eventSessionId);
        if (known.isPresent()) {
            if (isRunTerminal(draft)) {
                return List.copyOf(routed);
            }
            routed.add(recontextualize(draft, childContext(rootScope, known.get(), scopeVersion(rootScope.runId()))));
            return List.copyOf(routed);
        }

        Optional<String> parentSessionId = parentSessionId(draft.payload());
        if (parentSessionId.filter(parent -> isKnownParent(rootScope, parent)).isPresent()) {
            ChildRoute child = discoverChild(rootScope, draft, new ChildDiscovery(
                    eventSessionId,
                    parentSessionId.orElse(rootScope.rootSessionId()),
                    SOURCE_SESSION_EVENT,
                    null,
                    null,
                    null,
                    Map.of("rawType", valueAsText(draft.payload().get("rawType")).orElse(""))));
            if (isRunTerminal(draft)) {
                return List.copyOf(routed);
            }
            routed.addAll(child.discoveryEvents());
            routed.addAll(drainPending(rootScope, child));
            routed.add(recontextualize(draft, child.scopeContext()));
            return List.copyOf(routed);
        }

        // 显式属于未知 session 的事件不能按 root 入库；非终态 session 事件短时进入 Redis pending。
        if (isRunTerminal(draft) || explicitSessionEvent(draft.payload())) {
            if (useRuntimeCache && eventSessionId != null && !isRunTerminal(draft)) {
                runtimeCache.appendPending(eventSessionId, draft);
            }
            return List.copyOf(routed);
        }
        routed.add(recontextualize(draft, rootContext(rootScope, draft)));
        return List.copyOf(routed);
    }

    private List<RunEventDraft> discoverBootstrapChildren(RunEventScopeContext rootScope, RunEventDraft draft) {
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
                        ChildRoute child = discoverChild(rootScope, draft, discovery);
                        discoveryEvents.addAll(child.discoveryEvents());
                        discoveryEvents.addAll(drainPending(rootScope, child));
                    });
        }
        return List.copyOf(discoveryEvents);
    }

    private ChildRoute discoverChild(RunEventScopeContext rootScope, RunEventDraft draft, ChildDiscovery discovery) {
        Optional<RunSessionScopeSession> existing = findSession(rootScope.runId(), discovery.sessionId());
        long version = scopeVersion(rootScope.runId());
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
            return new ChildRoute(childContext(rootScope, session, version), List.of());
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
        runtimeCache.recordScopeSession(scope, scopeSession);
        if (repository != null) {
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

    private List<RunEventDraft> drainPending(RunEventScopeContext rootScope, ChildRoute child) {
        if (child.discoveryEvents().isEmpty()) {
            return List.of();
        }
        List<RunEventDraft> pending = runtimeCache.drainPending(rootScope.runId(), child.scopeContext().sessionId());
        if (pending.isEmpty()) {
            return List.of();
        }
        List<RunEventDraft> drained = new ArrayList<>();
        for (RunEventDraft pendingDraft : pending) {
            drained.addAll(route(rootScope, pendingDraft, false));
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
                scopeVersion(draft.runId()),
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

    private boolean isKnownParent(RunEventScopeContext rootScope, String parentSessionId) {
        if (rootScope.rootSessionId().equals(parentSessionId)) {
            return true;
        }
        return findSession(rootScope.runId(), parentSessionId).isPresent();
    }

    private Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
        Optional<RunSessionScopeSession> cached = runtimeCache.findScopeSession(runId, sessionId);
        if (cached.isPresent()) {
            return cached;
        }
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findSession(runId, sessionId);
    }

    private long scopeVersion(RunId runId) {
        if (repository == null) {
            return 1L;
        }
        return Math.max(2L, repository.findSessionsByRunId(runId).size() + 1L);
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
}
