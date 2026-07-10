package com.icbc.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationRunContextTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-07-11T00:00:00Z");

    @Test
    void normalizesStableBindingsAndKeepsRemoteSessionOptional() {
        ConversationRunContext context = new ConversationRunContext(
                new UserId("usr_1234567890abcdef"),
                " OpenCode ",
                "ocp_1234567890abcdef",
                "server-a",
                session(null),
                workspace(),
                node(),
                null,
                1,
                EXPIRES_AT);

        assertThat(context.agentId()).isEqualTo("opencode");
        assertThat(context.remoteSessionId()).isNull();
        assertThat(context.withExpiresAt(EXPIRES_AT.plusSeconds(60)).expiresAt())
                .isEqualTo(EXPIRES_AT.plusSeconds(60));
    }

    @Test
    void rejectsNonPositiveContextVersion() {
        assertThatThrownBy(() -> new ConversationRunContext(
                new UserId("usr_1234567890abcdef"),
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                session(null),
                workspace(),
                node(),
                null,
                0,
                EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Workspace workspace() {
        return new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                EXPIRES_AT.minusSeconds(3600),
                EXPIRES_AT.minusSeconds(3600),
                "server-a",
                "trace_test");
    }

    private static Session session(String remoteSessionId) {
        ExecutionNodeId nodeId = remoteSessionId == null ? null : node().executionNodeId();
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                workspace().workspaceId(),
                "session",
                SessionStatus.ACTIVE,
                EXPIRES_AT.minusSeconds(3600),
                EXPIRES_AT.minusSeconds(3600),
                "trace_test",
                remoteSessionId,
                nodeId);
    }

    private static ExecutionNode node() {
        Instant snapshotAt = EXPIRES_AT.minusSeconds(3600);
        return new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://10.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                snapshotAt,
                Set.of("opencode"),
                snapshotAt,
                snapshotAt,
                "trace_test");
    }
}
