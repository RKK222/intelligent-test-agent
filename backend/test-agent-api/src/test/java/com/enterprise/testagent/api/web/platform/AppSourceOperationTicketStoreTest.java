package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppSourceOperationTicketStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");

    @Test
    void ticketIsBoundToOperationUserAndIssuerJvmAndCanBeConsumedOnlyOnce() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_1");
        AppSourceOperationTicket issued = store.issue(
                "aso_12345678", "usr_1", true, "bjp_issuer", "trace_ticket");

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
        store.issue("aso_12345678", "usr_1", false, "bjp_issuer", "trace_ticket");

        assertThatThrownBy(() -> store.consume(
                        "ast_ticket_2", "aso_12345678", "bjp_other", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void ticketCannotBeUsedForAnotherOperationAndFailureConsumesItWithoutLeakingExistence() {
        AppSourceOperationTicketStore store = new AppSourceOperationTicketStore(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "ast_ticket_operation");
        store.issue("aso_12345678", "usr_1", false, "bjp_issuer", "trace_ticket");

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
        issuer.issue("aso_12345678", "usr_1", false, "bjp_issuer", "trace_ticket");
        clock.now = NOW.plusSeconds(61);

        assertThatThrownBy(() -> issuer.consume(
                        "ast_ticket_3", "aso_12345678", "bjp_issuer", "https://console.example"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
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
