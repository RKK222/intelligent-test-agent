package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeInput;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalProjectionPending;
import com.icbc.testagent.domain.run.RunTerminalProjectionResult;
import com.icbc.testagent.domain.run.RunTerminalRetry;
import com.icbc.testagent.domain.run.RunTerminalRetryStore;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.opencode.runtime.run.summary.RunConversationSummarizer;
import com.icbc.testagent.opencode.runtime.run.summary.RunMessageSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 Redis 终态运行态投影为 PostgreSQL 控制面锚点和 USER/ASSISTANT 双摘要。
 * 本服务只读取物化状态，不遍历或持久化原始 Stream。
 */
@Service
public class RunTerminalProjectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunTerminalProjectionService.class);

    /**
     * REDIS_SUMMARY 的锚点在 RUN_STARTED 事件之后写入，因此关系型状态版本固定为 1。
     * 运行中的 CANCELLING 等 Redis 状态不会写 PostgreSQL，终态 CAS 不能使用 Redis 当前版本倒推。
     */
    private static final long INITIAL_ANCHOR_STATUS_VERSION = 1L;

    private final RunRuntimeStore runtimeStore;
    private final RunSummaryPersistencePort persistencePort;
    private final RunConversationSummarizer summarizer;
    private final RunTerminalRetryStore retryStore;
    private final Clock clock;

    /** 保留给既有单元测试的兼容构造；生产装配始终注入 Redis 重试端口。 */
    public RunTerminalProjectionService(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort,
            RunConversationSummarizer summarizer) {
        this(runtimeStore, persistencePort, summarizer, null, Clock.systemUTC());
    }

    /** 注入 Redis 运行态、关系型终态事务、安全重试端口和统一 UTC 时钟。 */
    @Autowired
    public RunTerminalProjectionService(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort,
            RunConversationSummarizer summarizer,
            RunTerminalRetryStore retryStore,
            Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort must not be null");
        this.summarizer = Objects.requireNonNull(summarizer, "summarizer must not be null");
        this.retryStore = retryStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 终态事件已先写 Redis；关系型 CAS 始终针对启动时写入的固定锚点版本。 */
    public RunTerminalProjectionResult project(
            RunId runId,
            RunStatus terminalStatus,
            String terminalSource,
            String terminalReasonCode,
            String safeErrorMessage,
            boolean remoteStopConfirmed,
            String traceId) {
        Optional<RunTerminalProjectionPending> pending = runtimeStore.findTerminalProjectionPending(runId);
        long terminalProjectionVersion = pending.map(RunTerminalProjectionPending::version).orElse(0L);
        return projectInternal(
                runId,
                terminalStatus,
                terminalSource,
                terminalReasonCode,
                safeErrorMessage,
                remoteStopConfirmed,
                traceId,
                terminalProjectionVersion);
    }

    /** 恢复路径完全使用终态 Lua 原子保存的 outbox 元数据，成功或版本冲突后确认同一 version。 */
    public RunTerminalProjectionResult project(RunTerminalProjectionPending pending) {
        Objects.requireNonNull(pending, "pending must not be null");
        return projectInternal(
                pending.runId(),
                pending.status(),
                pending.terminalSource(),
                pending.terminalReasonCode(),
                pending.safeErrorMessage(),
                pending.remoteStopConfirmed(),
                pending.traceId(),
                pending.version());
    }

    private RunTerminalProjectionResult projectInternal(
            RunId runId,
            RunStatus terminalStatus,
            String terminalSource,
            String terminalReasonCode,
            String safeErrorMessage,
            boolean remoteStopConfirmed,
            String traceId,
            long terminalProjectionVersion) {
        if (terminalStatus == null || !terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("terminalStatus must be terminal");
        }
        RunRuntimeManifest manifest = runtimeStore.findManifest(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run manifest 不存在"));
        RunRuntimeInput input = runtimeStore.findInput(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run input 不存在"));
        RunRuntimeReplay replay = runtimeStore.replayAfter(runId, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS);
        VisibleAssistant assistant = visibleAssistant(replay.snapshot().events());
        var generated = summarizer.summarize(input.prompt(), assistant.text());
        List<RunConversationSummary> summaries = generated.messages().stream()
                .map(message -> summary(manifest, input, assistant, message))
                .toList();
        RunTerminalProjection projection = new RunTerminalProjection(
                runId,
                manifest.sessionId(),
                terminalStatus,
                INITIAL_ANCHOR_STATUS_VERSION,
                terminalSource,
                terminalReasonCode,
                safeErrorMessage(safeErrorMessage),
                remoteStopConfirmed,
                manifest.lastSeq(),
                manifest.detailsExpiresAt(),
                manifest.rootRemoteSessionId(),
                runtimeStore.diffCounts(runId),
                assistant.messageId(),
                assistant.partId(),
                assistant.tokenUsage(),
                assistant.costUsd(),
                traceId,
                manifest.updatedAt(),
                manifest.agentId(),
                ConversationSourceType.MANUAL,
                null,
                manifest.userId(),
                summaries);
        try {
            RunTerminalProjectionResult result = persistencePort.persistTerminal(projection);
            if ((result == RunTerminalProjectionResult.APPLIED
                    || result == RunTerminalProjectionResult.VERSION_CONFLICT)
                    && terminalProjectionVersion > 0) {
                runtimeStore.ackTerminalProjection(runId, terminalProjectionVersion);
            }
            return result;
        } catch (RuntimeException databaseFailure) {
            if (retryStore == null) {
                throw databaseFailure;
            }
            // 只保存已经过摘要器清洗的投影；数据库异常正文不进入 Redis、日志或后续 PostgreSQL。
            retryStore.save(RunTerminalRetry.pending(
                    projection, clock.instant(), terminalProjectionVersion));
            LOGGER.warn(
                    "Run terminal projection pending database retry, runId={}, errorType={}",
                    runId.value(),
                    databaseFailure.getClass().getSimpleName());
            return RunTerminalProjectionResult.TERMINAL_PENDING_DB;
        }
    }

    /** safe_error_message 也必须经过与双摘要相同的敏感信息清洗，且遵守关系型字段长度预算。 */
    private String safeErrorMessage(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String sanitized = summarizer.summarize("", source).assistant().content();
        int maxCodePoints = 1_024;
        if (sanitized.codePointCount(0, sanitized.length()) <= maxCodePoints) {
            return sanitized;
        }
        int end = sanitized.offsetByCodePoints(0, maxCodePoints - 1);
        return sanitized.substring(0, end) + "…";
    }

    private RunConversationSummary summary(
            RunRuntimeManifest manifest,
            RunRuntimeInput input,
            VisibleAssistant assistant,
            RunMessageSummary message) {
        boolean user = message.role() == com.icbc.testagent.domain.session.SessionMessageRole.USER;
        return new RunConversationSummary(
                user
                        ? RunSummaryIdentifiers.user(manifest.runId(), input.messageId())
                        : RunSummaryIdentifiers.assistant(manifest.runId()),
                message.role(),
                message.content(),
                manifest.runId().value() + ':' + message.role().name() + ":v" + message.summaryVersion(),
                message.summaryVersion(),
                com.icbc.testagent.domain.run.RunSummaryStatus.valueOf(message.summaryStatus().name()),
                user ? input.createdAt() : manifest.updatedAt(),
                user ? null : assistant.messageId());
    }

    private VisibleAssistant visibleAssistant(List<RunEventDraft> events) {
        List<String> texts = new ArrayList<>();
        String lastMessageId = null;
        String lastPartId = null;
        TokenUsage tokenUsage = TokenUsage.empty();
        BigDecimal costUsd = null;
        for (RunEventDraft event : events == null ? List.<RunEventDraft>of() : events) {
            if (childEvent(event)) {
                continue;
            }
            Map<String, Object> payload = event.payload();
            if (event.type() == RunEventType.ASSISTANT_MESSAGE_DELTA) {
                text(payload, "text", "delta", "content").ifPresent(texts::add);
                lastMessageId = firstText(payload, "messageId", "messageID").orElse(lastMessageId);
                continue;
            }
            if (event.type() == RunEventType.MESSAGE_PART_UPDATED) {
                Map<String, Object> part = map(payload.get("part")).orElse(payload);
                String type = firstText(part, "type", "partType").orElse("text");
                if ("text".equalsIgnoreCase(type)) {
                    text(part, "text", "content", "delta").ifPresent(texts::add);
                    lastMessageId = firstText(part, "messageID", "messageId")
                            .or(() -> firstText(payload, "messageID", "messageId"))
                            .orElse(lastMessageId);
                    lastPartId = firstText(part, "id", "partID", "partId")
                            .or(() -> firstText(payload, "partID", "partId"))
                            .orElse(lastPartId);
                }
                continue;
            }
            if (event.type() == RunEventType.MESSAGE_PART_DELTA
                    && "text".equalsIgnoreCase(firstText(payload, "field").orElse("text"))) {
                text(payload, "text", "delta", "content").ifPresent(texts::add);
                lastMessageId = firstText(payload, "messageID", "messageId").orElse(lastMessageId);
                lastPartId = firstText(payload, "partID", "partId").orElse(lastPartId);
                continue;
            }
            if (event.type() == RunEventType.MESSAGE_UPDATED) {
                Map<String, Object> message = map(payload.get("message"))
                        .or(() -> map(payload.get("info")))
                        .orElse(payload);
                if ("assistant".equalsIgnoreCase(firstText(message, "role", "type").orElse(""))) {
                    text(message, "text", "content", "summary").ifPresent(texts::add);
                    lastMessageId = firstText(message, "id", "messageID", "messageId").orElse(lastMessageId);
                    Usage latestUsage = usage(message);
                    if (!latestUsage.tokenUsage().isEmpty()) {
                        tokenUsage = latestUsage.tokenUsage();
                    }
                    if (latestUsage.costUsd() != null) {
                        costUsd = latestUsage.costUsd();
                    }
                }
            }
        }
        return new VisibleAssistant(String.join("\n", texts), lastMessageId, lastPartId, tokenUsage, costUsd);
    }

    private Usage usage(Map<String, Object> source) {
        Map<String, Object> usage = map(source.get("tokens"))
                .or(() -> map(source.get("usage")))
                .orElse(source);
        Map<String, Object> cache = map(usage.get("cache")).orElse(Map.of());
        TokenUsage tokenUsage = new TokenUsage(
                longValue(usage, "input", "inputTokens", "promptTokens", "prompt_tokens").orElse(null),
                longValue(usage, "output", "outputTokens", "completionTokens", "completion_tokens").orElse(null),
                longValue(usage, "reasoning", "reasoningTokens", "reasoning_tokens").orElse(null),
                longValue(usage, "cacheRead", "cache_read", "cacheReadTokens")
                        .or(() -> longValue(cache, "read", "readTokens"))
                        .orElse(null),
                longValue(usage, "cacheWrite", "cache_write", "cacheWriteTokens")
                        .or(() -> longValue(cache, "write", "writeTokens"))
                        .orElse(null));
        BigDecimal costUsd = decimalValue(source, "costUsd", "cost_usd", "cost")
                .or(() -> decimalValue(usage, "costUsd", "cost_usd", "cost"))
                .orElse(null);
        return new Usage(tokenUsage, costUsd);
    }

    private Optional<Long> longValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            try {
                if (value instanceof Number number) {
                    long parsed = number.longValue();
                    return parsed < 0 ? Optional.empty() : Optional.of(parsed);
                }
                if (value instanceof String text && !text.isBlank()) {
                    long parsed = Long.parseLong(text);
                    return parsed < 0 ? Optional.empty() : Optional.of(parsed);
                }
            } catch (NumberFormatException ignored) {
                // 上游可选 usage 字段格式异常时忽略该字段，不影响终态摘要落库。
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> decimalValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            try {
                if (value instanceof BigDecimal decimal) {
                    return decimal.signum() < 0 ? Optional.empty() : Optional.of(decimal);
                }
                if (value instanceof Number number) {
                    BigDecimal parsed = new BigDecimal(number.toString());
                    return parsed.signum() < 0 ? Optional.empty() : Optional.of(parsed);
                }
                if (value instanceof String text && !text.isBlank()) {
                    BigDecimal parsed = new BigDecimal(text);
                    return parsed.signum() < 0 ? Optional.empty() : Optional.of(parsed);
                }
            } catch (NumberFormatException ignored) {
                // 与 token 一样，非关键计费字段格式异常按缺失处理。
            }
        }
        return Optional.empty();
    }

    private boolean childEvent(RunEventDraft event) {
        if (event.scopeContext() != null && event.scopeContext().childSession()) {
            return true;
        }
        return Boolean.TRUE.equals(event.payload().get("isChildSession"));
    }

    private Optional<String> text(Map<String, Object> source, String... keys) {
        return firstText(source, keys).map(String::trim).filter(value -> !value.isEmpty());
    }

    private Optional<String> firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private Optional<Map<String, Object>> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String text) {
                result.put(text, item);
            }
        });
        return Optional.of(Map.copyOf(result));
    }

    private record VisibleAssistant(
            String text,
            String messageId,
            String partId,
            TokenUsage tokenUsage,
            BigDecimal costUsd) {
    }

    private record Usage(TokenUsage tokenUsage, BigDecimal costUsd) {
    }
}
