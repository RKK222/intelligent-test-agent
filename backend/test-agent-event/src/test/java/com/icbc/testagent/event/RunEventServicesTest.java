package com.icbc.testagent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeAppendResult;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeSnapshot;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunRuntimeStreamEvent;
import com.icbc.testagent.domain.run.RunRuntimeTail;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
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
    void liveBusSpringBeanStartsAsStandaloneBean() {
        new ApplicationContextRunner()
                .withBean(RunEventLiveBus.class)
                .run(context -> assertThat(context).hasSingleBean(RunEventLiveBus.class));
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
    void redisSummaryAppenderSkipsDatabaseAndPublishesRedisAssignedEvent() {
        RunEventRepository repository = mock(RunEventRepository.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_redis_summary_event");
        RunEventDraft draft = new RunEventDraft(
                runId, RunEventType.RUN_STARTED, "trace_redis", NOW, Map.of("status", "RUNNING"));
        RunEvent redisEvent = event(draft, 9L);
        when(runtimeStore.storageMode(runId)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(runtimeStore.appendDurable(draft)).thenReturn(new RunRuntimeAppendResult(redisEvent, false, 0, 1));
        RunEventAppender appender = new RunEventAppender(repository, liveBus, runtimeStore);

        StepVerifier.create(liveBus.stream(runId).take(1))
                .then(() -> assertThat(appender.append(draft)).isEqualTo(redisEvent))
                .assertNext(live -> assertThat(live.payload().seq()).isEqualTo(9L))
                .verifyComplete();

        verify(repository, never()).append(draft);
        verify(runtimeStore).appendDurable(draft);
    }

    @Test
    void redisSummaryTransientProjectsSnapshotBeforePublishingLiveEvent() {
        RunEventRepository repository = mock(RunEventRepository.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunEventLiveBus liveBus = mock(RunEventLiveBus.class);
        RunId runId = new RunId("run_redis_transient_event");
        RunEventDraft draft = new RunEventDraft(
                runId, RunEventType.MESSAGE_PART_DELTA, "trace_redis", NOW, Map.of("delta", "hello"));
        when(runtimeStore.storageMode(runId)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(runtimeStore.projectTransient(draft)).thenReturn(true);
        RunEventAppender appender = new RunEventAppender(repository, liveBus, runtimeStore);

        assertThat(appender.publishTransient(draft)).isTrue();

        var ordered = inOrder(runtimeStore, liveBus);
        ordered.verify(runtimeStore).projectTransient(draft);
        ordered.verify(liveBus).publishTransient(draft);
        verify(repository, never()).append(draft);
    }

    @Test
    void redisSummaryDoesNotPublishStatusEventRejectedByRuntimeStore() {
        RunEventRepository repository = mock(RunEventRepository.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunEventLiveBus liveBus = mock(RunEventLiveBus.class);
        RunId runId = new RunId("run_redis_terminal_late_event");
        RunEventDraft durable = new RunEventDraft(
                runId, RunEventType.RUN_STARTED, "trace_late_durable", NOW, Map.of("status", "RUNNING"));
        RunEventDraft transientDraft = new RunEventDraft(
                runId, RunEventType.RUN_STARTED, "trace_late_transient", NOW, Map.of("status", "RUNNING"));
        RunEvent ignoredEvent = event(durable, 10L);
        when(runtimeStore.appendDurable(durable))
                .thenReturn(new RunRuntimeAppendResult(ignoredEvent, false, 0, 1, false));
        when(runtimeStore.projectTransient(transientDraft)).thenReturn(false);
        RunEventAppender appender = new RunEventAppender(repository, liveBus, runtimeStore);

        assertThat(appender.append(durable, RunStorageMode.REDIS_SUMMARY)).isEqualTo(ignoredEvent);
        assertThat(appender.publishTransient(transientDraft, RunStorageMode.REDIS_SUMMARY)).isFalse();

        verify(liveBus, never()).publishDurable(ignoredEvent);
        verify(liveBus, never()).publishTransient(transientDraft);
        verify(repository, never()).append(durable);
    }

    @Test
    void explicitLegacyModeKeepsDatabaseAsFactWhenShadowRedisIsUnavailable() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunEventLiveBus liveBus = mock(RunEventLiveBus.class);
        RunId runId = new RunId("run_legacy_shadow_failure");
        RunEventDraft draft = new RunEventDraft(
                runId, RunEventType.RUN_STARTED, "trace_legacy", NOW, Map.of("status", "RUNNING"));
        when(runtimeStore.findManifest(runId)).thenThrow(new IllegalStateException("redis unavailable"));
        RunEventAppender appender = new RunEventAppender(repository, liveBus, runtimeStore);

        RunEvent event = appender.append(draft, RunStorageMode.LEGACY_FULL);

        assertThat(event.seq()).isEqualTo(1L);
        verify(liveBus).publishDurable(event);
    }

    @Test
    void liveBusStreamAllPublishesLocalEventsWithoutBreakingRunScopedStream() {
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");

        StepVerifier.create(Flux.zip(liveBus.streamAll().take(1), liveBus.stream(runId).take(1)))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.QUESTION_ASKED,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("requestId", "q_1"))))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1().payload().type()).isEqualTo("question.asked");
                    assertThat(tuple.getT2().payload().type()).isEqualTo("question.asked");
                    assertThat(tuple.getT2().payload().runId()).isEqualTo(runId.value());
                })
                .verifyComplete();
    }

    @Test
    void liveBusKeepsAcceptingEventsAfterSubscriberCompletes() {
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");

        StepVerifier.create(liveBus.stream(runId).take(1))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("delta", "first"))))
                .assertNext(event -> assertThat(event.payload().payload()).containsEntry("delta", "first"))
                .verifyComplete();

        StepVerifier.create(liveBus.stream(runId).take(1))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("delta", "second"))))
                .assertNext(event -> assertThat(event.payload().payload()).containsEntry("delta", "second"))
                .verifyComplete();
    }

    @Test
    void liveBusDropsConcurrentOverflowWithoutTerminatingChannel() {
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");

        StepVerifier.create(liveBus.stream(runId), 0)
                .then(() -> publishManyConcurrently(liveBus, runId, 16, 200))
                .thenCancel()
                .verify();

        StepVerifier.create(liveBus.stream(runId).take(1))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("delta", "after-overflow"))))
                .assertNext(event -> assertThat(event.payload().payload()).containsEntry("delta", "after-overflow"))
                .verifyComplete();
    }

    private static void publishManyConcurrently(
            RunEventLiveBus liveBus,
            RunId runId,
            int workerCount,
            int eventsPerWorker) {
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        for (int worker = 0; worker < workerCount; worker++) {
            int workerIndex = worker;
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    for (int index = 0; index < eventsPerWorker; index++) {
                        liveBus.publishTransient(new RunEventDraft(
                                runId,
                                RunEventType.MESSAGE_PART_DELTA,
                                "trace_1234567890abcdef",
                                NOW,
                                Map.of("worker", workerIndex, "index", index)));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException exception) {
                    failure.compareAndSet(null, exception);
                }
            });
        }
        start.countDown();
        executor.shutdown();
        try {
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("publisher workers were interrupted", exception);
        }
        assertThat(failure.get()).isNull();
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
    void sseStreamSubscribesToLiveEventsWhileInitialSnapshotIsStillLoading() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper(),
                liveBus);

        StepVerifier.create(streamService.streamAfterWithSnapshot(
                        runId,
                        "0",
                        Duration.ofSeconds(30),
                        50,
                        Flux.never()).take(1))
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("messageID", "msg_1", "partID", "part_1", "delta", "streaming"))))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("message.part.delta");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().payload()).containsEntry("delta", "streaming");
                })
                .verifyComplete();
    }

    @Test
    void sseStreamServiceExposesDurablePayloadSnapshotForHistoryApis() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper());
        repository.append(new RunEventDraft(
                runId,
                RunEventType.PERMISSION_ASKED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("sessionId", "ses_child", "requestId", "perm_1")));

        List<RunEventSsePayload> payloads = streamService.snapshotDurablePayloads(runId, 0, 50);

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).seq()).isEqualTo(1);
        assertThat(payloads.get(0).type()).isEqualTo("permission.asked");
        assertThat(payloads.get(0).payload()).containsEntry("requestId", "perm_1");
    }

    @Test
    void sseStreamServiceExposesDurablePayloadSnapshotByRootSessionForSessionHistoryApis() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper());
        repository.append(new RunEventDraft(
                runId,
                RunEventType.QUESTION_ASKED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("rootSessionId", "ses_root", "sessionId", "ses_child", "requestId", "q_1")));

        List<RunEventSsePayload> payloads =
                streamService.snapshotDurablePayloadsByRootSessionId("ses_root", 0, 50);

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).type()).isEqualTo("question.asked");
        assertThat(payloads.get(0).payload()).containsEntry("requestId", "q_1");
    }

    @Test
    void sseStreamMergesDurableReplayAndLocalLiveBusEvents() {
        FakeRunEventRepository repository = new FakeRunEventRepository();
        RunEventAppender appender = new RunEventAppender(repository);
        RunEventLiveBus liveBus = new RunEventLiveBus();
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSseStreamService streamService = new RunEventSseStreamService(
                new RunEventReplayService(repository),
                new RunEventSseMapper(),
                liveBus);
        appender.append(new RunEventDraft(
                runId,
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("status", "RUNNING")));

        StepVerifier.create(streamService.streamAfter(
                        runId,
                        "0",
                        Duration.ofSeconds(30),
                        50).take(2))
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("1");
                    assertThat(event.event()).isEqualTo("run.started");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().payload()).containsEntry("status", "RUNNING");
                })
                .then(() -> liveBus.publishTransient(new RunEventDraft(
                        runId,
                        RunEventType.MESSAGE_PART_DELTA,
                        "trace_1234567890abcdef",
                        NOW,
                        Map.of("delta", "live"))))
                .assertNext(event -> {
                    assertThat(event.id()).isNull();
                    assertThat(event.event()).isEqualTo("message.part.delta");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().payload()).containsEntry("delta", "live");
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

    @Test
    void redisSummarySseEmitsTransientResetSnapshotThenDurableStreamWithoutDatabasePolling() {
        RunEventRepository repository = mock(RunEventRepository.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunId runId = new RunId("run_redis_reset_event");
        RunEventDraft snapshotDraft = new RunEventDraft(
                runId, RunEventType.MESSAGE_UPDATED, "trace_redis", NOW, Map.of("messageId", "msg_1"));
        RunEvent durable = event(new RunEventDraft(
                runId, RunEventType.RUN_SUCCEEDED, "trace_redis", NOW, Map.of("status", "SUCCEEDED")), 8L);
        RunRuntimeManifest manifest = manifest(runId, 8L, 7L, 2L, true);
        RunRuntimeReplay replay = new RunRuntimeReplay(
                manifest,
                new RunRuntimeSnapshot(runId, 7L, 7L, 2L, List.of(snapshotDraft), NOW),
                List.of(),
                true,
                "CURSOR_BEFORE_EARLIEST_OR_DETAILS_TRUNCATED");
        when(runtimeStore.storageMode(runId)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(runtimeStore.replayAfter(runId, 1L, 50)).thenReturn(replay);
        when(runtimeStore.tailAfter(runId, 7L, 50)).thenReturn(new RunRuntimeTail(
                manifest,
                RunRuntimeSnapshot.empty(runId),
                List.of(new RunRuntimeStreamEvent(8L, true, 8L, new RunEventDraft(
                        runId, durable.type(), durable.traceId(), durable.occurredAt(), durable.payload()))),
                false,
                null));
        RunEventSseStreamService service = new RunEventSseStreamService(
                new RunEventReplayService(repository, runtimeStore),
                new RunEventSseMapper(),
                new RunEventLiveBus());

        StepVerifier.create(service.streamAfter(runId, "1", Duration.ofMillis(10), 50).take(2))
                .assertNext(reset -> {
                    assertThat(reset.id()).isNull();
                    assertThat(reset.event()).isEqualTo("run.snapshot.reset");
                    assertThat(reset.data()).isNotNull();
                    assertThat(reset.data().payload()).containsEntry("resetGeneration", 2L);
                    assertThat(reset.data().payload()).containsKey("snapshot");
                })
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("8");
                    assertThat(event.event()).isEqualTo("run.succeeded");
                })
                .verifyComplete();

        verify(repository, never()).findByRunIdAfter(runId, 1L, 50);
        verify(runtimeStore).replayAfter(runId, 1L, 50);
        verify(runtimeStore).tailAfter(runId, 7L, 50);
    }

    @Test
    void redisSummarySsePaginatesRuntimeStreamWithoutLosingEventsPastBatchLimit() {
        RunEventRepository repository = mock(RunEventRepository.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunId runId = new RunId("run_redis_runtime_pages");
        RunRuntimeManifest manifest = manifest(runId, 5L, 1L, 0L, false);
        RunRuntimeReplay initial = new RunRuntimeReplay(
                manifest,
                new RunRuntimeSnapshot(runId, 0L, 10L, 0L, List.of(), NOW),
                List.of(),
                false,
                null);
        when(runtimeStore.storageMode(runId)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(runtimeStore.replayAfter(runId, 0L, 2)).thenReturn(initial);
        when(runtimeStore.tailAfter(runId, 10L, 2)).thenReturn(tail(
                manifest, 11L, 1L, 12L, 2L));
        when(runtimeStore.tailAfter(runId, 12L, 2)).thenReturn(tail(
                manifest, 13L, 3L, 14L, 4L));
        when(runtimeStore.tailAfter(runId, 14L, 2)).thenReturn(tail(
                manifest, 15L, 5L));
        RunEventSseStreamService service = new RunEventSseStreamService(
                new RunEventReplayService(repository, runtimeStore),
                new RunEventSseMapper(),
                new RunEventLiveBus());

        StepVerifier.create(service.streamAfter(runId, "0", Duration.ofMillis(10), 2).take(6))
                .assertNext(reset -> assertThat(reset.event()).isEqualTo("run.snapshot.reset"))
                .thenConsumeWhile(
                        event -> event.id() != null,
                        event -> assertThat(event.event()).isEqualTo("todo.updated"))
                .verifyComplete();

        verify(repository, never()).findByRunIdAfter(runId, 0L, 2);
        verify(runtimeStore).tailAfter(runId, 10L, 2);
        verify(runtimeStore).tailAfter(runId, 12L, 2);
        verify(runtimeStore).tailAfter(runId, 14L, 2);
    }

    private static RunRuntimeTail tail(
            RunRuntimeManifest manifest,
            long... runtimeVersionAndDurableSeq) {
        List<RunRuntimeStreamEvent> events = new ArrayList<>();
        for (int index = 0; index < runtimeVersionAndDurableSeq.length; index += 2) {
            long runtimeVersion = runtimeVersionAndDurableSeq[index];
            long seq = runtimeVersionAndDurableSeq[index + 1];
            events.add(new RunRuntimeStreamEvent(
                    runtimeVersion,
                    true,
                    seq,
                    new RunEventDraft(
                            manifest.runId(),
                            RunEventType.TODO_UPDATED,
                            "trace_runtime_page",
                            NOW,
                            Map.of("todos", List.of(), "pageSeq", seq))));
        }
        return new RunRuntimeTail(
                manifest, RunRuntimeSnapshot.empty(manifest.runId()), events, false, null);
    }

    private static RunEvent event(RunEventDraft draft, long seq) {
        return new RunEvent(
                new RunEventId("evt_redis_" + seq),
                draft.runId(),
                seq,
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                draft.payload(),
                draft.scopeContext());
    }

    private static RunRuntimeManifest manifest(
            RunId runId,
            long lastSeq,
            long earliestSeq,
            long resetGeneration,
            boolean truncated) {
        return new RunRuntimeManifest(
                runId,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_redis_event"),
                new SessionId("ses_redis_event"),
                new WorkspaceId("wrk_redis_event"),
                "opencode",
                "req_redis_event",
                "msg_dispatch_redis_event",
                "server-a",
                "bjp_server_a",
                "node_ocp_redis_event",
                "ocp_redis_event",
                "remote-session-redis-event",
                RunStatus.RUNNING,
                0,
                lastSeq,
                earliestSeq,
                resetGeneration,
                truncated,
                1,
                128,
                null,
                null,
                null,
                NOW.plus(Duration.ofHours(24)),
                NOW,
                NOW);
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

        @Override
        public List<RunEvent> findByRootSessionIdAfter(String rootSessionId, long lastSeq, int limit) {
            return events.stream()
                    .filter(event -> rootSessionId.equals(event.payload().get("rootSessionId")))
                    .filter(event -> event.seq() > lastSeq)
                    .limit(limit)
                    .toList();
        }
    }

}
