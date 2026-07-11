package com.icbc.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SideQuestionTerminalServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final RunId RUN_ID = new RunId("run_sidequestionterminal01");

    @Test
    void casWinnerPersistsSingleSucceededTerminalEvent() {
        RunRepository runs = mock(RunRepository.class);
        RunEventAppender events = mock(RunEventAppender.class);
        Run running = running();
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(running));
        when(runs.saveIfStatus(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        SideQuestionTerminalService service = new SideQuestionTerminalService(runs, events);

        boolean won = service.succeed(
                RUN_ID,
                Map.of("sideQuestion", true, "answer", "完成", "compacted", false),
                "trace_sidequestionterminal01");

        assertThat(won).isTrue();
        ArgumentCaptor<Run> runCaptor = ArgumentCaptor.forClass(Run.class);
        verify(runs).saveIfStatus(runCaptor.capture(), org.mockito.ArgumentMatchers.eq(RunStatus.RUNNING));
        assertThat(runCaptor.getValue().status()).isEqualTo(RunStatus.SUCCEEDED);
        ArgumentCaptor<RunEventDraft> eventCaptor = ArgumentCaptor.forClass(RunEventDraft.class);
        verify(events).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RunEventType.RUN_SUCCEEDED);
        assertThat(eventCaptor.getValue().payload()).containsEntry("answer", "完成");
    }

    @Test
    void casLoserAndAlreadyTerminalRunDoNotAppendAnotherTerminalEvent() {
        RunRepository runs = mock(RunRepository.class);
        RunEventAppender events = mock(RunEventAppender.class);
        Run running = running();
        Run alreadySucceeded = running.succeed(NOW.plusSeconds(2));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(running), Optional.of(alreadySucceeded));
        when(runs.saveIfStatus(any(), any())).thenReturn(alreadySucceeded);
        SideQuestionTerminalService service = new SideQuestionTerminalService(runs, events);

        assertThat(service.fail(RUN_ID, "清理任务已收敛", "trace_sidequestionterminal01")).isFalse();
        assertThat(service.fail(RUN_ID, "重复清理", "trace_sidequestionterminal01")).isFalse();

        verify(events, never()).append(any());
    }

    @Test
    void failedTerminalPayloadIsSafeAndDoesNotExposeThrowableDetails() {
        RunRepository runs = mock(RunRepository.class);
        RunEventAppender events = mock(RunEventAppender.class);
        Run running = running();
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(running));
        when(runs.saveIfStatus(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        SideQuestionTerminalService service = new SideQuestionTerminalService(runs, events);

        assertThat(service.fail(RUN_ID, "旁路问答暂时失败", "trace_sidequestionterminal01")).isTrue();

        ArgumentCaptor<RunEventDraft> eventCaptor = ArgumentCaptor.forClass(RunEventDraft.class);
        verify(events).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RunEventType.RUN_FAILED);
        assertThat(eventCaptor.getValue().payload())
                .containsEntry("sideQuestion", true)
                .containsEntry("message", "旁路问答暂时失败")
                .doesNotContainKeys("error", "stackTrace", "cause");
    }

    private Run running() {
        return new Run(
                RUN_ID,
                new SessionId("ses_sidequestionterminal01"),
                new WorkspaceId("wrk_sidequestionterminal01"),
                RunStatus.RUNNING,
                NOW,
                NOW.plusSeconds(1),
                "trace_sidequestionterminal01");
    }
}
