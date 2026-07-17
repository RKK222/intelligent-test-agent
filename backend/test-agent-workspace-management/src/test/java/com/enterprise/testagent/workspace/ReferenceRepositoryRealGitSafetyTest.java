package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitCommandResult;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.ProcessGitCommandExecutor;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 使用真实 Git 仓库固定已有引用副本只能安全快进，任何未知本地状态都不得被覆盖。 */
class ReferenceRepositoryRealGitSafetyTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_real_assets");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");
    private static final UserId ADMIN = new UserId("usr_admin");

    @TempDir
    Path tempDir;

    private Path referencesRoot;
    private Path remote;
    private Path writer;
    private Path local;
    private String targetCommit;
    private GitWorkspaceService gitWorkspaceService;
    private ReferenceRepositoryRepository referenceRepository;
    private ReferenceRepositoryApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        referencesRoot = tempDir.resolve("references");
        remote = tempDir.resolve("reference-assets.git");
        writer = tempDir.resolve("writer");
        local = referencesRoot.resolve("assets");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");
        git(tempDir, "clone", remote.toString(), writer.toString());
        git(writer, "config", "user.name", "Reference Admin");
        git(writer, "config", "user.email", "reference-admin@example.invalid");
        git(writer, "checkout", "-b", "main");
        Files.writeString(writer.resolve("README.md"), "version 1\n");
        git(writer, "add", "--all");
        git(writer, "commit", "-m", "initial reference assets");
        git(writer, "push", "-u", "origin", "main");
        gitWorkspaceService = new GitWorkspaceService();
        gitWorkspaceService.cloneBranch(remote.toString(), "main", local, null);
        Files.writeString(writer.resolve("README.md"), "version 2\n");
        git(writer, "commit", "-am", "advance reference assets");
        git(writer, "push", "origin", "main");
        targetCommit = git(writer, "rev-parse", "HEAD").stdoutText().trim();

        ConfigurationManagementRepository configurationRepository = mock(ConfigurationManagementRepository.class);
        referenceRepository = mock(ReferenceRepositoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OpencodeProcessHeartbeatStore heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
        CommonParameterValues parameterValues = mock(CommonParameterValues.class);
        SshKeyEncryptionService encryptionService = mock(SshKeyEncryptionService.class);
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        CodeRepository repository = new CodeRepository(
                REPOSITORY_ID,
                remote.toString(),
                "真实资产库",
                "assets",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                "EXTERNAL",
                false,
                NOW,
                NOW);
        ReferenceRepositoryState state = new ReferenceRepositoryState(
                REPOSITORY_ID,
                "main",
                targetCommit,
                1L,
                ReferenceRepositoryStatus.SYNCHRONIZING,
                ADMIN,
                "trace_real_git",
                null,
                NOW,
                NOW,
                NOW);
        when(referenceRepository.claimReplica(
                        eq(REPOSITORY_ID),
                        eq(1L),
                        eq(SERVER_ID),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(processingReplica(invocation.getArgument(3, String.class))));
        when(referenceRepository.findState(REPOSITORY_ID)).thenReturn(Optional.of(state));
        when(referenceRepository.renewLease(any(), anyLong(), any(), anyString(), any(), any())).thenReturn(true);
        when(referenceRepository.markReady(any(), anyLong(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(true);
        when(referenceRepository.markBlocked(any(), anyLong(), any(), anyString(), any(), any()))
                .thenReturn(true);
        when(referenceRepository.findReplicas(REPOSITORY_ID)).thenReturn(List.of());
        when(configurationRepository.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(repository));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(referencesRoot.toString()));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(SERVER_ID));
        service = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                encryptionService,
                new WorkspaceServerIdentity(SERVER_ID.value()),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void cleanRepositoryFastForwardsToFixedRemoteCommit() throws Exception {
        service.handle(syncEvent());

        assertThat(git(local, "rev-parse", "HEAD").stdoutText().trim()).isEqualTo(targetCommit);
        assertThat(Files.readString(local.resolve("README.md"))).isEqualTo("version 2\n");
        verify(referenceRepository).markReady(
                eq(REPOSITORY_ID),
                eq(1L),
                eq(SERVER_ID),
                anyString(),
                eq("main"),
                eq(targetCommit),
                eq(NOW),
                eq(NOW));
    }

    @Test
    void dirtyRepositoryIsBlockedAndLocalContentIsPreserved() throws Exception {
        Files.writeString(local.resolve("README.md"), "local dirty content\n");

        assertBlocked("引用资产本地仓库存在未提交修改");

        assertThat(Files.readString(local.resolve("README.md"))).isEqualTo("local dirty content\n");
    }

    @Test
    void originConflictIsBlocked() {
        git(local, "remote", "set-url", "origin", tempDir.resolve("other.git").toString());

        assertBlocked("引用资产本地仓库 origin 与数据库不一致");
    }

    @Test
    void branchConflictIsBlocked() {
        git(local, "checkout", "-b", "other");

        assertBlocked("引用资产本地仓库分支与初始化分支不一致");
    }

    @Test
    void divergedLocalCommitIsBlockedWithoutReset() throws Exception {
        git(local, "config", "user.name", "Local Admin");
        git(local, "config", "user.email", "local-admin@example.invalid");
        Files.writeString(local.resolve("local.txt"), "local divergence\n");
        git(local, "add", "--all");
        git(local, "commit", "-m", "diverge locally");
        String localCommit = git(local, "rev-parse", "HEAD").stdoutText().trim();

        assertBlocked("引用资产本地仓库与目标提交发生分叉");

        assertThat(git(local, "rev-parse", "HEAD").stdoutText().trim()).isEqualTo(localCommit);
    }

    private void assertBlocked(String message) {
        service.handle(syncEvent());
        verify(referenceRepository).markBlocked(
                eq(REPOSITORY_ID),
                eq(1L),
                eq(SERVER_ID),
                anyString(),
                eq(message),
                eq(NOW));
    }

    private ReferenceRepositoryReplica processingReplica(String token) {
        return new ReferenceRepositoryReplica(
                REPOSITORY_ID,
                SERVER_ID,
                1L,
                ReferenceRepositoryReplicaStatus.PROCESSING,
                "main",
                null,
                0,
                null,
                token,
                NOW.plusSeconds(120),
                null,
                null,
                NOW,
                NOW);
    }

    private ServerBroadcastEvent syncEvent() {
        return new ServerBroadcastEvent(
                "evt_real_git",
                ReferenceRepositoryApplicationService.SYNC_REQUESTED_EVENT,
                "backend-a",
                SERVER_ID.value(),
                "trace_real_git",
                NOW,
                Map.of("repositoryId", REPOSITORY_ID.value(), "generation", 1L, "traceId", "trace_real_git"));
    }

    private GitCommandResult git(Path repository, String... args) {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(args));
        return new ProcessGitCommandExecutor().execute(List.copyOf(command), null, Duration.ofSeconds(30));
    }
}
