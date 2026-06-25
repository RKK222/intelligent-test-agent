package com.icbc.testagent.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T00:00:05Z");

    @Test
    void attachOpencodeSessionSetsMappingTogetherAndPreservesPinnedFlag() {
        Session session = new Session(
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        "main",
                        CREATED_AT)
                .updateTitleAndPinned("main", true, UPDATED_AT, "trace_update");

        Session attached = session.attachOpencodeSession(
                "opencode-session-1",
                new ExecutionNodeId("node_123"),
                Instant.parse("2026-06-20T00:00:10Z"),
                "trace_attach");

        assertThat(attached.hasOpencodeSessionMapping()).isTrue();
        assertThat(attached.opencodeSessionId()).isEqualTo("opencode-session-1");
        assertThat(attached.opencodeExecutionNodeId()).isEqualTo(new ExecutionNodeId("node_123"));
        assertThat(attached.pinned()).isTrue();
        assertThat(attached.traceId()).isEqualTo("trace_attach");
    }

    @Test
    void archiveClearsPinnedFlagButKeepsInternalOpencodeMapping() {
        Session session = new Session(
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        "main",
                        SessionStatus.ACTIVE,
                        CREATED_AT,
                        UPDATED_AT,
                        "trace_create",
                        "opencode-session-1",
                        new ExecutionNodeId("node_123"))
                .updateTitleAndPinned("main", true, Instant.parse("2026-06-20T00:00:06Z"), "trace_pin");

        Session archived = session.archive(Instant.parse("2026-06-20T00:00:10Z"), "trace_archive");

        assertThat(archived.status()).isEqualTo(SessionStatus.ARCHIVED);
        assertThat(archived.pinned()).isFalse();
        assertThat(archived.hasOpencodeSessionMapping()).isTrue();
        assertThat(archived.traceId()).isEqualTo("trace_archive");
    }

    @Test
    void opencodeMappingMustBeSetAsPair() {
        assertThatThrownBy(() -> new Session(
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        "main",
                        SessionStatus.ACTIVE,
                        CREATED_AT,
                        UPDATED_AT,
                        "trace_create",
                        "opencode-session-1",
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void sessionDefaultsToManualSourceAndCanCarryScheduledTaskSource() {
        Session manual = new Session(
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                "main",
                CREATED_AT);

        Session scheduled = manual.withSource(
                ConversationSourceType.SCHEDULED_TASK,
                "str_1234567890abcdef",
                new UserId("usr_1234567890abcdef"));

        assertThat(manual.sourceType()).isEqualTo(ConversationSourceType.MANUAL);
        assertThat(manual.sourceRefId()).isNull();
        assertThat(manual.createdByUserId()).isNull();
        assertThat(scheduled.sourceType()).isEqualTo(ConversationSourceType.SCHEDULED_TASK);
        assertThat(scheduled.sourceRefId()).isEqualTo("str_1234567890abcdef");
        assertThat(scheduled.createdByUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
    }
}
