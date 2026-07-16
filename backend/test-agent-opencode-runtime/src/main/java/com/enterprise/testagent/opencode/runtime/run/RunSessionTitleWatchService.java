package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventScopeContext;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionTitleUpdateRepository;
import com.enterprise.testagent.event.RunEventAppender;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 处理首轮 OpenCode 原生标题监听的注册、CAS 同步与主动取消。
 */
@Service
public class RunSessionTitleWatchService {

    private static final Duration REMOTE_TITLE_READ_TIMEOUT = Duration.ofSeconds(5);

    private final RunSessionTitleWatchRegistry registry;
    private final SessionTitleUpdateRepository sessionTitleUpdateRepository;
    private final RunEventAppender runEventAppender;
    private final RunEventPersistencePolicy runEventPersistencePolicy;
    private final SessionRepository sessionRepository;

    public RunSessionTitleWatchService(
            RunSessionTitleWatchRegistry registry,
            SessionTitleUpdateRepository sessionTitleUpdateRepository,
            RunEventAppender runEventAppender,
            RunEventPersistencePolicy runEventPersistencePolicy) {
        this(registry, sessionTitleUpdateRepository, runEventAppender, runEventPersistencePolicy, null);
    }

    /** 生产标题同步额外读取平台会话，确认 token 仍指向当前远端 root session。 */
    @Autowired
    public RunSessionTitleWatchService(
            RunSessionTitleWatchRegistry registry,
            SessionTitleUpdateRepository sessionTitleUpdateRepository,
            RunEventAppender runEventAppender,
            RunEventPersistencePolicy runEventPersistencePolicy,
            SessionRepository sessionRepository) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.sessionTitleUpdateRepository = Objects.requireNonNull(sessionTitleUpdateRepository, "sessionTitleUpdateRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.runEventPersistencePolicy = Objects.requireNonNull(runEventPersistencePolicy, "runEventPersistencePolicy must not be null");
        this.sessionRepository = sessionRepository;
    }

    /** 注册首轮 root Run 的不可变远端监听路由。 */
    public RunSessionTitleWatchRegistry.TitleWatchToken registerFirstRun(
            SessionId sessionId,
            RunId runId,
            AgentRuntime runtime,
            ExecutionNode node,
            String directory,
            String workspace,
            String remoteSessionId,
            String expectedPlatformTitle) {
        return registry.register(
                sessionId,
                runId,
                runtime,
                node,
                directory,
                workspace,
                remoteSessionId,
                expectedPlatformTitle);
    }

    /** root 成功后进入标题等待，返回 false 表示令牌已取消或不是当前代际。 */
    public boolean enterTitleWait(RunSessionTitleWatchRegistry.TitleWatchToken token) {
        return registry.enterTitleWait(token);
    }

    /** 新 Run 只取消旧的 TITLE_WAIT 监听，并通知仍订阅原 Run SSE 的页面停止等待。 */
    public boolean closeTitleWaitForNextRun(SessionId sessionId, String traceId) {
        Optional<RunSessionTitleWatchRegistry.TitleWatchToken> token = registry.findBySessionId(sessionId)
                .filter(this::isTitlePending);
        if (token.isEmpty() || !registry.closeTitleWaitForNextRun(sessionId)) {
            return false;
        }
        appendWatchClosed(token.get(), traceId);
        return true;
    }

    /** 手动改名、归档或删除会注销当前代际；TITLE_WAIT 同时通知原 Run SSE。 */
    public boolean cancelForSession(SessionId sessionId, String traceId) {
        Optional<RunSessionTitleWatchRegistry.TitleWatchToken> token = registry.findBySessionId(sessionId);
        if (token.isEmpty()) {
            return false;
        }
        boolean wasWaiting = isTitlePending(token.get());
        if (!registry.close(token.get())) {
            return false;
        }
        if (wasWaiting) {
            appendWatchClosed(token.get(), traceId);
        }
        return true;
    }

