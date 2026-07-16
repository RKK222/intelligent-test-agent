package com.enterprise.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TerminalInputRateLimiterTest {

    @Test
    void allowsInputAndResizeWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-19T00:00:00Z"));
        TerminalInputRateLimiter limiter = new TerminalInputRateLimiter(clock, 8, 2, 1, Duration.ofSeconds(1));

        assertThat(limiter.check(new TerminalClientMessage("input", "abc", null, null, null)).allowed()).isTrue();
        assertThat(limiter.check(new TerminalClientMessage("input", "def", null, null, null)).allowed()).isTrue();
        assertThat(limiter.check(new TerminalClientMessage("resize", null, 120, 32, null)).allowed()).isTrue();

        clock.advance(Duration.ofSeconds(1));

        assertThat(limiter.check(new TerminalClientMessage("resize", null, 100, 30, null)).allowed()).isTrue();
    }

    @Test
    void rejectsInputFrameLargerThanConfiguredBytes() {
        TerminalInputRateLimiter limiter = new TerminalInputRateLimiter(
                Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("UTC")),
                4,
                10,
                10,
                Duration.ofSeconds(1));

        TerminalInputRateLimiter.Decision decision =
                limiter.check(new TerminalClientMessage("input", "12345", null, null, null));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("RATE_LIMITED");
        assertThat(decision.message()).isEqualTo("terminal input too large");
    }

    @Test
    void rejectsInputAndResizeWhenWindowCapacityIsExceeded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-19T00:00:00Z"));
        TerminalInputRateLimiter limiter = new TerminalInputRateLimiter(clock, 16, 1, 1, Duration.ofSeconds(10));

        assertThat(limiter.check(new TerminalClientMessage("input", "a", null, null, null)).allowed()).isTrue();
        TerminalInputRateLimiter.Decision inputDecision =
                limiter.check(new TerminalClientMessage("input", "b", null, null, null));
        assertThat(inputDecision.allowed()).isFalse();
        assertThat(inputDecision.code()).isEqualTo("RATE_LIMITED");
        assertThat(inputDecision.message()).isEqualTo("terminal input rate exceeded");

        assertThat(limiter.check(new TerminalClientMessage("resize", null, 100, 30, null)).allowed()).isTrue();
        TerminalInputRateLimiter.Decision resizeDecision =
                limiter.check(new TerminalClientMessage("resize", null, 110, 31, null));
        assertThat(resizeDecision.allowed()).isFalse();
        assertThat(resizeDecision.message()).isEqualTo("terminal resize rate exceeded");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
