package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 首轮 OpenCode 标题监听的本地代际注册表。
 *
 * <p>监听权只属于承载该 Run 的本地事件流，不能跨 Java 实例复用。令牌持有当次已解析的运行时路由，避免后续
 * workspace 或 session binding 变化后误投递到其他远端会话。</p>
 */
@Component
public class RunSessionTitleWatchRegistry {

    private final ConcurrentMap<SessionId, TitleWatchToken> tokensBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<RunId, TitleWatchToken> tokensByRun = new ConcurrentHashMap<>();

    /** 标题监听的局部状态机。 */
    public enum State {
        ACTIVE,
        TITLE_WAIT,
        TITLE_READING,
        CLOSED
    }

    /**
     * 为首轮 Run 注册不可复用监听令牌。已有令牌时返回已有令牌，调用方不得将后续 Run 替换为新代际。
     */
    public TitleWatchToken register(
            SessionId sessionId,
            RunId runId,
            AgentRuntime runtime,
            ExecutionNode node,
            String directory,
            String workspace,
            String remoteSessionId,
            String expectedPlatformTitle) {
        TitleWatchToken candidate = new TitleWatchToken(
                sessionId,
                runId,
                runtime,
                node,
                directory,
                workspace,
                remoteSessionId,
                expectedPlatformTitle);
        TitleWatchToken token = tokensBySession.putIfAbsent(sessionId, candidate);
        if (token == null) {
            tokensByRun.put(runId, candidate);
            return candidate;
        }
        return token;
    }

    /** 仅 ACTIVE 令牌可在 root 成功后进入标题等待。 */
    public boolean enterTitleWait(TitleWatchToken token) {
        return token != null && token.state.compareAndSet(State.ACTIVE, State.TITLE_WAIT);
    }

    /**
     * title agent 的完成消息只允许触发一次最终远端标题读取。读取期间不再接受第二条完成消息，
     * 但仍可被用户的下一轮对话、手动改名或归档主动关闭。
     */
    public boolean beginTitleRead(TitleWatchToken token) {
        return token != null && token.state.compareAndSet(State.TITLE_WAIT, State.TITLE_READING);
    }

    /** 查询当前 Run 的有效令牌；关闭后的令牌不会再暴露给事件处理。 */
    public Optional<TitleWatchToken> findByRunId(RunId runId) {
        return Optional.ofNullable(tokensByRun.get(runId));
    }

    /** 查询当前平台会话的有效令牌。 */
    public Optional<TitleWatchToken> findBySessionId(SessionId sessionId) {
        return Optional.ofNullable(tokensBySession.get(sessionId));
    }

    /**
     * 新一轮对话只关闭已经进入 TITLE_WAIT 的旧监听，不干扰仍在执行中的 ACTIVE Run。
     */
    public boolean closeTitleWaitForNextRun(SessionId sessionId) {
        return findBySessionId(sessionId)
                .filter(token -> token.state() == State.TITLE_WAIT || token.state() == State.TITLE_READING)
                .map(this::close)
                .orElse(false);
    }

    /**
     * 注销令牌。若已进入 TITLE_WAIT，会向取消信号发射以真实释放远端 Flux；ACTIVE 注销只阻止后续标题处理，
     * 不中断正在处理的主 Run 事件流。
     */
    public boolean close(TitleWatchToken token) {
        if (token == null) {
            return false;
        }
        State previous = token.state.getAndSet(State.CLOSED);
        if (previous == State.CLOSED) {
            return false;
        }
        tokensBySession.remove(token.sessionId(), token);
        tokensByRun.remove(token.runId(), token);
        if (previous == State.TITLE_WAIT || previous == State.TITLE_READING) {
            token.cancelSignal.tryEmitEmpty();
        }
        return true;
    }

    /** 标题监听令牌的不可变路由字段与可变生命周期状态。 */
    public static final class TitleWatchToken {
        private final SessionId sessionId;
        private final RunId runId;
        private final AgentRuntime runtime;
        private final ExecutionNode node;
        private final String directory;
        private final String workspace;
        private final String remoteSessionId;
        private final String expectedPlatformTitle;
        private final Sinks.One<Void> cancelSignal = Sinks.one();
        private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

        private TitleWatchToken(
                SessionId sessionId,
                RunId runId,
                AgentRuntime runtime,
                ExecutionNode node,
                String directory,
                String workspace,
                String remoteSessionId,
                String expectedPlatformTitle) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
            this.runId = Objects.requireNonNull(runId, "runId must not be null");
            this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
            this.node = Objects.requireNonNull(node, "node must not be null");
            this.directory = directory;
            this.workspace = workspace;
            this.remoteSessionId = requireText(remoteSessionId, "remoteSessionId");
            this.expectedPlatformTitle = requireText(expectedPlatformTitle, "expectedPlatformTitle");
        }

        public SessionId sessionId() {
            return sessionId;
        }

        public RunId runId() {
            return runId;
        }

        public AgentRuntime runtime() {
            return runtime;
        }

        public ExecutionNode node() {
            return node;
        }

        public String directory() {
            return directory;
        }

        public String workspace() {
            return workspace;
        }

        public String remoteSessionId() {
            return remoteSessionId;
        }

        public String expectedPlatformTitle() {
            return expectedPlatformTitle;
        }

        public State state() {
            return state.get();
        }

        /** 仅在 TITLE_WAIT 被关闭时完成，供远端 Flux 的 takeUntilOther 释放订阅。 */
        public Mono<Void> cancellationSignal() {
            return cancelSignal.asMono();
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
