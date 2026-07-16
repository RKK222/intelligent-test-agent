package com.enterprise.testagent.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionMessageTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void sessionMessageRequiresPrefixedIdAndTextContent() {
        SessionMessage message = new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.USER,
                "run the tests",
                NOW,
                "trace_1234567890abcdef");

        assertThat(message.messageId().value()).isEqualTo("msg_1234567890abcdef");
        assertThat(message.role()).isEqualTo(SessionMessageRole.USER);
        assertThat(message.content()).isEqualTo("run the tests");
    }

    @Test
    void sessionMessageRejectsBlankContent() {
        assertThatThrownBy(() -> new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.USER,
                " ",
                NOW,
                "trace_1234567890abcdef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void assistantMessageAllowsBlankContentWhenStructuredPartsExist() {
        SessionMessage message = new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.ASSISTANT,
                "",
                NOW,
                "trace_1234567890abcdef",
                null,
                "opencode",
                "msg_remote1234567890abcdef",
                "[{\"type\":\"tool\",\"id\":\"part_1\"}]",
                TokenUsage.empty(),
                null,
                NOW);

        assertThat(message.content()).isEmpty();
        assertThat(message.partsJson()).contains("\"type\":\"tool\"");
    }

    @Test
    void messageDefaultsToManualSourceAndCanCarryScheduledSender() {
        SessionMessage manual = new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.USER,
                "run the tests",
                NOW,
                "trace_1234567890abcdef");

        SessionMessage scheduled = manual.withSource(
                ConversationSourceType.SCHEDULED_TASK,
                "str_1234567890abcdef",
                new UserId("usr_1234567890abcdef"));

        assertThat(manual.sourceType()).isEqualTo(ConversationSourceType.MANUAL);
        assertThat(scheduled.sourceType()).isEqualTo(ConversationSourceType.SCHEDULED_TASK);
        assertThat(scheduled.sourceRefId()).isEqualTo("str_1234567890abcdef");
        assertThat(scheduled.senderUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
    }
}
