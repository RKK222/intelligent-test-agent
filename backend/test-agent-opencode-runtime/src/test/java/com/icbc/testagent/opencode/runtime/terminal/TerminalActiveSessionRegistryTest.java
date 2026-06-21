package com.icbc.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TerminalActiveSessionRegistryTest {

    @Test
    void reserveRejectsSecondActiveTerminalForSameSessionUntilReleased() {
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalActiveSessionRegistry.Lease lease = registry.reserve(ticket("ses_1234567890abcdef"));

        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isTrue();
        assertThatThrownBy(() -> registry.reserve(ticket("ses_1234567890abcdef")))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        lease.close();

        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
        registry.reserve(ticket("ses_1234567890abcdef")).close();
    }

    private static TerminalTicket ticket(String sessionId) {
        return new TerminalTicket(
                "pty_1234567890abcdef",
                new SessionId(sessionId),
                new WorkspaceId("wrk_1234567890abcdef"),
                new ExecutionNodeId("node_1234567890abcdef"),
                Path.of("/tmp/demo"),
                Path.of("/tmp/demo"),
                "/bin/sh",
                80,
                24,
                "trace_1234567890abcdef",
                Instant.parse("2026-06-19T00:01:00Z"));
    }
}
