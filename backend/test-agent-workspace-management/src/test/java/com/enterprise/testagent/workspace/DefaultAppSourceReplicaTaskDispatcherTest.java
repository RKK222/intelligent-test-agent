package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultAppSourceReplicaTaskDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");

    @Test
    void wakeRunsOnlyLocalTargetAndBroadcastsFrozenTargetSet() throws Exception {
        AppSourceReplicaWorker worker = mock(AppSourceReplicaWorker.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        CountDownLatch executed = new CountDownLatch(1);
        doAnswer(invocation -> {
            executed.countDown();
            return AppSourceReplicaWorker.Outcome.SUCCEEDED;
        }).when(worker).run(any(), anyLong(), any(), any());
        DefaultAppSourceReplicaTaskDispatcher dispatcher = new DefaultAppSourceReplicaTaskDispatcher(
                worker, publisher, new WorkspaceServerIdentity("server-a"), 1, 4,
                Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.start();
        try {
            AppSourceOperation operation = operation();
            dispatcher.wake(operation, Set.of(new LinuxServerId("server-a"), new LinuxServerId("server-b")));

            assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
            verify(worker).run(
                    operation.repositoryId(), operation.targetGeneration(), new LinuxServerId("server-a"), operation.traceId());
            ArgumentCaptor<ServerBroadcastEvent> event = ArgumentCaptor.forClass(ServerBroadcastEvent.class);
            verify(publisher).publish(event.capture());
            assertThat(event.getValue().payload().get("targetServerIds"))
                    .asList()
                    .containsExactlyInAnyOrder("server-a", "server-b");
        } finally {
            dispatcher.stop();
        }
    }

    @Test
    void duplicateWakeWhileSameServerGenerationRunsIsCoalesced() throws Exception {
        AppSourceReplicaWorker worker = mock(AppSourceReplicaWorker.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                return AppSourceReplicaWorker.Outcome.SUCCEEDED;
            } finally {
                finished.countDown();
            }
        }).when(worker).run(any(), anyLong(), any(), any());
        DefaultAppSourceReplicaTaskDispatcher dispatcher = new DefaultAppSourceReplicaTaskDispatcher(
                worker, publisher, new WorkspaceServerIdentity("server-a"), 1, 1,
                Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.start();
        try {
            dispatcher.wake(operation(), Set.of(new LinuxServerId("server-a")));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            dispatcher.wake(operation(), Set.of(new LinuxServerId("server-a")));
            verify(worker, timeout(500).times(1)).run(any(), anyLong(), any(), any());
        } finally {
            release.countDown();
            finished.await(2, TimeUnit.SECONDS);
            dispatcher.stop();
        }
    }

    @Test
    void capacityLimitIncludesRunningKeyAndRejectsOnlyBestEffortWake() throws Exception {
        AppSourceReplicaWorker worker = mock(AppSourceReplicaWorker.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (((CodeRepositoryId) invocation.getArgument(0)).value().equals("repo_first")) {
                firstStarted.countDown();
                release.await(2, TimeUnit.SECONDS);
            }
            return AppSourceReplicaWorker.Outcome.SUCCEEDED;
        }).when(worker).run(any(), anyLong(), any(), any());
        DefaultAppSourceReplicaTaskDispatcher dispatcher = new DefaultAppSourceReplicaTaskDispatcher(
                worker, publisher, new WorkspaceServerIdentity("server-a"), 1, 1,
                Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.start();
        try {
            dispatcher.wake(operation("op-first", "repo_first"), Set.of(new LinuxServerId("server-a")));
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            dispatcher.wake(operation("op-second", "repo_second"), Set.of(new LinuxServerId("server-a")));
            release.countDown();

            verify(worker, after(500).never()).run(
                    new CodeRepositoryId("repo_second"), 1L, new LinuxServerId("server-a"), "trace-1");
        } finally {
            release.countDown();
            dispatcher.stop();
        }
    }

    private AppSourceOperation operation() {
        return operation("op-dispatch", "repo_dispatch");
    }

    private AppSourceOperation operation(String operationId, String repositoryId) {
        return new AppSourceOperation(
                operationId, new ApplicationId("app-1"), new CodeRepositoryId(repositoryId),
                null, 1L, new UserId("user-1"), AppSourceOperationType.DOWNLOAD, "hash",
                AppSourceOperationStatus.PENDING, "trace-1", NOW, null);
    }
}
