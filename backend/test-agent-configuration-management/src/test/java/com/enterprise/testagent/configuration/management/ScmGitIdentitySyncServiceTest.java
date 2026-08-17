package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitCommitterIdentityEvidence;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityCandidate;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ScmGitIdentitySyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void scansEachRepositoryOnceAndPagesUsersInsteadOfMultiplyingGitCommands() throws Exception {
        Path first = createRepository("20261009", "slbci-aitestagent");
        Path second = createRepository("20261010", "otherci-aitestagent");
        Instant acceptedAt = Instant.parse("2026-08-13T10:00:00Z");
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        when(git.acceptedCommitterIdentities(any(), eq(50_000))).thenReturn(List.of(
                new GitCommitterIdentityEvidence(
                        "测试用户", "123456789@mails.icbc", acceptedAt,
                        "644d15a2a63077feb74233690f2a260c55a719dd")));

        UserScmGitIdentityRepository identities = mock(UserScmGitIdentityRepository.class);
        UserScmGitIdentity protectedIdentity = new UserScmGitIdentity(
                new UserId("usr-2"), "右控姓名", UserScmGitIdentitySource.REMOTE_REJECTION,
                "abcdef1", acceptedAt.minusSeconds(60), acceptedAt.minusSeconds(60));
        UserScmGitIdentity staleName = new UserScmGitIdentity(
                new UserId("usr-1"), "测试用户1", UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY,
                "644d15a2a63077feb74233690f2a260c55a719dd", acceptedAt, acceptedAt);
        when(identities.findSyncCandidatesAfter(null, 500)).thenReturn(List.of(
                new UserScmGitIdentityCandidate(new UserId("usr-1"), "123456789", staleName),
                new UserScmGitIdentityCandidate(new UserId("usr-2"), "123456789", protectedIdentity)));
        when(identities.upsertAcceptedCommitIdentities(any())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_WORKSPACE_ROOT))
                .thenReturn(Optional.of(tempDir.toString()));
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        ScmGitIdentitySyncService service = new ScmGitIdentitySyncService(
                identities,
                mock(UserRepository.class),
                parameters,
                git,
                Clock.fixed(Instant.parse("2026-08-13T11:00:00Z"), ZoneOffset.UTC));

        ScmGitIdentitySyncService.SyncResult result = service.synchronizeAll(context);

        assertThat(result.repositoryCount()).isEqualTo(2);
        assertThat(result.checkedUserCount()).isEqualTo(2);
        assertThat(result.updatedUserCount()).isEqualTo(1);
        verify(git, times(1)).acceptedCommitterIdentities(first, 50_000);
        verify(git, times(1)).acceptedCommitterIdentities(second, 50_000);
        ArgumentCaptor<List<UserScmGitIdentity>> changes = ArgumentCaptor.forClass(List.class);
        verify(identities).upsertAcceptedCommitIdentities(changes.capture());
        assertThat(changes.getValue())
                .singleElement()
                .satisfies(identity -> {
                    assertThat(identity.userId()).isEqualTo(new UserId("usr-1"));
                    assertThat(identity.gitName()).isEqualTo("测试用户");
                });
    }

    private Path createRepository(String version, String name) throws Exception {
        Path repository = Files.createDirectories(tempDir.resolve(version).resolve(name));
        Files.createDirectories(repository.resolve(".git"));
        return repository.toAbsolutePath().normalize();
    }
}
