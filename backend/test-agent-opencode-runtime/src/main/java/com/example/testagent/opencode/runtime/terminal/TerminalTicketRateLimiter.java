package com.example.testagent.opencode.runtime.terminal;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
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

    @Autowired
    public TerminalTicketRateLimiter(
            @Value("${test-agent.terminal.ticket-capacity:10}") int capacity,
            @Value("${test-agent.terminal.ticket-window:1m}") Duration window) {
        this(Clock.systemUTC(), capacity, window);
    }

    TerminalTicketRateLimiter(Clock clock, int capacity, Duration window) {
        this.clock = clock;
        this.capacity = Math.max(1, capacity);
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(1)
                : window;
    }

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

    private static final class Counter {
        private Instant windowStartedAt;
        private int used;

        private Counter(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

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
