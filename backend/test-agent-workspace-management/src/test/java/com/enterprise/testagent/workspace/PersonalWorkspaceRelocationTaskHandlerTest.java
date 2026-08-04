package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PersonalWorkspaceRelocationTaskHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");

    @Test
    void scheduledRunBroadcastsAllServersAndProcessesTheLocalSource() {
        PersonalWorkspaceRelocationWorker worker = mock(PersonalWorkspaceRelocationWorker.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        when(publisher.instanceId()).thenReturn("java-instance-a");
        when(context.traceId()).thenReturn("trace_relocation");
        when(worker.runDue("trace_relocation"))
                .thenReturn(new PersonalWorkspaceRelocationWorker.RunResult(2, 1, 1, 0));
        PersonalWorkspaceRelocationTaskHandler handler = new PersonalWorkspaceRelocationTaskHandler(
                worker,
                publisher,
                new WorkspaceServerIdentity("server-a"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(handler.run(context).result())
                .containsEntry("discovered", 2)
                .containsEntry("due", 1)
                .containsEntry("succeeded", 1)
                .containsEntry("retried", 0);

        ArgumentCaptor<ServerBroadcastEvent> event = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(PersonalWorkspaceRelocationTaskHandler.WAKE_EVENT);
        assertThat(event.getValue().originLinuxServerId()).isEqualTo("server-a");
        assertThat(event.getValue().payload()).isEmpty();
        assertThat(handler.taskKey().value())
                .isEqualTo("workspace-management.personal-workspace-relocation");
        assertThat(handler.cronExpression()).isEqualTo("0 0/1 * * * ? *");

        handler.handle(new ServerBroadcastEvent(
                "sbe_relocation",
                PersonalWorkspaceRelocationTaskHandler.WAKE_EVENT,
                "java-instance-b",
                "server-b",
                "trace_relocation",
                NOW,
                Map.of()));
        verify(worker, times(2)).runDue("trace_relocation");
    }
}
