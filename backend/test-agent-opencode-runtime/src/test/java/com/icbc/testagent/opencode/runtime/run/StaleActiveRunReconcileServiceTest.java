package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * 验证 stale active Run 收敛只处理真正失联的运行，避免误杀仍有输出或等待用户处理 ask 的会话。
 */
class StaleActiveRunReconcileServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TRACE_ID = "trace_stale1234567890abcdef";
    private static final RunId RUN_ID = new RunId("run_stale1234567890abcdef");

    @Test
    void marksStaleActiveRunFailedAndAppendsRunFailedEventWhenNoRecentOutputOrPendingAsk() {
        FakeRunRepository runs = new FakeRunRepository();
        Run running = run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(3 * 3600));
        runs.save(running);
        FakeRunEventRepository events = new FakeRunEventRepository();
        RecordingRunOutputActivityStore activity = new RecordingRunOutputActivityStore(false);
        StaleActiveRunReconcileService service = new StaleActiveRunReconcileService(
                runs,
                new RunEventAppender(events),
                activity,
                null,
                CLOCK);

        StaleActiveRunReconcileService.Result result = service.reconcile(TRACE_ID, () -> false);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(runs.findById(RUN_ID)).get().extracting(Run::status).isEqualTo(RunStatus.FAILED);
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.type().wireName()).isEqualTo("run.failed");
            assertThat(event.payload()).containsEntry("message", StaleActiveRunReconcileService.TIMEOUT_MESSAGE);
        });
    }

    @Test
    void skipsRunWhenRecentOutputMarkerExists() {
        FakeRunRepository runs = new FakeRunRepository();
        Run running = run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(3 * 3600));
        runs.save(running);
        FakeRunEventRepository events = new FakeRunEventRepository();
        StaleActiveRunReconcileService service = new StaleActiveRunReconcileService(
                runs,
                new RunEventAppender(events),
                new RecordingRunOutputActivityStore(true),
                null,
                CLOCK);

        StaleActiveRunReconcileService.Result result = service.reconcile(TRACE_ID, () -> false);

        assertThat(result.recentOutputSkippedCount()).isEqualTo(1);
        assertThat(runs.findById(RUN_ID)).get().extracting(Run::status).isEqualTo(RunStatus.RUNNING);
        assertThat(events.events).isEmpty();
    }

    @Test
    void skipsRunWhenLatestAskHasNotBeenHandled() {
        FakeRunRepository runs = new FakeRunRepository();
        Run running = run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(3 * 3600));
        runs.save(running);
        FakeRunEventRepository events = new FakeRunEventRepository();
        StaleActiveRunReconcileService service = new StaleActiveRunReconcileService(
                runs,
                new RunEventAppender(events),
                new RecordingRunOutputActivityStore(false, true),
                null,
                CLOCK);

        StaleActiveRunReconcileService.Result result = service.reconcile(TRACE_ID, () -> false);

        assertThat(result.pendingAskSkippedCount()).isEqualTo(1);
        assertThat(runs.findById(RUN_ID)).get().extracting(Run::status).isEqualTo(RunStatus.RUNNING);
        assertThat(events.events).isEmpty();
    }

    @Test
    void skipsWhenRunBecameFreshOrTerminalBeforeCasWrite() {
        FakeRunRepository runs = new FakeRunRepository();
        Run fresh = run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(60));
        runs.staleCandidates.add(run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(3 * 3600)));
        runs.save(fresh);
        FakeRunEventRepository events = new FakeRunEventRepository();
        StaleActiveRunReconcileService service = new StaleActiveRunReconcileService(
                runs,
                new RunEventAppender(events),
                new RecordingRunOutputActivityStore(false),
                null,
                CLOCK);

        StaleActiveRunReconcileService.Result result = service.reconcile(TRACE_ID, () -> false);

        assertThat(result.freshSkippedCount()).isEqualTo(1);
        assertThat(events.events).isEmpty();
    }

    @Test
    void skipsWhenRedisActivityLookupFailsConservatively() {
        FakeRunRepository runs = new FakeRunRepository();
        runs.save(run(RUN_ID, RunStatus.RUNNING, NOW.minusSeconds(3 * 3600)));
        FakeRunEventRepository events = new FakeRunEventRepository();
        StaleActiveRunReconcileService service = new StaleActiveRunReconcileService(
                runs,
                new RunEventAppender(events),
                new ThrowingRunOutputActivityStore(),
                null,
                CLOCK);

        StaleActiveRunReconcileService.Result result = service.reconcile(TRACE_ID, () -> false);

        assertThat(result.recentOutputSkippedCount()).isEqualTo(1);
        assertThat(runs.findById(RUN_ID)).get().extracting(Run::status).isEqualTo(RunStatus.RUNNING);
        assertThat(events.events).isEmpty();
    }

    private static Run run(RunId runId, RunStatus status, Instant updatedAt) {
        return new Run(
                runId,
                new SessionId("ses_stale1234567890abcdef"),
                new WorkspaceId("wrk_stale1234567890abcdef"),
                status,
                NOW.minusSeconds(4 * 3600),
                updatedAt,
                TRACE_ID);
    }

    private static class FakeRunRepository implements RunRepository {
        private final List<Run> saved = new CopyOnWriteArrayList<>();
        private final List<Run> staleCandidates = new ArrayList<>();

        @Override
        public Run save(Run run) {
            saved.add(run);
            if (staleCandidates.isEmpty()) {
                staleCandidates.add(run);
            }
            return run;
        }

        @Override
        public Run saveIfStatus(Run run, RunStatus expectedStatus) {
            Optional<Run> current = findById(run.runId());
            if (current.isPresent() && current.get().status() != expectedStatus) {
                return current.get();
            }
            return save(run);
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return saved.stream().filter(run -> run.runId().equals(runId)).reduce((first, second) -> second);
        }

        @Override
        public List<Run> findStaleActiveRuns(Instant updatedBefore, int limit) {
            return staleCandidates.stream()
                    .filter(run -> run.updatedAt().isBefore(updatedBefore))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class FakeRunEventRepository implements RunEventRepository {
        private final List<RunEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public RunEvent append(RunEventDraft draft) {
            RunEvent event = new RunEvent(
                    new RunEventId("evt_stale" + (events.size() + 1) + "1234567890abcdef"),
                    draft.runId(),
                    events.size() + 1L,
                    draft.type(),
                    draft.traceId(),
                    draft.occurredAt(),
                    draft.payload(),
                    draft.scopeContext());
            events.add(event);
            return event;
        }

        @Override
        public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
            return List.of();
        }
    }

    private static class RecordingRunOutputActivityStore extends RunActivityStateStore {
        private final boolean recentOutput;
        private final boolean pendingAsk;

        RecordingRunOutputActivityStore(boolean recentOutput) {
            this(recentOutput, false);
        }

        RecordingRunOutputActivityStore(boolean recentOutput, boolean pendingAsk) {
            super(null);
            this.recentOutput = recentOutput;
            this.pendingAsk = pendingAsk;
        }

        @Override
        boolean hasRecentOutput(RunId runId) {
            return recentOutput;
        }

        @Override
        boolean hasPendingAsk(RunId runId) {
            return pendingAsk;
        }
    }

    private static final class ThrowingRunOutputActivityStore extends RecordingRunOutputActivityStore {
        ThrowingRunOutputActivityStore() {
            super(false);
        }

        @Override
        boolean hasRecentOutput(RunId runId) {
            throw new IllegalStateException("redis unavailable");
        }
    }
}
