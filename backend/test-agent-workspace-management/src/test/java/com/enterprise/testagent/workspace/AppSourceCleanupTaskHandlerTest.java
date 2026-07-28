package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppSourceCleanupTaskHandlerTest {

    @Test
    void scheduledRunBroadcastsSafeWakeAndProcessesOnlyLocalDueTasks() {
        AppSourceCleanupWorker worker = mock(AppSourceCleanupWorker.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        when(context.traceId()).thenReturn("trace-cleanup");
        when(worker.runDue()).thenReturn(3);
        AppSourceCleanupTaskHandler handler = new AppSourceCleanupTaskHandler(
                worker, publisher, new WorkspaceServerIdentity("server-a"),
                Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC));

        assertThat(handler.run(context).result()).containsEntry("localCompleted", 3);

        ArgumentCaptor<ServerBroadcastEvent> event = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo("app-source.cleanup-requested");
        assertThat(event.getValue().payload()).isEmpty();
        verify(worker).runDue();
        assertThat(handler.taskKey().value()).isEqualTo("workspace-management.app-source-cleanup");
        assertThat(handler.cronExpression()).isEqualTo("0 0/1 * * * ? *");
    }
}
