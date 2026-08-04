package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证每日任务的 XXL 元数据、广播和本机执行。 */
class InactiveOpencodeProcessCleanupTaskHandlerTest {

    @Test
    void runsDailyAtTwoAndBroadcastsBeforeLocalCleanup() {
        InactiveOpencodeProcessCleanupService service = mock(InactiveOpencodeProcessCleanupService.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        when(identity.linuxServerId()).thenReturn("server-a");
        when(context.traceId()).thenReturn("trace_inactive_handler");
        when(service.cleanupCurrentServer(eq("trace_inactive_handler"), any()))
                .thenReturn(new InactiveOpencodeProcessCleanupService.Result(
                        Instant.parse("2026-07-20T02:00:00Z"), 2, 1, 0, 1, 0, 0, false));
        InactiveOpencodeProcessCleanupTaskHandler handler = new InactiveOpencodeProcessCleanupTaskHandler(
                service,
                publisher,
                identity,
                Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneOffset.UTC));

        var result = handler.run(context);

        assertThat(handler.taskKey().value())
                .isEqualTo("opencode-runtime.inactive-user-process-cleanup");
        assertThat(handler.cronExpression()).isEqualTo("0 0 2 * * ? *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(result.result()).containsEntry("stoppedCount", 1);
        ArgumentCaptor<ServerBroadcastEvent> event = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type())
                .isEqualTo("opencode-runtime.inactive-user-process-cleanup-requested");
        assertThat(event.getValue().payload()).isEqualTo(Map.of());
    }

    @Test
    void broadcastWakeRunsCleanupOnReceivingInstance() {
        InactiveOpencodeProcessCleanupService service = mock(InactiveOpencodeProcessCleanupService.class);
        InactiveOpencodeProcessCleanupTaskHandler handler = new InactiveOpencodeProcessCleanupTaskHandler(
                service,
                mock(ServerBroadcastPublisher.class),
                mock(BackendInstanceIdentity.class),
                Clock.systemUTC());
        ServerBroadcastEvent event = new ServerBroadcastEvent(
                "sbe_inactive_cleanup_123",
                "opencode-runtime.inactive-user-process-cleanup-requested",
                "instance-a",
                "server-a",
                "trace_broadcast_inactive",
                Instant.parse("2026-08-04T02:00:00Z"),
                Map.of());

        handler.handle(event);

        verify(service).cleanupCurrentServer(eq("trace_broadcast_inactive"), any());
    }
}
