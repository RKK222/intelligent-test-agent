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

class WorkspaceGitAccessInspectionTaskHandlerTest {

    @Test
    void shouldRunEveryTwoHoursAndBroadcastLocalClientInspection() {
        WorkspaceGitAccessInspectionService inspection = mock(WorkspaceGitAccessInspectionService.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        when(inspection.inspectApplicationWorkspaces(context)).thenReturn(
                new WorkspaceGitAccessInspectionService.InspectionResult(4, 2, 1, 1));
        when(publisher.instanceId()).thenReturn("instance-a");
        when(context.traceId()).thenReturn("trace-git-access");
        WorkspaceGitAccessInspectionTaskHandler handler = new WorkspaceGitAccessInspectionTaskHandler(
                inspection,
                publisher,
                new WorkspaceServerIdentity("server-a"),
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC));

        var result = handler.run(context);

        assertThat(handler.taskKey().value()).isEqualTo("workspace-management.git-access-inspection");
        assertThat(handler.cronExpression()).isEqualTo("0 0 0/2 * * ? *");
        assertThat(result.result()).containsEntry("checked", 4).containsEntry("localClientInspectionBroadcast", true);
        ArgumentCaptor<ServerBroadcastEvent> event = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo("workspace.git-access-inspection-requested");
        assertThat(event.getValue().payload()).isEmpty();
    }
}
