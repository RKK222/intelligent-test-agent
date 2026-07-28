package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;

class AppSourceReplicaWorkerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_worker");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    @TempDir
    Path appSourceRoot;

    @Test
    void claimedReplicaMaterializesFrozenSnapshotAndRegistersGenerationWorkspace() {
        Fixture fixture = fixture();
        AppSourceReplica claimed = fixture.claimed();
        when(fixture.appSources.claimReplica(
                        eq(REPOSITORY_ID), eq(2L), eq(SERVER_ID), any(), eq(NOW.plus(Duration.ofMinutes(10))), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(fixture.snapshot()));
        when(fixture.appSources.findInFlightOperationForReplica(REPOSITORY_ID, 2L, SERVER_ID))
                .thenReturn(Optional.of(fixture.operation()));
        when(fixture.configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(fixture.repository()));
        when(fixture.gitAccess.resolve(fixture.repository(), fixture.operation().actorUserId()))
                .thenReturn(new AppSourceGitAccessResolver.GitAccess("ssh://git/orders.git", "secret-key"));
        when(fixture.materializer.materialize(any(), any(), any())).thenAnswer(invocation -> {
            AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer.Result("b".repeat(64), true);
            AppSourceGitMaterializer.Progress progress = invocation.getArgument(2);
            progress.started(AppSourceReplicaStepCatalog.STAGING);
            progress.succeeded(AppSourceReplicaStepCatalog.STAGING);
            AppSourceGitMaterializer.Completion completion = invocation.getArgument(1);
            completion.complete(result);
            return result;
        });

        AppSourceReplicaWorker.Outcome outcome = fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1");

        assertThat(outcome).isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);
        ArgumentCaptor<AppSourceGitMaterializer.Request> request =
                ArgumentCaptor.forClass(AppSourceGitMaterializer.Request.class);
        verify(fixture.materializer).materialize(request.capture(), any(), any());
        assertThat(request.getValue().targetRoot()).isEqualTo(appSourceRoot.resolve("orders"));
        assertThat(request.getValue().targetCommit()).isEqualTo("deadbeef");
        assertThat(request.getValue().selectedPaths()).isEqualTo(fixture.snapshot().selectedPaths());
        ArgumentCaptor<Workspace> workspace = ArgumentCaptor.forClass(Workspace.class);
        verify(fixture.results).recordSuccess(
                eq(fixture.operation()), eq(claimed), any(), workspace.capture(), eq("b".repeat(64)), eq(NOW));
        assertThat(workspace.getValue().rootPath()).isEqualTo("appsource:orders");
        assertThat(workspace.getValue().linuxServerId()).isEqualTo(SERVER_ID.value());
        InOrder progressOrder = inOrder(fixture.progress);
        progressOrder.verify(fixture.progress).start(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.QUEUED), eq(NOW));
        progressOrder.verify(fixture.progress).succeed(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.QUEUED), eq(NOW));
        progressOrder.verify(fixture.progress).start(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.LEASE_CLAIM), eq(NOW));
        progressOrder.verify(fixture.progress).start(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.LOCAL_LOCK), eq(NOW));
        progressOrder.verify(fixture.progress).start(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.STAGING), eq(NOW));
        progressOrder.verify(fixture.progress).succeed(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.STAGING), eq(NOW));
        progressOrder.verify(fixture.progress).start(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.REGISTER_WORKSPACE), eq(NOW));
    }

    @Test
    void workerPersistsOnlySafeFailureCodeAndMessage() {
        Fixture fixture = fixture();
        AppSourceReplica claimed = fixture.claimed();
        when(fixture.appSources.claimReplica(any(), eq(2L), eq(SERVER_ID), any(), any(), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(fixture.snapshot()));
        when(fixture.appSources.findInFlightOperationForReplica(REPOSITORY_ID, 2L, SERVER_ID))
                .thenReturn(Optional.of(fixture.operation()));
        when(fixture.configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(fixture.repository()));
        when(fixture.gitAccess.resolve(any(), any()))
                .thenReturn(new AppSourceGitAccessResolver.GitAccess("ssh://git/orders.git", "secret-key"));
        when(fixture.materializer.materialize(any(), any(), any())).thenThrow(new PlatformException(
                ErrorCode.GIT_UNAVAILABLE,
                "raw git stderr contains secret-key /private/path",
                Map.of("stderr", "secret-key")));

        AppSourceReplicaWorker.Outcome outcome = fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1");

        assertThat(outcome).isEqualTo(AppSourceReplicaWorker.Outcome.FAILED);
        verify(fixture.results).recordFailure(
                eq(fixture.operation()), eq(claimed), any(), eq("GIT_UNAVAILABLE"),
                eq("源码副本物化失败"), eq(NOW));
        verify(fixture.progress).failCurrentAndSkipFollowing(
                eq(fixture.operation()), eq(claimed), any(), eq(AppSourceReplicaStepCatalog.LOCAL_LOCK), eq(NOW));
    }

    @Test
    void workerCompletesOperationBoundToItsServerInsteadOfRepositoryWideLatestOperation() {
        Fixture fixture = fixture();
        AppSourceReplica claimed = fixture.claimed();
        AppSourceOperation serverOperation = fixture.operation();
        AppSourceOperation laterOtherServerOperation = new AppSourceOperation(
                "op-other-server", new ApplicationId("app-1"), REPOSITORY_ID, 2L, 2L,
                new UserId("user-2"), AppSourceOperationType.RETRY_REPLICAS, "other-hash",
                AppSourceOperationStatus.PENDING, "trace-other", NOW.minusSeconds(30), null);
        when(fixture.appSources.claimReplica(any(), eq(2L), eq(SERVER_ID), any(), any(), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(fixture.snapshot()));
        when(fixture.appSources.findInFlightOperationForReplica(REPOSITORY_ID, 2L, SERVER_ID))
                .thenReturn(Optional.of(serverOperation));
        when(fixture.configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(fixture.repository()));
        when(fixture.gitAccess.resolve(fixture.repository(), serverOperation.actorUserId()))
                .thenReturn(new AppSourceGitAccessResolver.GitAccess("ssh://git/orders.git", "secret-key"));
        when(fixture.materializer.materialize(any(), any(), any())).thenAnswer(invocation -> {
            AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer.Result("b".repeat(64), true);
            ((AppSourceGitMaterializer.Completion) invocation.getArgument(1)).complete(result);
            return result;
        });

        assertThat(fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1"))
                .isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);

        verify(fixture.results).recordSuccess(
                eq(serverOperation), eq(claimed), any(), any(), eq("b".repeat(64)), eq(NOW));
    }

    @Test
    void successUsesFreshCompletionTimeForAbsoluteLeaseFencing() {
        Clock clock = mock(Clock.class);
        Instant completedAt = NOW.plusSeconds(5);
        when(clock.instant()).thenReturn(NOW, completedAt);
        Fixture fixture = fixture(clock);
        AppSourceReplica claimed = fixture.claimed();
        when(fixture.appSources.claimReplica(any(), eq(2L), eq(SERVER_ID), any(), any(), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(fixture.snapshot()));
        when(fixture.appSources.findInFlightOperationForReplica(REPOSITORY_ID, 2L, SERVER_ID))
                .thenReturn(Optional.of(fixture.operation()));
        when(fixture.configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(fixture.repository()));
        when(fixture.gitAccess.resolve(any(), any()))
                .thenReturn(new AppSourceGitAccessResolver.GitAccess("ssh://git/orders.git", "secret-key"));
        when(fixture.materializer.materialize(any(), any(), any())).thenAnswer(invocation -> {
            AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer.Result("b".repeat(64), true);
            ((AppSourceGitMaterializer.Completion) invocation.getArgument(1)).complete(result);
            return result;
        });

        assertThat(fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1"))
                .isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);

        verify(fixture.results).recordSuccess(
                eq(fixture.operation()), eq(claimed), any(), any(), eq("b".repeat(64)), eq(completedAt));
    }

    @Test
    void expiredGenerationIsQuietlySkippedBeforeLeaseAndGitAccess() {
        Fixture fixture = fixture();
        AppSourceSnapshot expired = new AppSourceSnapshot(
                REPOSITORY_ID, 2L, "orders", AppSourcePurpose.TEAM, new UserId("user-1"),
                "main", "deadbeef", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)),
                AppSourceSnapshotStatus.ACTIVE, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(expired));

        AppSourceReplicaWorker.Outcome outcome = fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1");

        assertThat(outcome).isEqualTo(AppSourceReplicaWorker.Outcome.SKIPPED_STALE);
        verify(fixture.appSources, never()).claimReplica(any(), anyLong(), any(), any(), any(), any());
        verify(fixture.gitAccess, never()).resolve(any(), any());
        verify(fixture.materializer, never()).materialize(any(), any(), any());
        verify(fixture.results, never()).recordSuccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void leaseLostDuringAttemptResetStopsWithoutAnyLaterProgressOrReplicaResult() {
        Fixture fixture = fixture();
        AppSourceReplica claimed = fixture.claimed();
        when(fixture.appSources.claimReplica(any(), eq(2L), eq(SERVER_ID), any(), any(), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(fixture.appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(fixture.snapshot()));
        when(fixture.appSources.findInFlightOperationForReplica(REPOSITORY_ID, 2L, SERVER_ID))
                .thenReturn(Optional.of(fixture.operation()));
        when(fixture.configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(fixture.repository()));
        doThrow(new PlatformException(
                        ErrorCode.CONFLICT, "应用源码步骤租约已失效",
                        Map.of("failure", "REPLICA_LEASE_LOST")))
                .when(fixture.progress).resetForAttempt(
                        eq(fixture.operation()), eq(claimed), any(), eq(NOW));

        assertThat(fixture.worker.run(REPOSITORY_ID, 2L, SERVER_ID, "trace-1"))
                .isEqualTo(AppSourceReplicaWorker.Outcome.NOT_CLAIMED);

        verify(fixture.progress, never()).start(any(), any(), any(), any(), any());
        verify(fixture.progress, never()).succeed(any(), any(), any(), any(), any());
        verify(fixture.progress, never()).failCurrentAndSkipFollowing(any(), any(), any(), any(), any());
        verify(fixture.materializer, never()).materialize(any(), any(), any());
        verify(fixture.results, never()).recordSuccess(any(), any(), any(), any(), any(), any());
        verify(fixture.results, never()).recordFailure(any(), any(), any(), any(), any(), any());
    }

    private Fixture fixture() {
        return fixture(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Fixture fixture(Clock clock) {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AppSourceGitAccessResolver gitAccess = mock(AppSourceGitAccessResolver.class);
        AppSourceGitMaterializer materializer = mock(AppSourceGitMaterializer.class);
        AppSourceReplicaResultRecorder results = mock(AppSourceReplicaResultRecorder.class);
        AppSourceReplicaProgressRecorder progress = mock(AppSourceReplicaProgressRecorder.class);
        ManagedWorkspacePathResolver paths = new ManagedWorkspacePathResolver(appSourceParameters());
        AppSourceReplicaWorker worker = new AppSourceReplicaWorker(
                appSources, configuration, gitAccess, materializer, results, progress, paths,
                new WorkspaceServerIdentity(SERVER_ID.value()),
                clock, Duration.ofMinutes(10));
        return new Fixture(appSources, configuration, gitAccess, materializer, results, progress, worker);
    }

    private CommonParameterValues appSourceParameters() {
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_SOURCE_ROOT.equals(englishName)
                        ? Optional.of(appSourceRoot.toString()) : Optional.empty();
            }

            @Override
            public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
                return resolvedValue(englishName);
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }

    private record Fixture(
            AppSourceRepository appSources,
            ConfigurationManagementRepository configuration,
            AppSourceGitAccessResolver gitAccess,
            AppSourceGitMaterializer materializer,
            AppSourceReplicaResultRecorder results,
            AppSourceReplicaProgressRecorder progress,
            AppSourceReplicaWorker worker) {

        private AppSourceReplica claimed() {
            return new AppSourceReplica(
                    REPOSITORY_ID, 2L, SERVER_ID, null, AppSourceReplicaStatus.RUNNING,
                    "lease", NOW.plusSeconds(600), 1, null, null, null, NOW.minusSeconds(60), NOW);
        }

        private AppSourceSnapshot snapshot() {
            return new AppSourceSnapshot(
                    REPOSITORY_ID, 2L, "orders", AppSourcePurpose.TEAM, new UserId("user-1"),
                    "main", "deadbeef", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                    null, NOW.minusSeconds(60), NOW.minusSeconds(60).plus(Duration.ofHours(2)),
                    AppSourceSnapshotStatus.PENDING, NOW.minusSeconds(60), NOW.minusSeconds(60));
        }

        private AppSourceOperation operation() {
            return new AppSourceOperation(
                    "op-worker", new ApplicationId("app-1"), REPOSITORY_ID, 1L, 2L,
                    new UserId("user-1"), AppSourceOperationType.UPDATE, "hash",
                    AppSourceOperationStatus.PENDING, "trace-1", NOW.minusSeconds(60), null);
        }

        private CodeRepository repository() {
            return new CodeRepository(
                    REPOSITORY_ID, "ssh://git/orders.git", "订单", "orders",
                    CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), CodeRepositoryDeploymentMode.EXTERNAL.value(),
                    false, NOW.minusSeconds(600), NOW.minusSeconds(600));
        }
    }
}
