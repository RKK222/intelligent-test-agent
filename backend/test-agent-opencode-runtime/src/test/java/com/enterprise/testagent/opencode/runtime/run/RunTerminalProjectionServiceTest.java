package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventScopeContext;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.RunDiffCounts;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeReplay;
import com.enterprise.testagent.domain.run.RunRuntimeSnapshot;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunTerminalProjection;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.run.summary.RunConversationSummarizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RunTerminalProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T01:00:00Z");
    private static final RunId RUN_ID = new RunId("run_terminal_projection");

    @Test
    void assistantSummaryIdUsesFrontendCompatibleStablePlatformMessageShape() {
        RunId generatedShape = new RunId("run_0123456789abcdef0123456789abcdef");

        assertThat(RunSummaryIdentifiers.assistant(generatedShape).value())
                .isEqualTo("msg_0123456789abcdef0123456789abcdef")
                .matches("msg_[0-9a-f]{32}");
    }

    @Test
    void projectsRootVisibleTextIntoDeterministicDualSummaryAndUsesAnchorStatusVersion() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeManifest manifest = manifest(2L, RunStatus.SUCCEEDED);
        RunRuntimeInput input = new RunRuntimeInput(
                RUN_ID,
                "执行回归 <context>不得持久化</context>",
                List.of(),
                "msg_dispatch_terminal",
                NOW.minusSeconds(10));
        List<RunEventDraft> snapshot = List.of(
                assistantUsage(),
                part("msg_root", "part_root", "最终回答", false),
                part("msg_child", "part_child", "子会话原文不得进入摘要", true));
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest));
        when(runtimeStore.findInput(RUN_ID)).thenReturn(Optional.of(input));
        when(runtimeStore.replayAfter(RUN_ID, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(new RunRuntimeReplay(
                        manifest,
                        new RunRuntimeSnapshot(RUN_ID, 8L, 8L, 0L, snapshot, NOW),
                        List.of(),
                        false,
                        null));
        when(runtimeStore.diffCounts(RUN_ID)).thenReturn(new RunDiffCounts(2, 1, 0));
        when(persistence.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunTerminalProjectionService service = new RunTerminalProjectionService(
                runtimeStore, persistence, new RunConversationSummarizer());

        assertThat(service.project(
                RUN_ID,
                RunStatus.SUCCEEDED,
                "REMOTE_ROOT",
                "COMPLETED",
                "远端失败 token=super-secret-value",
                true,
                "trace_terminal"))
                .isEqualTo(RunTerminalProjectionResult.APPLIED);

        ArgumentCaptor<RunTerminalProjection> captor = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistence).persistTerminal(captor.capture());
        RunTerminalProjection projection = captor.getValue();
        assertThat(projection.expectedStatusVersion()).isEqualTo(1L);
        assertThat(projection.lastEventSeq()).isEqualTo(8L);
        assertThat(projection.rootRemoteSessionId()).isEqualTo("remote-root");
        assertThat(projection.safeErrorMessage())
                .contains("远端失败", "[REDACTED]")
                .doesNotContain("super-secret-value");
        assertThat(projection.diffCounts()).isEqualTo(new RunDiffCounts(2, 1, 0));
        assertThat(projection.tokenUsage()).isEqualTo(new com.enterprise.testagent.domain.run.TokenUsage(11L, 22L, 3L, 4L, 5L));
        assertThat(projection.costUsd()).isEqualByComparingTo(new BigDecimal("0.125"));
        assertThat(projection.summaries()).extracting(summary -> summary.role())
                .containsExactly(SessionMessageRole.USER, SessionMessageRole.ASSISTANT);
        assertThat(projection.summaries().get(0).content())
                .contains("执行回归")
                .doesNotContain("不得持久化");
        assertThat(projection.summaries().get(1).content())
                .isEqualTo("最终回答")
                .doesNotContain("子会话原文");
        assertThat(projection.summaries().get(1).messageId())
                .isEqualTo(RunSummaryIdentifiers.assistant(RUN_ID));
        assertThat(projection.lastRemoteMessageId()).isEqualTo("msg_root");
        assertThat(projection.lastRemotePartId()).isEqualTo("part_root");
    }

    @Test
    void cancellingTransitionDoesNotChangePostgresqlAnchorCasVersion() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeManifest manifest = manifest(3L, RunStatus.CANCELLED);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest));
        when(runtimeStore.findInput(RUN_ID)).thenReturn(Optional.of(new RunRuntimeInput(
                RUN_ID, "取消当前任务", List.of(), "msg_dispatch_terminal", NOW.minusSeconds(10))));
        when(runtimeStore.replayAfter(RUN_ID, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(new RunRuntimeReplay(
                        manifest,
                        new RunRuntimeSnapshot(RUN_ID, 8L, 8L, 0L, List.of(), NOW),
                        List.of(),
                        false,
                        null));
        when(runtimeStore.diffCounts(RUN_ID)).thenReturn(RunDiffCounts.empty());
        when(persistence.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunTerminalProjectionService service = new RunTerminalProjectionService(
                runtimeStore, persistence, new RunConversationSummarizer());

        service.project(
                RUN_ID,
                RunStatus.CANCELLED,
                "USER_CANCEL",
                "CANCELLED",
                null,
                true,
                "trace_terminal");

        ArgumentCaptor<RunTerminalProjection> captor = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistence).persistTerminal(captor.capture());
        assertThat(captor.getValue().expectedStatusVersion()).isEqualTo(1L);
    }

    private RunEventDraft part(String messageId, String partId, String text, boolean child) {
        RunEventScopeContext scope = child
                ? new RunEventScopeContext(
                        RUN_ID, "remote-root", "remote-child", "remote-root", true,
                        "msg_task", "part_task", "call_task", 2L, true)
                : RunEventScopeContext.root(RUN_ID, "remote-root");
        return new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_PART_UPDATED,
                "trace_terminal",
                NOW,
                Map.of("part", Map.of(
                        "id", partId,
                        "messageID", messageId,
                        "type", "text",
                        "text", text)),
                scope);
    }

    private RunEventDraft assistantUsage() {
        return new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_UPDATED,
                "trace_terminal",
                NOW,
                Map.of("message", Map.of(
                        "id", "msg_root",
                        "role", "assistant",
                        "tokens", Map.of(
                                "input", 11,
                                "output", 22,
                                "reasoning", 3,
                                "cache", Map.of("read", 4, "write", 5)),
                        "cost", "0.125")),
                RunEventScopeContext.root(RUN_ID, "remote-root"));
    }

    private RunRuntimeManifest manifest(long statusVersion, RunStatus status) {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_terminal_projection"),
                new SessionId("ses_terminal_projection"),
                new WorkspaceId("wrk_terminal_projection"),
                "opencode",
                "request-terminal",
                "msg_dispatch_terminal",
                "server-terminal",
                "bjp_terminal",
                "node_ocp_terminal",
                "ocp_terminal",
                "remote-root",
                status,
                statusVersion,
                8L,
                1L,
                0L,
                false,
                8L,
                1024L,
                null,
                null,
                null,
                NOW.plusSeconds(86_400),
                NOW.minusSeconds(20),
                NOW);
    }
}
