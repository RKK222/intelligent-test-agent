package com.example.testagent.opencode.runtime.terminal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 单条 PTY WebSocket 连接内的输入限速器，避免交互式终端被大帧或高频 resize/input 压垮。
 */
public class TerminalInputRateLimiter {

    private final Clock clock;
    private final int maxInputBytes;
    private final WindowCounter inputCounter;
    private final WindowCounter resizeCounter;

    public TerminalInputRateLimiter(
            Clock clock,
            int maxInputBytes,
            int maxInputMessages,
            int maxResizeMessages,
            Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxInputBytes = Math.max(1, maxInputBytes);
        Duration effectiveWindow = window == null || window.isZero() || window.isNegative()
                ? Duration.ofSeconds(1)
                : window;
        this.inputCounter = new WindowCounter(Math.max(1, maxInputMessages), effectiveWindow);
        this.resizeCounter = new WindowCounter(Math.max(1, maxResizeMessages), effectiveWindow);
    }

    public Decision check(TerminalClientMessage message) {
        if (message == null || message.type() == null) {
            return Decision.allow();
        }
        if ("input".equals(message.type())) {
            int bytes = message.data() == null ? 0 : message.data().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxInputBytes) {
                return Decision.reject("RATE_LIMITED", "terminal input too large");
            }
            return inputCounter.tryAcquire(clock.instant())
                    ? Decision.allow()
                    : Decision.reject("RATE_LIMITED", "terminal input rate exceeded");
        }
        if ("resize".equals(message.type())) {
            return resizeCounter.tryAcquire(clock.instant())
                    ? Decision.allow()
                    : Decision.reject("RATE_LIMITED", "terminal resize rate exceeded");
        }
        return Decision.allow();
    }

    public record Decision(boolean allowed, String code, String message) {
        private static Decision allow() {
            return new Decision(true, null, null);
        }

        private static Decision reject(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    private static final class WindowCounter {
        private final int capacity;
        private final Duration window;
        private Instant windowStartedAt;
        private int used;

        private WindowCounter(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        private boolean tryAcquire(Instant now) {
            if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(window))) {
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
