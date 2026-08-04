package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationCandidate;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalWorkspaceRelocationWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");
    private static final String SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path root;

    @Test
    void discoversTransfersThenCleansOnlyTheLocalSourceWorktree() throws Exception {
        Fixture fixture = fixture(PersonalWorkspaceRelocationStatus.EXPORTING, 1);
        when(fixture.snapshotService.exportSnapshot(
                eq(fixture.paths.personalRepoRoot()), org.mockito.ArgumentMatchers.any(Path.class), eq("pwr_1")))
                .thenAnswer(invocation -> {
                    Path archive = invocation.getArgument(1);
                    Files.writeString(archive, "abc");
                    return new PersonalWorkspaceSnapshotService.Snapshot(archive, SHA256, 3L, "a".repeat(40));
                });
        when(fixture.repository.markTransferring(
                eq("pwr_1"), anyString(), eq(SHA256), eq(3L), eq(NOW))).thenReturn(true);
        when(fixture.repository.completeCleanup(eq("pwr_1"), anyString(), eq(NOW))).thenReturn(true);

        PersonalWorkspaceRelocationWorker.RunResult result = fixture.worker.runDue("trace_relocation");

        assertThat(result).isEqualTo(new PersonalWorkspaceRelocationWorker.RunResult(1, 1, 1, 0));
        verify(fixture.repository).discover(eq(fixture.candidate), anyString(), eq("trace_relocation"), eq(NOW));
        verify(fixture.transferGateway).transfer(
                eq(fixture.claimed), org.mockito.ArgumentMatchers.any(Path.class),
                eq(SHA256), eq(3L), eq("trace_relocation"));
        verify(fixture.snapshotService).removeSourceWorktree(fixture.paths);
    }

    @Test
    void snapshotConflictKeepsDatabaseOwnershipAndSchedulesSafeRetry() {
        Fixture fixture = fixture(PersonalWorkspaceRelocationStatus.EXPORTING, 1);
        when(fixture.snapshotService.exportSnapshot(
                eq(fixture.paths.personalRepoRoot()), org.mockito.ArgumentMatchers.any(Path.class), eq("pwr_1")))
                .thenThrow(new PlatformException(ErrorCode.CONFLICT, "source changed"));

        PersonalWorkspaceRelocationWorker.RunResult result = fixture.worker.runDue("trace_relocation");

        assertThat(result).isEqualTo(new PersonalWorkspaceRelocationWorker.RunResult(1, 1, 0, 1));
        verify(fixture.repository).reschedule(
                eq("pwr_1"), anyString(), eq(1), eq(NOW.plusSeconds(60)),
                eq("RELOCATION_CONFLICT"), eq("个人工作区自动搬迁失败，等待下一轮安全重试"), eq(NOW));
        verify(fixture.transferGateway, never()).transfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(fixture.snapshotService, never()).removeSourceWorktree(fixture.paths);
    }

    @Test
    void cleanupPendingRetryDoesNotExportOrTransferAgain() {
        Fixture fixture = fixture(PersonalWorkspaceRelocationStatus.CLEANUP_PENDING, 2);
        when(fixture.repository.completeCleanup(eq("pwr_1"), anyString(), eq(NOW))).thenReturn(true);

        PersonalWorkspaceRelocationWorker.RunResult result = fixture.worker.runDue("trace_cleanup");

        assertThat(result).isEqualTo(new PersonalWorkspaceRelocationWorker.RunResult(1, 1, 1, 0));
        verify(fixture.snapshotService).removeSourceWorktree(fixture.paths);
        verify(fixture.snapshotService, never()).exportSnapshot(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString());
        verify(fixture.transferGateway, never()).transfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private Fixture fixture(PersonalWorkspaceRelocationStatus claimedStatus, int attemptCount) {
        PersonalWorkspaceRelocationRepository repository = mock(PersonalWorkspaceRelocationRepository.class);
        ManagedWorkspaceApplicationService managedWorkspaceService = mock(ManagedWorkspaceApplicationService.class);
        PersonalWorkspaceSnapshotService snapshotService = mock(PersonalWorkspaceSnapshotService.class);
        PersonalWorkspaceRelocationTransferGateway transferGateway = mock(PersonalWorkspaceRelocationTransferGateway.class);
        PersonalWorkspaceRelocationCandidate candidate = candidate();
        PersonalWorkspaceRelocation due = relocation(PersonalWorkspaceRelocationStatus.DISCOVERED, 0, null, null);
        PersonalWorkspaceRelocation claimed = relocation(
                claimedStatus, attemptCount, "server-a:lease", NOW.plusSeconds(1800));
        PersonalWorkspaceRelocationPaths paths = new PersonalWorkspaceRelocationPaths(
                root.resolve("application"),
                root.resolve("personal"),
                root.resolve("personal/app"),
                "personalworktree:version/user/repository/branch",
                "personalworktree:version/user/repository/branch/app");
        try {
            Files.createDirectories(paths.personalRepoRoot());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(repository.findMismatches("server-a", 200)).thenReturn(List.of(candidate));
        when(repository.findClaimable("server-a", NOW, 2)).thenReturn(List.of(due));
        when(repository.claim(eq("pwr_1"), anyString(), eq(NOW.plusSeconds(1800)), eq(NOW)))
                .thenReturn(Optional.of(claimed));
        when(managedWorkspaceService.sourceRelocationPaths(claimed)).thenReturn(paths);
        PersonalWorkspaceRelocationWorker worker = new PersonalWorkspaceRelocationWorker(
                repository,
                managedWorkspaceService,
                snapshotService,
                transferGateway,
                new WorkspaceServerIdentity("server-a"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(
                repository, snapshotService, transferGateway, worker, candidate, claimed, paths);
    }

    private PersonalWorkspaceRelocationCandidate candidate() {
        return new PersonalWorkspaceRelocationCandidate(
                new PersonalWorkspaceId("psw_1"),
                new ApplicationWorkspaceVersionId("awv_1"),
                new UserId("usr_1"),
                new WorkspaceId("wrk_1"),
                "server-a",
                "server-b");
    }

    private PersonalWorkspaceRelocation relocation(
            PersonalWorkspaceRelocationStatus status,
            int attemptCount,
            String leaseOwner,
            Instant leaseUntil) {
        return new PersonalWorkspaceRelocation(
                "pwr_1",
                new PersonalWorkspaceId("psw_1"),
                new ApplicationWorkspaceVersionId("awv_1"),
                new UserId("usr_1"),
                new WorkspaceId("wrk_1"),
                "server-a",
                "server-b",
                "feature-user",
                "personalworktree:version/user/repository/branch",
                "personalworktree:version/user/repository/branch/app",
                status,
                attemptCount,
                leaseOwner,
                leaseUntil,
                null,
                null,
                "trace_relocation",
                NOW.minusSeconds(60),
                NOW);
    }

    private record Fixture(
            PersonalWorkspaceRelocationRepository repository,
            PersonalWorkspaceSnapshotService snapshotService,
            PersonalWorkspaceRelocationTransferGateway transferGateway,
            PersonalWorkspaceRelocationWorker worker,
            PersonalWorkspaceRelocationCandidate candidate,
            PersonalWorkspaceRelocation claimed,
            PersonalWorkspaceRelocationPaths paths) {
    }
}
