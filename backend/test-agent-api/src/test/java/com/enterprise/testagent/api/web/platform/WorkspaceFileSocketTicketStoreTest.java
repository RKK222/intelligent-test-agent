package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkspaceFileSocketTicketStoreTest {

    @Test
    void validatesWithoutConsumptionThenRejectsReuseWithOneGenericUnauthorizedError() {
        WorkspaceFileSocketTicketStore store = new WorkspaceFileSocketTicketStore(
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC),
                () -> "wft_once");
        store.issue("wrk_1", "server-a", "server-a", false, false, false,
                "usr_1", "u001", "workspace", null, null, "trace_1234567890abcdef");

        store.validate("wft_once", "https://console.example");
        assertThat(store.consume("wft_once", "https://console.example").unifiedAuthId()).isEqualTo("u001");
        assertGenericUnauthorized(() -> store.validate("wft_once", "https://console.example"));
        assertGenericUnauthorized(() -> store.consume("missing", "https://console.example"));
        assertGenericUnauthorized(() -> store.validate("wft_once", null));
    }

    @Test
    void rejectsExpiredTicketWithTheSameGenericUnauthorizedError() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        WorkspaceFileSocketTicketStore store = new WorkspaceFileSocketTicketStore(clock, () -> "wft_expired");
        store.issue("wrk_1", "server-a", "server-a", false, false, false,
                "usr_1", "u001", "workspace", null, null, "trace_1234567890abcdef");

        clock.advance(Duration.ofSeconds(61));

        assertGenericUnauthorized(() -> store.validate("wft_expired", "https://console.example"));
        assertGenericUnauthorized(() -> store.consume("wft_expired", "https://console.example"));
    }

    private static void assertGenericUnauthorized(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
            assertThat(exception.getMessage()).isEqualTo("文件 WebSocket 未授权");
        });
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        private void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
