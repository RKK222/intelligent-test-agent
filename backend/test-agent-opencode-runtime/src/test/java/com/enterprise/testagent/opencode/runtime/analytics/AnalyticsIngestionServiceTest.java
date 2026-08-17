package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsEventSink;
import com.enterprise.testagent.domain.analytics.AnalyticsRuntimeOutboxReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AnalyticsIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T05:00:00Z");
    private AnalyticsEventOutboxRepository outbox;
    private AnalyticsEventSink sink;
    private AnalyticsIngestionService service;

    @BeforeEach
    void setUp() {
        outbox = Mockito.mock(AnalyticsEventOutboxRepository.class);
        sink = Mockito.mock(AnalyticsEventSink.class);
        service = new AnalyticsIngestionService(outbox, sink, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void successfulBatchIsAcknowledgedAfterClickHouseWrite() {
        AnalyticsEventOutboxRepository.Event event = event(7L);
        when(outbox.pending(500, NOW)).thenReturn(List.of(event));

        AnalyticsIngestionService.Result result = service.ingestDue();

        verify(sink).append(List.of(event), NOW);
        verify(outbox).markPublished(List.of(7L), NOW);
        verify(outbox, never()).markFailed(any(), any(), any());
        assertThat(result).isEqualTo(new AnalyticsIngestionService.Result(1, 1, 0));
    }

    @Test
    void clickHouseFailureKeepsEventAndSchedulesRetryWithoutThrowing() {
        AnalyticsEventOutboxRepository.Event event = event(9L);
        when(outbox.pending(500, NOW)).thenReturn(List.of(event));
        Mockito.doThrow(new IllegalStateException("secret endpoint details"))
                .when(sink).append(List.of(event), NOW);

        AnalyticsIngestionService.Result result = service.ingestDue();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(outbox).markFailed(eq(List.of(9L)), error.capture(), eq(NOW.plusSeconds(60)));
        verify(outbox, never()).markPublished(any(), any());
        assertThat(error.getValue()).isEqualTo("IllegalStateException");
        assertThat(result).isEqualTo(new AnalyticsIngestionService.Result(1, 0, 1));
    }

    @Test
    void runtimeOnlyBatchDoesNotIssueAnEmptyPostgresqlUpdate() {
        AnalyticsRuntimeOutboxReader runtimeOutbox = Mockito.mock(AnalyticsRuntimeOutboxReader.class);
        AnalyticsEventOutboxRepository.Event runtimeEvent = event(-1L);
        AnalyticsRuntimeOutboxReader.RunBatch batch =
                new AnalyticsRuntimeOutboxReader.RunBatch("run-1", "171-0", List.of(runtimeEvent));
        when(outbox.pending(500, NOW)).thenReturn(List.of());
        when(runtimeOutbox.read(200, 500, NOW.minusSeconds(14 * 86_400L))).thenReturn(List.of(batch));
        service = new AnalyticsIngestionService(
                outbox, sink, runtimeOutbox, Clock.fixed(NOW, ZoneOffset.UTC));

        AnalyticsIngestionService.Result result = service.ingestDue();

        verify(sink).append(List.of(runtimeEvent), NOW);
        verify(outbox, never()).markPublished(any(), any());
        verify(outbox, never()).markFailed(any(), any(), any());
        verify(runtimeOutbox).acknowledge(List.of(batch), NOW);
        assertThat(result).isEqualTo(new AnalyticsIngestionService.Result(1, 1, 0));
    }

    private AnalyticsEventOutboxRepository.Event event(long id) {
        return new AnalyticsEventOutboxRepository.Event(
                id, "message:msg-1", 1, "SESSION_MESSAGES", "msg-1", NOW.minusSeconds(5),
                "{\"eventType\":\"MESSAGE\",\"userMessageCount\":1}");
    }
}
