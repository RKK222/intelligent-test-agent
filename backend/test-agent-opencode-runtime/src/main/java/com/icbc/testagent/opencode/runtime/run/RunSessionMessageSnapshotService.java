package com.icbc.testagent.opencode.runtime.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 负责把 agent session projected messages 刷新到平台 session_messages，作为消息查询和 Run 终态快照的统一回写入口。
 */
@Service
public class RunSessionMessageSnapshotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunSessionMessageSnapshotService.class);
    private static final int SNAPSHOT_PAGE_LIMIT = 50;
    private static final int SNAPSHOT_MAX_MESSAGES = 200;

    private final RunRepository runRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final ObjectMapper objectMapper;

    /**
     * 注入快照刷新所需端口。该服务只使用 agent facade 投影，不直接依赖 generated SDK。
     */
    public RunSessionMessageSnapshotService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            ObjectMapper objectMapper) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Run 进入终态后刷新 assistant 快照，并把 token/cost 回写到 runs 表。
     */
    public boolean persistRunSnapshot(String agentId, Run run, String traceId) {
        Objects.requireNonNull(run, "run must not be null");
        Optional<Session> session = sessionRepository.findById(run.sessionId());
        if (session.isEmpty()) {
            return false;
        }
        return refreshSnapshot(agentId, session.get(), run, traceId);
    }

    /**
     * 查询会话消息前尝试刷新远端投影；失败时调用方继续使用 DB 快照 fallback。
     */
    public boolean refreshSessionSnapshot(String agentId, Session session, String traceId) {
        return refreshSnapshot(agentId, session, null, traceId);
    }

    private boolean refreshSnapshot(String agentId, Session session, Run run, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        try {
            Optional<AgentSessionBinding> binding = findAgentBinding(resolvedAgentId, session, traceId);
            if (binding.isEmpty()) {
                return false;
            }
            Optional<ExecutionNode> node = executionNodeRepository.findById(binding.get().executionNodeId());
            if (node.isEmpty()) {
                return false;
            }
            AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
            SnapshotUsage runUsage = refreshSnapshotPages(
                    runtime,
                    node.get(),
                    binding.get().remoteSessionId(),
                    resolvedAgentId,
                    session,
                    run,
                    traceId);
            if (runUsage == null) {
                return false;
            }
            if (run != null && runUsage.hasValue()) {
                runRepository.save(run.withUsage(runUsage.tokenUsage(), runUsage.costUsd()));
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to refresh agent session snapshot, sessionId={}, runId={}, agentId={}, traceId={}",
                    session.sessionId().value(),
                    run == null ? null : run.runId().value(),
                    resolvedAgentId,
                    traceId,
                    exception);
            return false;
        }
    }

    private SnapshotUsage refreshSnapshotPages(
            AgentRuntime runtime,
            ExecutionNode node,
            String remoteSessionId,
            String agentId,
            Session session,
            Run run,
            String traceId) {
        SnapshotUsage runUsage = SnapshotUsage.empty();
        Instant runUsageAt = null;
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        int loaded = 0;
        while (loaded < SNAPSHOT_MAX_MESSAGES) {
            int pageLimit = Math.min(SNAPSHOT_PAGE_LIMIT, SNAPSHOT_MAX_MESSAGES - loaded);
            AgentSessionMessagesResult result = runtime.sessionMessages(new AgentSessionMessagesCommand(
                            node,
                            remoteSessionId,
                            pageLimit,
                            "asc",
                            cursor,
                            traceId))
                    .block();
            if (result == null) {
                return null;
            }
            loaded += result.messages().size();
            for (AgentSessionMessage message : result.messages()) {
                Optional<SnapshotUsage> usage = upsertAssistantMessage(agentId, session.sessionId(), run, message, traceId);
                Instant messageCreatedAt = projectedCreatedAt(message).orElse(Instant.now());
                if (usage.isPresent() && usage.get().hasValue()
                        && (runUsageAt == null || messageCreatedAt.isAfter(runUsageAt))) {
                    runUsage = usage.get();
                    runUsageAt = messageCreatedAt;
                }
            }
            String nextCursor = result.nextCursor();
            if (nextCursor == null || result.messages().isEmpty()) {
                break;
            }
            if (!seenCursors.add(nextCursor)) {
                LOGGER.warn(
                        "Detected repeated agent session snapshot cursor, stop paging; sessionId={}, runId={}, agentId={}, traceId={}",
                        session.sessionId().value(),
                        run == null ? null : run.runId().value(),
                        agentId,
                        traceId);
                break;
            }
            cursor = nextCursor;
        }
        return runUsage;
    }

    private Optional<AgentSessionBinding> findAgentBinding(String agentId, Session session, String traceId) {
        Optional<AgentSessionBinding> binding =
                agentSessionBindingRepository.findBySessionIdAndAgentId(session.sessionId(), agentId);
        if (binding.isPresent()) {
            return binding;
        }
        if (agentRuntimeRegistry.defaultAgentId().equals(agentId) && session.hasOpencodeSessionMapping()) {
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

    private Optional<SnapshotUsage> upsertAssistantMessage(
            String agentId,
            SessionId sessionId,
            Run run,
            AgentSessionMessage projected,
            String traceId) {
        if (!isAssistant(projected.message())) {
            return Optional.empty();
        }
        String serializedParts = partsJson(projected.parts()).orElse(null);
        Optional<String> projectedContent = content(projected);
        if (projectedContent.isEmpty() && serializedParts == null) {
            return Optional.empty();
        }
        String remoteMessageId = firstText(projected.message(), "messageID", "messageId", "id")
                .orElseGet(() -> syntheticRemoteMessageId(sessionId, projected, projectedContent.orElse(""), serializedParts));
        Optional<SessionMessage> existing = sessionMessageRepository.findBySessionIdAndRemoteMessageId(sessionId, remoteMessageId);
        Instant now = Instant.now();
        Instant createdAt = existing.map(SessionMessage::createdAt)
                .orElseGet(() -> projectedCreatedAt(projected).orElse(now));
        SnapshotUsage usage = usage(projected);
        SessionMessage message = new SessionMessage(
                existing.map(SessionMessage::messageId).orElseGet(() -> new SessionMessageId(RuntimeIdGenerator.messageId())),
                sessionId,
                SessionMessageRole.ASSISTANT,
                projectedContent.orElse(""),
                createdAt,
                traceId,
                run == null ? existing.map(SessionMessage::runId).orElse(null) : run.runId(),
                agentId,
                remoteMessageId,
                serializedParts,
                usage.tokenUsage(),
                usage.costUsd(),
                now);
        sessionMessageRepository.save(message);
        return Optional.of(usage);
    }

    private String syntheticRemoteMessageId(
            SessionId sessionId,
            AgentSessionMessage projected,
            String projectedContent,
            String serializedParts) {
        // 上游历史投影偶发缺少 message id；用稳定内容指纹补一个内部幂等键，避免每次刷新都插入重复 assistant 快照。
        String seed = String.join("\n",
                sessionId.value(),
                firstText(projected.message(), "role", "type").orElse("assistant"),
                projectedCreatedAt(projected).map(Instant::toString).orElse(""),
                projectedContent == null ? "" : projectedContent,
                serializedParts == null ? "" : serializedParts);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return "synthetic:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private Optional<Instant> projectedCreatedAt(AgentSessionMessage projected) {
        Map<String, Object> message = projected.message();
        Map<String, Object> time = mapValue(message.get("time")).orElse(Map.of());
        return longValue(time, "created", "createdAt", "created_at")
                .or(() -> longValue(message, "createdAt", "created_at"))
                .map(this::epochInstant);
    }

    private Instant epochInstant(long value) {
        return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    }

    private boolean isAssistant(Map<String, Object> message) {
        Optional<String> role = firstText(message, "role", "type").map(value -> value.trim().toLowerCase(java.util.Locale.ROOT));
        return role.filter(value -> value.equals("assistant")).isPresent();
    }

    private Optional<String> content(AgentSessionMessage message) {
        List<String> texts = new ArrayList<>();
        for (Map<String, Object> part : message.parts()) {
            String partType = firstText(part, "type", "partType").orElse("");
            if ("text".equalsIgnoreCase(partType)) {
                textFromValue(part.get("text"))
                        .or(() -> textFromValue(part.get("content")))
                        .or(() -> textFromValue(part.get("delta")))
                        .ifPresent(texts::add);
            }
        }
        if (!texts.isEmpty()) {
            return Optional.of(String.join("\n", texts));
        }
        return textFromValue(message.message().get("content"))
                .or(() -> textFromValue(message.message().get("text")))
                .or(() -> textFromValue(message.message().get("summary")));
    }

    private Optional<String> partsJson(List<Map<String, Object>> parts) {
        if (parts == null || parts.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.writeValueAsString(parts));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private SnapshotUsage usage(AgentSessionMessage projected) {
        SnapshotUsage usage = usage(projected.message());
        for (Map<String, Object> part : projected.parts()) {
            if (!usage.hasValue()) {
                usage = usage(part);
            }
        }
        return usage;
    }

    private SnapshotUsage usage(Map<String, Object> source) {
        Map<String, Object> usage = mapValue(source.get("tokens"))
                .or(() -> mapValue(source.get("usage")))
                .orElse(source);
        Map<String, Object> cache = mapValue(usage.get("cache")).orElse(Map.of());
        TokenUsage tokenUsage = new TokenUsage(
                longValue(usage, "input", "inputTokens", "promptTokens", "prompt_tokens").orElse(null),
                longValue(usage, "output", "outputTokens", "completionTokens", "completion_tokens").orElse(null),
                longValue(usage, "reasoning", "reasoningTokens", "reasoning_tokens").orElse(null),
                longValue(usage, "cacheRead", "cache_read", "cacheReadTokens").or(() -> longValue(cache, "read", "readTokens")).orElse(null),
                longValue(usage, "cacheWrite", "cache_write", "cacheWriteTokens").or(() -> longValue(cache, "write", "writeTokens")).orElse(null));
        BigDecimal costUsd = decimalValue(source, "costUsd", "cost_usd", "cost")
                .or(() -> decimalValue(usage, "costUsd", "cost_usd", "cost"))
                .orElse(null);
        return new SnapshotUsage(tokenUsage, costUsd);
    }

    private Optional<String> firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Optional<String> value = textFromValue(map.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> textFromValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = stringMap(raw);
            return firstText(map, "value", "text", "content");
        }
        return Optional.empty();
    }

    private Optional<Map<String, Object>> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        return Optional.of(stringMap(raw));
    }

    private Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String stringKey && value != null) {
                map.put(stringKey, value);
            }
        });
        return Map.copyOf(map);
    }

    private Optional<Long> longValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return Optional.of(number.longValue());
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Optional.of(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> decimalValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof BigDecimal decimal) {
                return Optional.of(decimal);
            }
            if (value instanceof Number number) {
                return Optional.of(new BigDecimal(number.toString()));
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Optional.of(new BigDecimal(text));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private record SnapshotUsage(TokenUsage tokenUsage, BigDecimal costUsd) {

        private SnapshotUsage {
            tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        }

        private static SnapshotUsage empty() {
            return new SnapshotUsage(TokenUsage.empty(), null);
        }

        private boolean hasValue() {
            return !tokenUsage.isEmpty() || costUsd != null;
        }
    }
}
