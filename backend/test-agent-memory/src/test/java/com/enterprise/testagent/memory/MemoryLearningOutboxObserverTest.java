package com.enterprise.testagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemoryLearningOutboxObserverTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void successfulManualRootRunCreatesLocatorOnlyOutbox() {
        RunRepository runs = mock(RunRepository.class);
        QaMemoryRepository memories = mock(QaMemoryRepository.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        Run run = run(ConversationSourceType.MANUAL);
        when(runs.findById(run.runId())).thenReturn(Optional.of(run));
        when(memories.isWhitelisted("usr_1")).thenReturn(true);
        when(memories.findApplicationIdByRuntimeWorkspace("wrk_1")).thenReturn(Optional.of("app_1"));
        MemoryLearningOutboxObserver observer = new MemoryLearningOutboxObserver(
                runs, memories, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        observer.onTerminal(run.runId(), RunStatus.SUCCEEDED, "trace_1");

        ArgumentCaptor<MemoryLearningJob> captor = ArgumentCaptor.forClass(MemoryLearningJob.class);
        verify(memories).enqueueLearningJob(captor.capture());
        assertThat(captor.getValue()).satisfies(job -> {
            assertThat(job.runId()).isEqualTo("run_1");
            assertThat(job.sessionId()).isEqualTo("ses_1");
            assertThat(job.workspaceId()).isEqualTo("wrk_1");
            assertThat(job.applicationId()).isEqualTo("app_1");
            assertThat(job.status()).isEqualTo("PENDING");
        });
    }

    @Test
    void scheduledRunIsNeverLearned() {
        RunRepository runs = mock(RunRepository.class);
        QaMemoryRepository memories = mock(QaMemoryRepository.class);
        QaMemoryProperties properties = new QaMemoryProperties();
        properties.setEnabled(true);
        Run run = run(ConversationSourceType.SCHEDULED_TASK);
        when(runs.findById(run.runId())).thenReturn(Optional.of(run));
        MemoryLearningOutboxObserver observer = new MemoryLearningOutboxObserver(
                runs, memories, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        observer.onTerminal(run.runId(), RunStatus.SUCCEEDED, "trace_1");

        verify(memories, never()).enqueueLearningJob(any());
    }

    private Run run(ConversationSourceType sourceType) {
        return new Run(
                new RunId("run_1"), new SessionId("ses_1"), new WorkspaceId("wrk_1"),
                RunStatus.SUCCEEDED, NOW, NOW, "trace_1", TokenUsage.empty(), null,
                sourceType, null, new UserId("usr_1"), "opencode", "chat-model");
    }
}
