package com.icbc.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TerminalTicketStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void issuedTicketCanBeConsumedOnlyOnceForSameSessionWithOrigin() {
        MutableClock clock = new MutableClock(NOW);
        TerminalTicketStore store = new TerminalTicketStore(clock, () -> "pty_1234567890abcdef");
        TerminalTicket issued = store.issue(draft("ses_1234567890abcdef"));

        TerminalTicket consumed = store.consume(
                new SessionId("ses_1234567890abcdef"),
                issued.ticket(),
                "http://localhost:3000",
                "trace_1234567890abcdef");

        assertThat(consumed).isEqualTo(issued);
        assertThatThrownBy(() -> store.consume(
                        new SessionId("ses_1234567890abcdef"),
                        issued.ticket(),
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void consumeRejectsSessionMismatchAndMissingOrigin() {
        TerminalTicketStore store = new TerminalTicketStore(new MutableClock(NOW), () -> "pty_1234567890abcdef");
        TerminalTicket first = store.issue(draft("ses_1234567890abcdef"));

        assertThatThrownBy(() -> store.consume(
                        new SessionId("ses_other1234567890abcdef"),
                        first.ticket(),
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        TerminalTicket second = store.issue(draft("ses_1234567890abcdef"));
        assertThatThrownBy(() -> store.consume(
                        new SessionId("ses_1234567890abcdef"),
                        second.ticket(),
                        "",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void consumeRejectsExpiredTicket() {
        MutableClock clock = new MutableClock(NOW);
        TerminalTicketStore store = new TerminalTicketStore(clock, () -> "pty_1234567890abcdef");
        TerminalTicket issued = store.issue(draft("ses_1234567890abcdef"));
        clock.advance(Duration.ofSeconds(61));

        assertThatThrownBy(() -> store.consume(
                        new SessionId("ses_1234567890abcdef"),
                        issued.ticket(),
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private static TerminalTicketDraft draft(String sessionId) {
        return new TerminalTicketDraft(
                new SessionId(sessionId),
                new WorkspaceId("wrk_1234567890abcdef"),
                new ExecutionNodeId("node_1234567890abcdef"),
                Path.of("/tmp/demo"),
                Path.of("/tmp/demo"),
                "/bin/sh",
                80,
                24,
                "trace_1234567890abcdef");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
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
