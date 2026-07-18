package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PTY ticket 创建限流器。当前平台没有真实用户主体，先固定按 session/workspace 维度控制。
 */
@Component
public class TerminalTicketRateLimiter {

    private final Clock clock;
    private final int capacity;
    private final Duration window;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * 使用系统时钟创建 ticket 限流器，配置来自 Spring properties。
     */
    @Autowired
    public TerminalTicketRateLimiter(
            @Value("${test-agent.terminal.ticket-capacity:10}") int capacity,
            @Value("${test-agent.terminal.ticket-window:1m}") Duration window) {
        this(Clock.systemUTC(), capacity, window);
    }

    /**
     * 创建可注入时钟的限流器，便于单元测试固定窗口时间。
     */
    TerminalTicketRateLimiter(Clock clock, int capacity, Duration window) {
        this.clock = clock;
        this.capacity = Math.max(1, capacity);
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(1)
                : window;
    }

    /**
     * 占用 session/workspace 维度的 ticket 创建额度，超限时抛出统一 RATE_LIMITED。
     */
    public void acquire(SessionId sessionId, WorkspaceId workspaceId) {
        String key = sessionId.value() + "|" + workspaceId.value();
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(clock.instant()));
        if (!counter.tryAcquire(clock.instant(), capacity, window)) {
            throw new PlatformException(
                    ErrorCode.RATE_LIMITED,
                    "PTY ticket 创建过于频繁",
                    Map.of("sessionId", sessionId.value(), "workspaceId", workspaceId.value()));
        }
    }

    /** 按服务器和超级管理员用户限制服务器终端 ticket 创建频率。 */
    public void acquireServer(LinuxServerId linuxServerId, UserId userId) {
        String key = "server-shell|" + linuxServerId.value() + "|" + userId.value();
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(clock.instant()));
        if (!counter.tryAcquire(clock.instant(), capacity, window)) {
            throw new PlatformException(
                    ErrorCode.RATE_LIMITED,
                    "服务器终端 ticket 创建过于频繁",
                    Map.of("linuxServerId", linuxServerId.value(), "userId", userId.value()));
        }
    }

    private static final class Counter {
        private Instant windowStartedAt;
        private int used;

        /**
         * 创建固定窗口计数器。
         */
        private Counter(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        /**
         * 尝试占用一个额度，窗口过期后重置计数。
         */
        private synchronized boolean tryAcquire(Instant now, int capacity, Duration window) {
            if (!now.isBefore(windowStartedAt.plus(window))) {
                windowStartedAt = now;
                used = 0;
            }
            if (used >= capacity) {
                return false;
            }
            used++;
            return true;
        }
    }
}
