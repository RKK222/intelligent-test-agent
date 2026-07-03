package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.event.RunEventSsePayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final String RECOVERY_ORDER = "asc";

    private final RunRepository runRepository;
    private final SessionRepository sessionRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final RunSessionScopeRepository runSessionScopeRepository;

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
            RunSessionScopeRepository runSessionScopeRepository) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.runSessionScopeRepository = runSessionScopeRepository;
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
                null);
    }

    /**
     * 异步恢复 Run 的默认 agent projected messages，失败时返回空流而不影响 SSE 建连。
     */
    public Flux<RunEventSsePayload> recover(RunId runId, String traceId) {
        return recover(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 异步恢复指定 agent 的 projected messages，失败时返回空流而不影响 SSE 建连。
     */
    public Flux<RunEventSsePayload> recover(String agentId, RunId runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        return Mono.fromCallable(() -> recoverSync(resolvedAgentId, runId, traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover run messages from agent, agentId={}, runId={}, traceId={}",
                            resolvedAgentId, runId.value(), traceId, error);
                    return Mono.just(List.of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 异步恢复平台 Session root 下的全量历史 session tree，失败时返回空流。
     */
    public Flux<RunEventSsePayload> recoverSessionTree(SessionId sessionId, String traceId) {
        return recoverSessionTree(agentRuntimeRegistry.defaultAgentId(), sessionId, traceId);
    }

    /**
     * 异步恢复指定 agent 的 Session 级历史树，包含该 root 下跨 Run 已发现的 child session。
     */
    public Flux<RunEventSsePayload> recoverSessionTree(String agentId, SessionId sessionId, String traceId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        return Mono.fromCallable(() -> recoverSessionTreeSync(resolvedAgentId, sessionId, traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover session tree messages from agent, agentId={}, sessionId={}, traceId={}",
                            resolvedAgentId, sessionId.value(), traceId, error);
                    return Mono.just(List.of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 同步执行恢复查询，缺失 Run/Session/节点或未绑定远端 session 时返回空列表。
     */
    private List<RunEventSsePayload> recoverSync(String agentId, RunId runId, String traceId) {
        AgentRuntime runtime = agentRuntimeRegistry.require(agentId);
        Run run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return List.of();
        }
        Session session = sessionRepository.findById(run.sessionId()).orElse(null);
        if (session == null) {
            return List.of();
        }
        AgentSessionBinding binding = findAgentBinding(agentId, session, traceId).orElse(null);
        if (binding == null) {
            return List.of();
        }
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId()).orElse(null);
        if (node == null) {
            return List.of();
        }
        return recoverScopes(runtime, node, runId.value(), traceId, runScopes(runId, binding.remoteSessionId()));
    }

    /**
     * 同步恢复 Session root 下的历史子树；没有 scope 记录时降级为 root-only。
     */
    private List<RunEventSsePayload> recoverSessionTreeSync(String agentId, SessionId sessionId, String traceId) {
        AgentRuntime runtime = agentRuntimeRegistry.require(agentId);
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return List.of();
        }
        AgentSessionBinding binding = findAgentBinding(agentId, session, traceId).orElse(null);
        if (binding == null) {
            return List.of();
        }
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId()).orElse(null);
        if (node == null) {
            return List.of();
        }
        return recoverScopes(
                runtime,
                node,
                "session_snapshot:" + sessionId.value(),
                traceId,
                historyScopes(binding.remoteSessionId()));
    }

    private List<RunEventSsePayload> recoverScopes(
            AgentRuntime runtime,
            ExecutionNode node,
            String snapshotRunId,
            String traceId,
            List<SnapshotSessionScope> scopes) {
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
                events.addAll(toSnapshotEvents(snapshotRunId, traceId, messages, scopedSession));
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
            SnapshotSessionScope scopedSession) {
        Instant occurredAt = Instant.now();
        List<RunEventSsePayload> events = new ArrayList<>();
        for (AgentSessionMessage message : messages) {
            Map<String, Object> messagePayload = normalizeMessage(message.message());
            if (!"assistant".equalsIgnoreCase(text(messagePayload.get("role")))) {
                // 平台已在 Run 启动前保存 user 消息；重复回放 user part 会被前端当作 assistant part 拼进正文。
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
            return Optional.of(agentSessionBindingRepository.save(legacy));
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