    /**
     * 对已经通过 root scope 校验的原生 session.updated 做标题 CAS。过期 token、默认标题、绑定不匹配和 CAS
     * 失败都不产生标题确认字段。
     */
    public RunEventDraft synchronizeNativeTitle(RunEventDraft draft) {
        if (draft.type() != RunEventType.SESSION_UPDATED) {
            return draft;
        }
        RunEventScopeContext scope = draft.scopeContext();
        if (scope == null || scope.childSession() || !scope.rootSessionId().equals(scope.sessionId())) {
            return draft;
        }
        Optional<RunSessionTitleWatchRegistry.TitleWatchToken> token = registry.findByRunId(draft.runId())
                .filter(current -> current.remoteSessionId().equals(scope.rootSessionId()))
                .filter(current -> current.state() != RunSessionTitleWatchRegistry.State.CLOSED);
        if (token.isEmpty()) {
            return draft;
        }
        if (!isCurrentRemoteSessionBinding(token.get())) {
            closeAfterUnresolvedTitle(token.get(), draft.traceId());
            return draft;
        }
        Optional<String> title = sessionUpdatedTitle(draft.payload())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !OpencodeSessionTitlePolicy.isDefaultTitle(value));
        if (title.isEmpty()) {
            return draft;
        }
        boolean wasWaiting = isTitlePending(token.get());
        boolean updated = sessionTitleUpdateRepository.updateTitleIfCurrent(
                token.get().sessionId(),
                token.get().expectedPlatformTitle(),
                title.get(),
                Instant.now(),
                draft.traceId());
        if (!updated) {
            if (wasWaiting && registry.close(token.get())) {
                appendWatchClosed(token.get(), draft.traceId());
            } else if (!wasWaiting) {
                registry.close(token.get());
            }
            return draft;
        }
        registry.close(token.get());
        return withSynchronizedTitle(draft, title.get(), wasWaiting);
    }

    /**
     * 在 scope router 之前消费 title agent 的完成消息。完成信号本身不写入旧 Run，而是用 token 固化的
     * runtime/node/directory/workspace/remote id 读取一次最终 session 标题并合成为 root session.updated。
     */
    public Optional<RunEventDraft> completeTitleAgentMessage(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            RunEventDraft completedMessage) {
        if (!isCompletedTitleAgentMessage(token, completedMessage) || !registry.beginTitleRead(token)) {
            return Optional.empty();
        }
        Optional<String> title = findRemoteSessionTitle(token, completedMessage.traceId());
        if (title.isEmpty()) {
            closeAfterUnresolvedTitle(token, completedMessage.traceId());
            return Optional.empty();
        }
        RunEventScopeContext scope = RunEventScopeContext.root(token.runId(), token.remoteSessionId());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(scope.toPayloadMetadata());
        payload.put("rawType", "session.updated");
        payload.put("sessionID", token.remoteSessionId());
        payload.put("info", Map.of("title", title.get()));
        return Optional.of(new RunEventDraft(
                token.runId(),
                RunEventType.SESSION_UPDATED,
                completedMessage.traceId(),
                completedMessage.occurredAt(),
                payload,
                scope));
    }

    /**
     * 标题等待连接异常后的补偿读取。它只关闭或同步标题确认，不会调用 Run 失败路径；已取消 token 不会读取或重连。
     */
    public Optional<RunEventDraft> compensateAfterTitleWaitDisconnect(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            String traceId) {
        if (!registry.beginTitleRead(token)) {
            return Optional.empty();
        }
        Optional<String> title = findRemoteSessionTitle(token, traceId);
        if (title.isEmpty()) {
            closeAfterUnresolvedTitle(token, traceId);
            return Optional.empty();
        }
        RunEventScopeContext scope = RunEventScopeContext.root(token.runId(), token.remoteSessionId());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(scope.toPayloadMetadata());
        payload.put("rawType", "session.updated");
        payload.put("sessionID", token.remoteSessionId());
        payload.put("info", Map.of("title", title.get()));
        return Optional.of(new RunEventDraft(
                token.runId(), RunEventType.SESSION_UPDATED, traceId, Instant.now(), payload, scope));
    }

    /**
     * 只使用令牌固化的实际路由读取远端 session；不得重新按 workspace/session 解析路由，避免新一轮对话改变目标。
     */
    public Optional<String> findRemoteSessionTitle(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            String traceId) {
        if (token == null || token.state() == RunSessionTitleWatchRegistry.State.CLOSED) {
            return Optional.empty();
        }
        try {
            AgentRuntimeResult result = token.runtime().runtime(new AgentRuntimeCommand(
                            token.node(),
                            "GET",
                            "/session/" + encodePath(token.remoteSessionId()),
                            token.directory(),
                            token.workspace(),
                            Map.of(),
                            null,
                            traceId))
                    .block(REMOTE_TITLE_READ_TIMEOUT);
            if (result == null) {
                return Optional.empty();
            }
            return remoteSessionTitle(result.body());
        } catch (RuntimeException exception) {
            // 404、runtime 超时和短暂断连都只意味着本次标题不可用，不能逆转已经成功的 Run。
            return Optional.empty();
        }
    }

    /** 当前 Run 是否仍具有可转入 TITLE_WAIT 的首轮监听令牌。 */
    public Optional<RunSessionTitleWatchRegistry.TitleWatchToken> tokenForRun(RunId runId) {
        return registry.findByRunId(runId);
    }

    /** 当前 token 仍处于可恢复的标题等待阶段。 */
    public boolean isWaitingForTitle(RunSessionTitleWatchRegistry.TitleWatchToken token) {
        return token != null && token.state() == RunSessionTitleWatchRegistry.State.TITLE_WAIT;
    }

    /** TITLE_WAIT 阶段只认指定 root 的 title agent assistant 完成消息。 */
    public boolean isCompletedTitleAgentMessage(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            RunEventDraft draft) {
        if (token == null || token.state() != RunSessionTitleWatchRegistry.State.TITLE_WAIT
                || draft.type() != RunEventType.MESSAGE_UPDATED
                || !"message.updated".equals(text(draft.payload().get("rawType")).orElse(null))
                || !token.remoteSessionId().equals(text(draft.payload().get("sessionID"))
                        .or(() -> text(draft.payload().get("sessionId"))).orElse(null))) {
            return false;
        }
        return map(draft.payload().get("info"))
                .filter(info -> "assistant".equals(text(info.get("role")).orElse(null)))
                .filter(info -> "title".equals(text(info.get("agent")).orElse(null)))
                .flatMap(info -> map(info.get("time")))
                .flatMap(time -> text(time.get("completed")))
                .isPresent();
    }

    private boolean isCurrentRemoteSessionBinding(RunSessionTitleWatchRegistry.TitleWatchToken token) {
        return sessionRepository == null || sessionRepository.findById(token.sessionId())
                .map(session -> token.remoteSessionId().equals(session.opencodeSessionId()))
                .orElse(false);
    }

    private boolean isTitlePending(RunSessionTitleWatchRegistry.TitleWatchToken token) {
        return token.state() == RunSessionTitleWatchRegistry.State.TITLE_WAIT
                || token.state() == RunSessionTitleWatchRegistry.State.TITLE_READING;
    }

    private void closeAfterUnresolvedTitle(RunSessionTitleWatchRegistry.TitleWatchToken token, String traceId) {
        boolean wasWaiting = isTitlePending(token);
        if (registry.close(token) && wasWaiting) {
            appendWatchClosed(token, traceId);
        }
    }

    private void appendWatchClosed(RunSessionTitleWatchRegistry.TitleWatchToken token, String traceId) {
        RunEventScopeContext scope = RunEventScopeContext.root(token.runId(), token.remoteSessionId());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(scope.toPayloadMetadata());
        payload.put("rawType", "session.updated");
        payload.put("sessionID", token.remoteSessionId());
        payload.put("platformSessionTitlePending", false);
        payload.put("platformSessionTitleWatchClosed", true);
        runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(new RunEventDraft(
                token.runId(),
                RunEventType.SESSION_UPDATED,
                traceId,
                Instant.now(),
                payload,
                scope)));
    }

    private RunEventDraft withSynchronizedTitle(RunEventDraft draft, String title, boolean watchClosed) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        payload.put("platformSessionTitleSynchronized", true);
        payload.put("platformSessionTitle", title);
        if (watchClosed) {
            payload.put("platformSessionTitlePending", false);
            payload.put("platformSessionTitleWatchClosed", true);
        }
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                payload,
                draft.scopeContext());
    }

    @SuppressWarnings("unchecked")
    private Optional<String> sessionUpdatedTitle(Map<String, Object> payload) {
        Object info = payload.get("info");
        if (info instanceof Map<?, ?> map && map.get("title") instanceof String title) {
            return Optional.of(title);
        }
        Object rawPayload = payload.get("rawPayload");
        if (!(rawPayload instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        Object properties = raw.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return Optional.empty();
        }
        Object wrappedInfo = propertyMap.get("info");
        if (wrappedInfo instanceof Map<?, ?> wrappedMap && wrappedMap.get("title") instanceof String title) {
            return Optional.of(title);
        }
        return Optional.empty();
    }

    private Optional<String> remoteSessionTitle(JsonNode body) {
        if (body == null || body.isNull()) {
            return Optional.empty();
        }
        JsonNode title = body.path("title");
        if (!title.isTextual()) {
            title = body.path("info").path("title");
        }
        if (!title.isTextual()) {
            return Optional.empty();
        }
        String value = title.asText().trim();
        return value.isBlank() || OpencodeSessionTitlePolicy.isDefaultTitle(value)
                ? Optional.empty()
                : Optional.of(value);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> map(Object value) {
        return value instanceof Map<?, ?> source ? Optional.of((Map<String, Object>) source) : Optional.empty();
    }

    private Optional<String> text(Object value) {
        if (!(value instanceof String string)) {
            return Optional.empty();
        }
        String normalized = string.trim();
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
