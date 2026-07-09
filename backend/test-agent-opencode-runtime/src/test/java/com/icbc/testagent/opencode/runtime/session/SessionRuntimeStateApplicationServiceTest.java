package com.icbc.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRuntimeAttention;
import com.icbc.testagent.domain.session.SessionRuntimeState;
import com.icbc.testagent.domain.session.SessionRuntimeStateRepository;
import com.icbc.testagent.domain.session.SessionRuntimeStateSummary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.event.RunEventLiveBus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;

/**
 * 验证用户级会话运行态服务能查询快照，并在 run/question 事件后推送刷新结果。
 */
class SessionRuntimeStateApplicationServiceTest {

    private static final UserId USER_ID = new UserId("usr_runtime_current");
    private static final Instant NOW = Instant.parse("2026-07-08T08:00:00Z");

    @Test
    void snapshotReturnsRepositorySummary() {
        SessionRuntimeStateSummary summary = summary(1, 0);
        SessionRuntimeStateApplicationService service = new SessionRuntimeStateApplicationService(
                new FakeRepository(List.of(summary)),
                new RunEventLiveBus(),
                Duration.ofHours(1));

        assertThat(service.snapshot(USER_ID)).isEqualTo(summary);
    }

    @Test
    void streamEmitsInitialSnapshotAndRefreshesAfterRunOrQuestionEvent() {
        RunEventLiveBus liveBus = new RunEventLiveBus();
        SessionRuntimeStateApplicationService service = new SessionRuntimeStateApplicationService(
                new FakeRepository(List.of(summary(0, 0), summary(1, 1))),
                liveBus,
                Duration.ofHours(1));

        StepVerifier.create(service.stream(USER_ID).take(2))
                .assertNext(summary -> {
                    assertThat(summary.runningCount()).isZero();
                    assertThat(summary.questionCount()).isZero();
                })
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        new RunId("run_runtime_question"),
                        RunEventType.QUESTION_ASKED,
                        "trace_runtime",
                        NOW,
                        Map.of("requestId", "q_1"))))
                .assertNext(summary -> {
                    assertThat(summary.runningCount()).isEqualTo(1);
                    assertThat(summary.questionCount()).isEqualTo(1);
                    assertThat(summary.sessions().get(0).attention()).isEqualTo(SessionRuntimeAttention.QUESTION);
                })
                .verifyComplete();
    }

    @Test
    void streamIgnoresMessageOnlyEventsToAvoidUnnecessaryStateQueries() {
        RunEventLiveBus liveBus = new RunEventLiveBus();
        FakeRepository repository = new FakeRepository(List.of(summary(0, 0), summary(1, 0)));
        SessionRuntimeStateApplicationService service = new SessionRuntimeStateApplicationService(
                repository,
                liveBus,
                Duration.ofHours(1));

        StepVerifier.create(service.stream(USER_ID).take(1))
                .assertNext(summary -> assertThat(summary.runningCount()).isZero())
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        new RunId("run_runtime_question"),
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_runtime",
                        NOW,
                        Map.of("delta", "ignored"))))
                .verifyComplete();
        assertThat(repository.calls()).isEqualTo(1);
    }

    private static SessionRuntimeStateSummary summary(int runningCount, int questionCount) {
        List<SessionRuntimeState> sessions = runningCount == 0
                ? List.of()
                : List.of(new SessionRuntimeState(
                        new SessionId("ses_runtime_question"),
                        new RunId("run_runtime_question"),
                        RunStatus.RUNNING,
                        questionCount > 0 ? SessionRuntimeAttention.QUESTION : null,
                        questionCount > 0 ? "evt_runtime_question" : null,
                        questionCount > 0 ? NOW : null,
                        NOW));
        return new SessionRuntimeStateSummary(runningCount, questionCount, sessions, NOW);
    }

    private static final class FakeRepository implements SessionRuntimeStateRepository {
        private final List<SessionRuntimeStateSummary> summaries;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeRepository(List<SessionRuntimeStateSummary> summaries) {
            this.summaries = summaries;
        }

        @Override
        public SessionRuntimeStateSummary findUserRuntimeState(UserId userId) {
            int index = Math.min(calls.getAndIncrement(), summaries.size() - 1);
            return summaries.get(index);
        }

        private int calls() {
            return calls.get();
        }
    }
}
