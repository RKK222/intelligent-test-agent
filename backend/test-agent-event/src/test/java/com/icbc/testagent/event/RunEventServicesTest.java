package com.icbc.testagent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

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

    @Test
    void sseMapperOmitsIdForTransientEvent() {
        RunEventDraft draft = new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.MESSAGE_PART_DELTA,
                "trace_1234567890abcdef",
                NOW,
                Map.of("messageID", "msg_1", "partID", "part_1", "delta", "hello"));

        ServerSentEvent<RunEventSsePayload> sse = new RunEventSseMapper()
                .toTransientSse(RunEventSsePayload.transientFrom(draft, "evt_live_1234567890abcdef"));

        assertThat(sse.id()).isNull();
        assertThat(sse.event()).isEqualTo("message.part.delta");
        assertThat(sse.data()).isNotNull();
        assertThat(sse.data().eventId()).isEqualTo("evt_live_1234567890abcdef");
        assertThat(sse.data().seq()).isZero();
        assertThat(sse.data().payload()).containsEntry("delta", "hello");
    }

    @Test
    void appenderPublishesDurableEventsToLiveBusAfterPersistence() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventAppender appender = new RunEventAppender(repository, liveBus);

        StepVerifier.create(liveBus.stream(runId).take(1))
                .then(() -> appender.append(new RunEventDraft(
                        runId,
                        RunEventType.RUN_STARTED,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("status", "RUNNING"))))
                .assertNext(event -> {
                    RunEventSsePayload payload = event.payload();
                    assertThat(event.durable()).isTrue();
                    assertThat(payload.eventId()).isEqualTo("evt_1");
                    assertThat(payload.seq()).isEqualTo(1L);
                    assertThat(payload.type()).isEqualTo("run.started");
                })
                .verifyComplete();
    }

    @Test
    void sseStreamPollsRepositoryAndAdvancesCursor() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventAppender appender = new RunEventAppender(repository);
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper());
        appender.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of()));
        appender.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.ASSISTANT_MESSAGE_DELTA,
                "trace_1234567890abcdef",
                NOW,
                Map.of("text", "hello")));

        StepVerifier.create(streamService.streamAfter(
                        new RunId("run_1234567890abcdef"),
                        "1",
                        Duration.ofMillis(10),
                        50).take(1))
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("2");
                    assertThat(event.event()).isEqualTo("assistant.message.delta");
                })
                .verifyComplete();
    }

    @Test
    void sseStreamMergesTransientLiveBusEventsWithoutSseId() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper(),
                liveBus);

        StepVerifier.create(streamService.streamAfter(
                        runId,
                        "0",
                        Duration.ofSeconds(30),
                        50).take(1))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("messageID", "msg_1", "partID", "part_1", "delta", "hello"))))
                .assertNext(event -> {
                    assertThat(event.id()).isNull();
                    assertThat(event.event()).isEqualTo("message.part.delta");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().seq()).isZero();
                    assertThat(event.data().payload()).containsEntry("delta", "hello");
                })
                .verifyComplete();
    }

    @Test
    void sseStreamContinuesPollingAfterTransientReplayFailure() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        repository.remainingFindFailures = 1;
        RunEventAppender appender = new RunEventAppender(repository);
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper());
        appender.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of()));

        StepVerifier.create(streamService.streamAfter(
                        new RunId("run_1234567890abcdef"),
                        "0",
                        Duration.ofMillis(10),
                        50).take(1))
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("1");
                    assertThat(event.event()).isEqualTo("run.started");
                })
                .verifyComplete();
    }

    private static final class FakeRunEventRepository implements RunEventRepository {

        private final List<RunEvent> events = new ArrayList<>();
        private int remainingFindFailures;

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
            if (remainingFindFailures > 0) {
                remainingFindFailures--;
                throw new IllegalStateException("connection closed");
            }
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.seq() > lastSeq)
                    .limit(limit)
                    .toList();
        }
    }
}
