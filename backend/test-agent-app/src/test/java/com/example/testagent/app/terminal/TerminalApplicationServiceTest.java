package com.example.testagent.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void createTicketBindsSessionWorkspaceAndTraceId() throws Exception {
        Fixture fixture = new Fixture(tempDir);
        Files.createDirectories(tempDir.resolve("tests"));

        TerminalTicketResponse response = fixture.service.createTicket(
                new SessionId("ses_1234567890abcdef"),
                new TerminalTicketRequest("wrk_1234567890abcdef", "tests", null, 120, 32),
                "trace_1234567890abcdef");

        assertThat(response.ticket()).startsWith("pty_");
        assertThat(response.webSocketUrl()).isEqualTo("/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=" + response.ticket());
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(60));

        TerminalTicket ticket = fixture.service.consumeTicket(
                new SessionId("ses_1234567890abcdef"),
                response.ticket(),
                "http://localhost:3000",
                "trace_1234567890abcdef");
        assertThat(ticket.workspaceId()).isEqualTo(new WorkspaceId("wrk_1234567890abcdef"));
        assertThat(ticket.cwd()).isEqualTo(tempDir.resolve("tests").toRealPath());
        assertThat(ticket.traceId()).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void createTicketRejectsSessionWithoutRemoteMapping() {
        Fixture fixture = new Fixture(tempDir, false);

        assertThatThrownBy(() -> fixture.service.createTicket(
                        new SessionId("ses_1234567890abcdef"),
                        new TerminalTicketRequest("wrk_1234567890abcdef", ".", null, 80, 24),
                        "trace_1234567890abcdef"))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void createTicketRejectsCwdOutsideWorkspaceRoot() {
        Fixture fixture = new Fixture(tempDir);

        assertThatThrownBy(() -> fixture.service.createTicket(
                        new SessionId("ses_1234567890abcdef"),
                        new TerminalTicketRequest("wrk_1234567890abcdef", "../outside", null, 80, 24),
                        "trace_1234567890abcdef"))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void consumeTicketRejectsReuse() {
        Fixture fixture = new Fixture(tempDir);
        TerminalTicketResponse response = fixture.service.createTicket(
                new SessionId("ses_1234567890abcdef"),
                new TerminalTicketRequest("wrk_1234567890abcdef", ".", null, 80, 24),
                "trace_1234567890abcdef");

        fixture.service.consumeTicket(new SessionId("ses_1234567890abcdef"), response.ticket(), "http://localhost:3000", "trace_1234567890abcdef");

        assertThatThrownBy(() -> fixture.service.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        response.ticket(),
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static final class Fixture {
        private final WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        private final SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
        private final TerminalApplicationService service;

        private Fixture(Path root) {
            this(root, true);
        }

        private Fixture(Path root, boolean mappedSession) {
            service = new TerminalApplicationService(
                    workspaceRepository,
                    sessionRepository,
                    new TerminalTicketStore(Clock.fixed(NOW, ZoneOffset.UTC), () -> "pty_1234567890abcdef"));
            when(workspaceRepository.findById(new WorkspaceId("wrk_1234567890abcdef"))).thenReturn(Optional.of(workspace(root)));
            when(sessionRepository.findById(new SessionId("ses_1234567890abcdef"))).thenReturn(Optional.of(session(mappedSession)));
        }

        private static Workspace workspace(Path root) {
            return new Workspace(
                    new WorkspaceId("wrk_1234567890abcdef"),
                    "Demo",
                    root.toString(),
                    WorkspaceStatus.ACTIVE,
                    NOW,
                    NOW,
                    "trace_1234567890abcdef");
        }

        private static Session session(boolean mappedSession) {
            if (!mappedSession) {
                return new Session(
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        "Demo",
                        SessionStatus.ACTIVE,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef");
            }
            return new Session(
                    new SessionId("ses_1234567890abcdef"),
                    new WorkspaceId("wrk_1234567890abcdef"),
                    "Demo",
                    SessionStatus.ACTIVE,
                    NOW,
                    NOW,
                    "trace_1234567890abcdef",
                    "ses_remote1234567890abcdef",
                    new ExecutionNodeId("node_1234567890abcdef"));
        }
    }
}
