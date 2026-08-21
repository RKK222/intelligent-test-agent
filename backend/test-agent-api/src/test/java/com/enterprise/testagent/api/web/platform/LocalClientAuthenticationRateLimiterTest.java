package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 验证首次接入认证按真实来源限流，且成功后不会惩罚后续正常重连。 */
class LocalClientAuthenticationRateLimiterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsOnlyTheSourceThatExhaustedItsAuthenticationBudget() {
        LocalClientAuthenticationRateLimiter limiter =
                new LocalClientAuthenticationRateLimiter(CLOCK, 2, Duration.ofMinutes(1));

        limiter.acquire("203.0.113.8");
        limiter.acquire("203.0.113.8");

        assertThatThrownBy(() -> limiter.acquire("203.0.113.8"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(exception.getMessage()).isEqualTo("本地客户端认证失败");
                });
        assertThatCode(() -> limiter.acquire("203.0.113.9")).doesNotThrowAnyException();
    }

    @Test
    void successfulAuthenticationClearsTheSourceBudget() {
        LocalClientAuthenticationRateLimiter limiter =
                new LocalClientAuthenticationRateLimiter(CLOCK, 1, Duration.ofMinutes(1));

        limiter.acquire("203.0.113.8");
        limiter.authenticationSucceeded("203.0.113.8");

        assertThatCode(() -> limiter.acquire("203.0.113.8")).doesNotThrowAnyException();
    }
}
