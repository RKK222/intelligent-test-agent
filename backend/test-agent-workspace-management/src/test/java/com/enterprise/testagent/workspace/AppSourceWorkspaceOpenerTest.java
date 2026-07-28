package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSourceWorkspaceOpenerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_open");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_open");
    private static final LinuxServerId SERVER_A = new LinuxServerId("server-a");

    @TempDir
    Path root;

    @Test
    void corruptIndexIsRebuiltFromDatabaseSnapshotBeforeOpen() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = new AppSourceIndexManager();
        AppSourceSnapshot snapshot = indexedSnapshot(indexes);
        AppSourceReplica replica = new AppSourceReplica(
                REPOSITORY_ID, 3L, SERVER_A, WORKSPACE_ID, AppSourceReplicaStatus.READY,
                null, null, 1, null, null, null, NOW.minusSeconds(60), NOW);
        Workspace workspace = new Workspace(
                WORKSPACE_ID, "orders", "appsource:orders", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, SERVER_A.value(), "trace-open");
        when(appSources.findReplica(REPOSITORY_ID, 3L, SERVER_A)).thenReturn(Optional.of(replica));
        when(workspaces.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(paths.resolve("appsource:orders")).thenReturn(root);
        Files.writeString(root.resolve(AppSourceApplicationService.INDEX_FILE_NAME), "tampered");
        AppSourceWorkspaceOpener opener = new AppSourceWorkspaceOpener(
                appSources, workspaces, paths, indexes, new WorkspaceServerIdentity(SERVER_A.value()));

        assertThat(opener.open(snapshot, SERVER_A)).isEqualTo(workspace);
        assertThat(sha256(Files.readAllBytes(root.resolve(AppSourceApplicationService.INDEX_FILE_NAME))))
                .isEqualTo(snapshot.indexSha256());
    }

    @Test
    void openerRejectsReplicaOnAnotherServerBeforeResolvingWorkspace() {
        AppSourceWorkspaceOpener opener = new AppSourceWorkspaceOpener(
                mock(AppSourceRepository.class), mock(WorkspaceRepository.class),
                mock(ManagedWorkspacePathResolver.class), new AppSourceIndexManager(),
                new WorkspaceServerIdentity(SERVER_A.value()));

        assertThatThrownBy(() -> opener.open(indexedSnapshot(new AppSourceIndexManager()), new LinuxServerId("server-b")))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void openerRejectsRootSymlinkWithoutRepairingIndexOutsideManagedRoot() throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = new AppSourceIndexManager();
        AppSourceSnapshot snapshot = indexedSnapshot(indexes);
        AppSourceReplica replica = new AppSourceReplica(
                REPOSITORY_ID, 3L, SERVER_A, WORKSPACE_ID, AppSourceReplicaStatus.READY,
                null, null, 1, null, null, null, NOW.minusSeconds(60), NOW);
        Workspace workspace = new Workspace(
                WORKSPACE_ID, "orders", "appsource:orders", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, SERVER_A.value(), "trace-open");
        when(appSources.findReplica(REPOSITORY_ID, 3L, SERVER_A)).thenReturn(Optional.of(replica));
        when(workspaces.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        Path managed = root.resolve("managed");
        Path outside = root.resolve("outside-open");
        Files.createDirectories(managed);
        Files.createDirectories(outside);
        Path linkedTarget = managed.resolve("orders");
        Files.createSymbolicLink(linkedTarget, outside);
        when(paths.resolve("appsource:orders")).thenReturn(linkedTarget);
        AppSourceWorkspaceOpener opener = new AppSourceWorkspaceOpener(
                appSources, workspaces, paths, indexes, new WorkspaceServerIdentity(SERVER_A.value()));

        assertThatThrownBy(() -> opener.open(snapshot, SERVER_A))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(outside.resolve(AppSourceApplicationService.INDEX_FILE_NAME)).doesNotExist();
    }

    private AppSourceSnapshot indexedSnapshot(AppSourceIndexManager indexes) {
        AppSourceSnapshot snapshot = new AppSourceSnapshot(
                REPOSITORY_ID, 3L, "orders", AppSourcePurpose.TEAM, new UserId("usr_downloader"),
                "main", "a".repeat(40), List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, NOW, NOW.plus(Duration.ofHours(2)), AppSourceSnapshotStatus.ACTIVE, NOW, NOW);
        return new AppSourceSnapshot(
                snapshot.repositoryId(), snapshot.generation(), snapshot.repositoryEnglishName(),
                snapshot.purpose(), snapshot.ownerUserId(), snapshot.branch(), snapshot.targetCommit(),
                snapshot.selectedPaths(), indexes.canonicalSha256(snapshot), snapshot.acceptedAt(), snapshot.expiresAt(),
                snapshot.status(), snapshot.createdAt(), snapshot.updatedAt());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
