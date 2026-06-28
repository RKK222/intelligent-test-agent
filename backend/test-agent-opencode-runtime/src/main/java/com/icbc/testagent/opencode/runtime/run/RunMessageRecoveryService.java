package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.session.Session;
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

    /**
     * 创建消息恢复服务，恢复过程只依赖领域仓储和 agent runtime registry。
     */
    public RunMessageRecoveryService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
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
        AgentSessionMessagesResult result = runtime.sessionMessages(new AgentSessionMessagesCommand(
                        node,
                        binding.remoteSessionId(),
                        RECOVERY_MESSAGE_LIMIT,
                        RECOVERY_ORDER,
                        null,
                        traceId))
                .block();
        return result == null ? List.of() : toSnapshotEvents(runId, traceId, result.messages());
    }

    /**
     * 将 projected messages 转换为 transient SSE snapshot 事件，seq 固定为 0 表示不参与持久化续传。
     */
    private List<RunEventSsePayload> toSnapshotEvents(
            RunId runId,
            String traceId,
            List<AgentSessionMessage> messages) {
        Instant occurredAt = Instant.now();
        List<RunEventSsePayload> events = new ArrayList<>();
        for (AgentSessionMessage message : messages) {
            Map<String, Object> messagePayload = normalizeMessage(message.message());
            if (!"assistant".equalsIgnoreCase(text(messagePayload.get("role")))) {
                // 平台已在 Run 启动前保存 user 消息；重复回放 user part 会被前端当作 assistant part 拼进正文。
                continue;
            }
            String messageId = text(messagePayload.get("id"));
            events.add(transientPayload(
                    runId,
                    RunEventType.MESSAGE_UPDATED,
                    traceId,
                    occurredAt,
                    Map.of("message", messagePayload)));
            for (Map<String, Object> part : message.parts()) {
                Map<String, Object> partPayload = normalizePart(part, messageId);
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                String partMessageId = text(partPayload.get("messageID"));
                if (partMessageId != null) {
                    payload.put("messageID", partMessageId);
                    payload.put("messageId", partMessageId);
                }
                payload.put("part", partPayload);
                events.add(transientPayload(
                        runId,
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
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        return new RunEventSsePayload(
                transientEventId(),
                runId.value(),
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
}
