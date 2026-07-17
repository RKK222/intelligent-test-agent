package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunRetentionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduledTaskRunRetentionTaskHandlerTest {

    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("scheduler.run-retention-cleanup");
    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void deletesRunsEndedBeforeSevenDayCutoff() {
        ScheduledTaskRunRetentionRepository repository = mock(ScheduledTaskRunRetentionRepository.class);
        when(repository.deleteEndedBefore(Instant.parse("2026-07-08T00:00:00Z"))).thenReturn(3);
        ScheduledTaskRunRetentionTaskHandler handler = new ScheduledTaskRunRetentionTaskHandler(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        ScheduledTaskResult result = handler.run(testContext());

        verify(repository).deleteEndedBefore(Instant.parse("2026-07-08T00:00:00Z"));
        assertThat(result.result())
                .containsEntry("deletedCount", 3)
                .containsEntry("retentionDays", 7)
                .containsEntry("cutoff", "2026-07-08T00:00:00Z");
        assertThat(handler.taskKey()).isEqualTo(TASK_KEY);
        assertThat(handler.cronExpression()).isEqualTo("0 0 0 * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    private ScheduledTaskContext testContext() {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_retention_1234567890abcdef"),
                TASK_KEY,
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                NOW,
                "trace_scheduler_retention_test",
                Map.of());
    }
}
