package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunDispatchMessageAnchorResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");

    @Test
    void resolvesUniqueUserRemoteMessageIdForRun() {
        FakeRepository repository = new FakeRepository();
        repository.messages.add(message("msg_platform_user", SessionMessageRole.USER, RUN_ID, "msg_dispatch"));
        repository.messages.add(message("msg_platform_assistant", SessionMessageRole.ASSISTANT, RUN_ID, "msg_answer"));

        RunDispatchMessageAnchorResolver.Anchor anchor =
                RunDispatchMessageAnchorResolver.resolve(repository, SESSION_ID, RUN_ID);

        assertThat(anchor.conflicted()).isFalse();
        assertThat(anchor.dispatchMessageId()).isEqualTo("msg_dispatch");
    }

    @Test
    void rejectsDifferentUserRemoteMessageIdsForSameRun() {
        FakeRepository repository = new FakeRepository();
        repository.messages.add(message("msg_platform_user_1", SessionMessageRole.USER, RUN_ID, "msg_dispatch_1"));
        repository.messages.add(message("msg_platform_user_2", SessionMessageRole.USER, RUN_ID, "msg_dispatch_2"));

        RunDispatchMessageAnchorResolver.Anchor anchor =
                RunDispatchMessageAnchorResolver.resolve(repository, SESSION_ID, RUN_ID);

        assertThat(anchor.conflicted()).isTrue();
        assertThat(anchor.dispatchMessageId()).isNull();
    }

    private static SessionMessage message(
            String messageId,
            SessionMessageRole role,
            RunId runId,
            String remoteMessageId) {
        return new SessionMessage(
                new SessionMessageId(messageId),
                SESSION_ID,
                role,
                messageId,
                NOW,
                "trace_1234567890abcdef",
                runId,
                role == SessionMessageRole.ASSISTANT ? "opencode" : null,
                remoteMessageId,
                null,
                null,
                null,
                NOW);
    }

    private static final class FakeRepository implements SessionMessageRepository {
        private final List<SessionMessage> messages = new ArrayList<>();

        @Override
        public SessionMessage save(SessionMessage message) {
            messages.add(message);
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionMessage> findBySessionIdAndRemoteMessageId(
                SessionId sessionId,
                String remoteMessageId) {
            return Optional.empty();
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            return new PageResponse<>(List.copyOf(messages), pageRequest.page(), pageRequest.size(), messages.size());
        }
    }
}
