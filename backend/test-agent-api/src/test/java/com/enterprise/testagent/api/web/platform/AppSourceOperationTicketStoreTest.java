package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AppSourceOperationTicketStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");

    @Test
    void ticketIsBoundToOperationUserAndIssuerJvmAndCanBeConsumedOnlyOnce() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_1");
        AppSourceOperationTicket issued = store.issue(
                "aso_12345678", "usr_1", true, "bjp_issuer",
                "https://console.example", "trace_ticket");

        AppSourceOperationTicket consumed = store.consume(
                "ast_ticket_1", "aso_12345678", "bjp_issuer", "https://console.example");

        assertThat(consumed.operationId()).isEqualTo("aso_12345678");
        assertThat(consumed.userId()).isEqualTo("usr_1");
        assertThat(consumed.appAdmin()).isTrue();
        assertThat(consumed.issuerBackendProcessId()).isEqualTo("bjp_issuer");
        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_1", "aso_12345678", "bjp_issuer", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void ticketCannotBeConsumedByAnotherJvm() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_2");
        store.issue("aso_12345678", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");

        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_2", "aso_12345678", "bjp_other", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void ticketCannotBeConsumedFromAnotherAllowedOrigin() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_origin");
        store.issue("aso_12345678", "usr_1", false, "bjp_issuer",
                "https://Console-A.Example:443", "trace_ticket");

        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_origin", "aso_12345678", "bjp_issuer", "https://console-b.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(store.consume(
                "ast_ticket_origin", "aso_12345678", "bjp_issuer", "https://console-a.example").origin())
                .isEqualTo("https://console-a.example");
        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_origin", "aso_12345678", "bjp_issuer", "https://console-a.example"))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void ticketCannotBeUsedForAnotherOperationAndFailureConsumesItWithoutLeakingExistence() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_operation");
        store.issue("aso_12345678", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");

        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_operation", "aso_other", "bjp_issuer", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo("应用源码进度 ticket 无效");
                });
        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_operation", "aso_12345678", "bjp_issuer", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.getMessage()).isEqualTo("应用源码进度 ticket 无效"));
    }

    @Test
    void expiredTicketIsRejected() {
        MutableClock clock = new MutableClock(NOW);
        AppSourceOperationTicketStore issuer = new AppSourceOperationTicketStore(
                clock, () -> "ast_ticket_3");
        issuer.issue("aso_12345678", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");
        clock.now = NOW.plusSeconds(61);

        assertThatThrownBy(() -> issuer.consume(
                        "ast_ticket_3", "aso_12345678", "bjp_issuer", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void unconsumedExpiredTicketsAreReclaimedOnTheNextIssue() {
        MutableClock clock = new MutableClock(NOW);
        AtomicInteger sequence = new AtomicInteger();
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                clock, () -> "ast_expired_" + sequence.incrementAndGet(), 1);
        store.issue("job_1", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");
        clock.now = NOW.plusSeconds(61);

        assertThat(store.issue("job_2", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket").ticket()).isEqualTo("ast_expired_2");
    }

    @Test
    void capacityRejectsNewTicketsWithoutSilentlyOverwritingLiveTickets() {
        AtomicInteger sequence = new AtomicInteger();
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "ast_capacity_" + sequence.incrementAndGet(),
                1);
        store.issue("job_0", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");

        assertThatThrownBy(() -> store.issue(
                        "job_overflow", "usr_1", false, "bjp_issuer",
                        "https://console.example", "trace_ticket"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(store.consume(
                "ast_capacity_1", "job_0", "bjp_issuer", "https://console.example").operationId())
                .isEqualTo("job_0");
    }

    @Test
    void concurrentConsumersCanSucceedOnlyOnce() throws Exception {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_concurrent");
        store.issue("job_concurrent", "usr_1", false, "bjp_issuer",
                "https://console.example", "trace_ticket");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 16; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        store.consume("ast_concurrent", "job_concurrent", "bjp_issuer",
                                "https://console.example");
                        return true;
                    } catch (PlatformException exception) {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                        return false;
                    }
                }));
            }
            start.countDown();
        }

        assertThat(results.stream().filter(result -> {
            try {
                return result.get();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        })).hasSize(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
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
            return now;
        }
    }
}
