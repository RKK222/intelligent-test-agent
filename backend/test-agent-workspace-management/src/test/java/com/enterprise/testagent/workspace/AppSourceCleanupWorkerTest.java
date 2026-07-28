package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceCleanupStatus;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSourceCleanupWorkerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_cleanup");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    @TempDir
    Path root;

    @Test
    void newerGlobalGenerationDoesNotFenceDeletionWhenItHasNoReplicaOnThisServer() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = mock(AppSourceIndexManager.class);
        AppSourceCleanupResultRecorder results = mock(AppSourceCleanupResultRecorder.class);
        AppSourceCleanupTask due = cleanupTask(null, null, AppSourceCleanupStatus.PENDING);
        Clock clock = mock(Clock.class);
        Instant completedAt = NOW.plusSeconds(5);
        when(clock.instant()).thenReturn(NOW, NOW, completedAt);
        when(appSources.findDueCleanupTasks(SERVER_ID, NOW, 32)).thenReturn(List.of(due));
        when(appSources.claimCleanupTask(eq(due.cleanupTaskId()), any(), eq(NOW.plusSeconds(300)), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(cleanupTask(
                        invocation.getArgument(1), NOW.plusSeconds(300), AppSourceCleanupStatus.RUNNING)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot()));
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        when(paths.resolve("appsource:orders")).thenReturn(root);
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 2L, null, 3L, "op-new", 2L, NOW.minusSeconds(600), NOW)));
        when(appSources.findReplica(REPOSITORY_ID, 2L, SERVER_ID)).thenReturn(Optional.empty());
        Files.writeString(root.resolve("old-source.txt"), "old generation");
        AppSourceCleanupWorker worker = new AppSourceCleanupWorker(
                appSources, paths, indexes, results, new WorkspaceServerIdentity(SERVER_ID.value()),
                clock, 300L, 32);

        assertThat(worker.runDue()).isEqualTo(1);

        assertThat(root.resolve("old-source.txt")).doesNotExist();
        verify(indexes).ensureAuthoritativeIndex(root, snapshot());
        verify(results).complete(any(AppSourceCleanupTask.class), any(), eq(completedAt));
    }

    @Test
    void dueCurrentGenerationDeletesSourceStagingAndBackupButRepairsIndex() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = new AppSourceIndexManager();
        AppSourceCleanupResultRecorder results = mock(AppSourceCleanupResultRecorder.class);
        AppSourceSnapshot indexedSnapshot = indexedSnapshot(indexes);
        AppSourceCleanupTask due = cleanupTask(null, null, AppSourceCleanupStatus.PENDING);
        when(appSources.findDueCleanupTasks(SERVER_ID, NOW, 32)).thenReturn(List.of(due));
        when(appSources.claimCleanupTask(eq(due.cleanupTaskId()), any(), any(), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(cleanupTask(
                        invocation.getArgument(1), NOW.plusSeconds(300), AppSourceCleanupStatus.RUNNING)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(indexedSnapshot));
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        when(paths.resolve("appsource:orders")).thenReturn(root);
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 1L, null, 2L, "op-old", 1L, NOW.minusSeconds(600), NOW)));
        Files.writeString(root.resolve("source.txt"), "delete me");
        Files.writeString(root.resolve(AppSourceApplicationService.INDEX_FILE_NAME), "corrupt");
        String targetName = root.getFileName().toString();
        Path staging = root.getParent().resolve("." + targetName + ".g1.fixture.staging");
        Path backup = root.getParent().resolve(
                "." + targetName + ".00000000-0000-0000-0000-000000000003.backup");
        Files.createDirectories(staging);
        Files.createDirectories(backup);
        Files.writeString(staging.resolve("partial.txt"), "partial");
        Files.writeString(backup.resolve("old.txt"), "old");
        AppSourceCleanupWorker worker = new AppSourceCleanupWorker(
                appSources, paths, indexes, results, new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC), 300L, 32);

        assertThat(worker.runDue()).isEqualTo(1);

        assertThat(root.resolve("source.txt")).doesNotExist();
        assertThat(staging).doesNotExist();
        assertThat(backup).doesNotExist();
        assertThat(sha256(Files.readAllBytes(root.resolve(AppSourceApplicationService.INDEX_FILE_NAME))))
                .isEqualTo(indexedSnapshot.indexSha256());
    }

    @Test
    void oldCleanupRemovesRepeatedCompletedBackupsWithoutTouchingNewReadyGenerationOrSimilarNames()
            throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = mock(AppSourceIndexManager.class);
        AppSourceCleanupResultRecorder results = mock(AppSourceCleanupResultRecorder.class);
        AppSourceCleanupTask due = cleanupTask(null, null, AppSourceCleanupStatus.PENDING);
        when(appSources.findDueCleanupTasks(SERVER_ID, NOW, 32)).thenReturn(List.of(due));
        when(appSources.claimCleanupTask(eq(due.cleanupTaskId()), any(), any(), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(cleanupTask(
                        invocation.getArgument(1), NOW.plusSeconds(300), AppSourceCleanupStatus.RUNNING)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot()));
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        when(paths.resolve("appsource:orders")).thenReturn(root);
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 3L, null, 4L, "op-new", 3L, NOW.minusSeconds(600), NOW)));
        when(appSources.findReplica(REPOSITORY_ID, 3L, SERVER_ID)).thenReturn(Optional.of(new AppSourceReplica(
                REPOSITORY_ID, 3L, SERVER_ID, new WorkspaceId("wrk_new"),
                AppSourceReplicaStatus.READY, null, null, 1, null, null, null,
                NOW.minusSeconds(60), NOW)));
        Files.writeString(root.resolve("current-generation.txt"), "generation three");
        String targetName = root.getFileName().toString();
        Path firstRetainedBackup = root.getParent().resolve(
                "." + targetName + ".00000000-0000-0000-0000-000000000001.backup");
        Path secondRetainedBackup = root.getParent().resolve(
                "." + targetName + ".00000000-0000-0000-0000-000000000002.backup");
        Path currentStaging = root.getParent().resolve("." + targetName + ".g3.current.staging");
        Path otherRepositoryBackup = root.getParent().resolve("." + targetName + "-other.first.backup");
        Path similarSuffix = root.getParent().resolve("." + targetName + ".first.backup.tmp");
        Path nonUuidBackup = root.getParent().resolve("." + targetName + ".not-a-uuid.backup");
        for (Path directory : List.of(
                firstRetainedBackup, secondRetainedBackup, currentStaging,
                otherRepositoryBackup, similarSuffix, nonUuidBackup)) {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("marker.txt"), directory.getFileName().toString());
        }
        AppSourceCleanupWorker worker = new AppSourceCleanupWorker(
                appSources, paths, indexes, results, new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC), 300L, 32);

        assertThat(worker.runDue()).isEqualTo(1);

        assertThat(firstRetainedBackup).doesNotExist();
        assertThat(secondRetainedBackup).doesNotExist();
        assertThat(root.resolve("current-generation.txt")).hasContent("generation three");
        assertThat(currentStaging).isDirectory();
        assertThat(otherRepositoryBackup).isDirectory();
        assertThat(similarSuffix).isDirectory();
        assertThat(nonUuidBackup).isDirectory();
        verify(results).complete(any(AppSourceCleanupTask.class), any(), eq(NOW));
    }

    @Test
    void cleanupDefersWhileReplicaStillOwnsAnUnexpiredMaterializationLease() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = mock(AppSourceIndexManager.class);
        AppSourceCleanupResultRecorder results = mock(AppSourceCleanupResultRecorder.class);
        AppSourceCleanupTask due = cleanupTask(null, null, AppSourceCleanupStatus.PENDING);
        when(appSources.findDueCleanupTasks(SERVER_ID, NOW, 32)).thenReturn(List.of(due));
        when(appSources.claimCleanupTask(eq(due.cleanupTaskId()), any(), any(), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(cleanupTask(
                        invocation.getArgument(1), NOW.plusSeconds(300), AppSourceCleanupStatus.RUNNING)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot()));
        when(appSources.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).thenReturn(Optional.of(
                new com.enterprise.testagent.domain.appsource.AppSourceReplica(
                        REPOSITORY_ID, 1L, SERVER_ID, null,
                        com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus.RUNNING,
                        "materializer", NOW.plusSeconds(120), 1, null, null, null,
                        NOW.minusSeconds(60), NOW)));
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        when(paths.resolve("appsource:orders")).thenReturn(root);
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 1L, null, 2L, "op-old", 1L, NOW.minusSeconds(600), NOW)));
        Files.writeString(root.resolve("source.txt"), "still materializing");
        AppSourceCleanupWorker worker = new AppSourceCleanupWorker(
                appSources, paths, indexes, results, new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC), 300L, 32);

        assertThat(worker.runDue()).isZero();

        assertThat(root.resolve("source.txt")).exists();
        verify(results, never()).complete(any(), any(), any());
        verify(appSources).rescheduleCleanupTask(
                eq(due.cleanupTaskId()), any(), eq(1), eq(NOW.plusSeconds(5)),
                eq("CONFLICT"), eq("应用源码清理失败"), eq(NOW));
    }

    @Test
    void cleanupRejectsRootSymlinkWithoutDeletingOutsideManagedRoot() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = mock(AppSourceIndexManager.class);
        AppSourceCleanupResultRecorder results = mock(AppSourceCleanupResultRecorder.class);
        AppSourceCleanupTask due = cleanupTask(null, null, AppSourceCleanupStatus.PENDING);
        when(appSources.findDueCleanupTasks(SERVER_ID, NOW, 32)).thenReturn(List.of(due));
        when(appSources.claimCleanupTask(eq(due.cleanupTaskId()), any(), any(), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(cleanupTask(
                        invocation.getArgument(1), NOW.plusSeconds(300), AppSourceCleanupStatus.RUNNING)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot()));
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        Path managed = root.resolve("managed");
        Path outside = root.resolve("outside-cleanup");
        Files.createDirectories(managed);
        Files.createDirectories(outside);
        Path outsideMarker = outside.resolve("must-survive.txt");
        Files.writeString(outsideMarker, "outside");
        Path linkedTarget = managed.resolve("orders");
        Files.createSymbolicLink(linkedTarget, outside);
        when(paths.resolve("appsource:orders")).thenReturn(linkedTarget);
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 1L, null, 2L, "op-old", 1L, NOW.minusSeconds(600), NOW)));
        AppSourceCleanupWorker worker = new AppSourceCleanupWorker(
                appSources, paths, indexes, results, new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC), 300L, 32);

        assertThat(worker.runDue()).isZero();
        assertThat(outsideMarker).hasContent("outside");
        verify(results, never()).complete(any(), any(), any());
    }

    private AppSourceSnapshot snapshot() {
        return new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "orders", AppSourcePurpose.TEAM, new UserId("usr_downloader"),
                "main", "a".repeat(40), List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                "b".repeat(64), NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)),
                AppSourceSnapshotStatus.EXPIRED, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));
    }

    private AppSourceSnapshot indexedSnapshot(AppSourceIndexManager indexes) {
        AppSourceSnapshot withoutIndex = snapshot();
        return new AppSourceSnapshot(
                withoutIndex.repositoryId(), withoutIndex.generation(), withoutIndex.repositoryEnglishName(),
                withoutIndex.purpose(), withoutIndex.ownerUserId(), withoutIndex.branch(),
                withoutIndex.targetCommit(), withoutIndex.selectedPaths(), indexes.canonicalSha256(withoutIndex),
                withoutIndex.acceptedAt(), withoutIndex.expiresAt(), withoutIndex.status(),
                withoutIndex.createdAt(), withoutIndex.updatedAt());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private AppSourceCleanupTask cleanupTask(
            String leaseOwner, Instant leaseUntil, AppSourceCleanupStatus status) {
        return new AppSourceCleanupTask(
                "asc_cleanup", "op-old", REPOSITORY_ID, 1L, SERVER_ID, NOW.minusSeconds(1), status,
                leaseOwner, leaseUntil, status == AppSourceCleanupStatus.RUNNING ? 1 : 0,
                NOW.minusSeconds(1), null, null, "trace-cleanup", NOW.minus(Duration.ofHours(2)), NOW);
    }
}
