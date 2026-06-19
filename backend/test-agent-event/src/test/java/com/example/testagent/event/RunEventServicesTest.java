package com.example.testagent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventId;
import com.example.testagent.domain.event.RunEventRepository;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

class RunEventServicesTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void appenderStoresDraftAndReturnsPersistedEvent() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventAppender appender = new RunEventAppender(repository);

        RunEvent event = appender.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("status", "running")));

        assertThat(event.seq()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo(RunEventType.RUN_STARTED);
    }

    @Test
    void replayServiceParsesLastEventIdAndRejectsInvalidValues() {
        RunEventReplayService replayService = new RunEventReplayService(new FakeRunEventRepository());

        assertThat(replayService.resolveLastSeq("42")).isEqualTo(42L);
        assertThat(replayService.resolveLastSeq(null)).isZero();
        assertThatThrownBy(() -> replayService.resolveLastSeq("not-a-number"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void sseMapperUsesSeqAsIdAndWireNameAsEventName() {
        RunEvent event = new RunEvent(
                new RunEventId("evt_1234567890abcdef"),
                new RunId("run_1234567890abcdef"),
                7L,
                RunEventType.TOOL_FINISHED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("status", "success"));

        ServerSentEvent<RunEventSsePayload> sse = new RunEventSseMapper().toSse(event);

        assertThat(sse.id()).isEqualTo("7");
        assertThat(sse.event()).isEqualTo("tool.finished");
        assertThat(sse.data()).isNotNull();
        assertThat(sse.data().payload()).containsEntry("status", "success");
    }

    private static final class FakeRunEventRepository implements RunEventRepository {

        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public RunEvent append(RunEventDraft draft) {
            RunEvent event = new RunEvent(
                    new RunEventId("evt_" + (events.size() + 1)),
                    draft.runId(),
                    events.size() + 1L,
                    draft.type(),
                    draft.traceId(),
                    draft.occurredAt(),
                    draft.payload());
            events.add(event);
            return event;
        }

        @Override
        public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.seq() > lastSeq)
                    .limit(limit)
                    .toList();
        }
    }
}
