package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 首帧注册认证专用限流器；按可信代理解析后的来源地址隔离固定窗口额度。 */
@Component
public class LocalClientAuthenticationRateLimiter {

    private final Clock clock;
    private final int capacity;
    private final Duration window;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    @Autowired
    public LocalClientAuthenticationRateLimiter(
            @Value("${test-agent.local-client.authentication-rate-limit.capacity:5}") int capacity,
            @Value("${test-agent.local-client.authentication-rate-limit.window:1m}") Duration window) {
        this(Clock.systemUTC(), capacity, window);
    }

    LocalClientAuthenticationRateLimiter(Clock clock, int capacity, Duration window) {
        this.clock = clock;
        this.capacity = Math.max(1, capacity);
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(1)
                : window;
    }

    /** 认证发生前占用一次额度；对外保持统一失败文案，避免泄露凭据是否存在。 */
    public void acquire(String clientAddress) {
        String key = normalize(clientAddress);
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(clock.instant()));
        if (!counter.tryAcquire(clock.instant(), capacity, window)) {
            throw new PlatformException(ErrorCode.RATE_LIMITED, "本地客户端认证失败");
        }
    }

    /** 成功认证后清除该来源的失败窗口，保证正常服务重连不受历史尝试影响。 */
    public void authenticationSucceeded(String clientAddress) {
        counters.remove(normalize(clientAddress));
    }

    private static String normalize(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
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
