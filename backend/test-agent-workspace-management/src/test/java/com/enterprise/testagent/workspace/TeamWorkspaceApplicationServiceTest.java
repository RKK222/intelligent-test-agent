package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.team.TeamContributionType;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.WorkspaceVersionView;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 使用真实 Git 仓库验证已发布提交按现有 SCM 校准身份归属。 */
class TeamWorkspaceApplicationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void publishedCommitsRequireBothCalibratedNameAndEnterpriseEmail() throws Exception {
        Path repo = tempDir.resolve("repo");
        GitWorkspaceService git = new GitWorkspaceService();
        git.initializeLocalRepository(
                repo, "README.md", "base\n", GitCommitIdentity.forPlatformUser("base", "BASE001"));
        String base = git.headCommit(repo);
        GitCommitIdentity calibrated = GitCommitIdentity.forPlatformUser("校准姓名", "MEMBER001");

        commit(repo, git, "wrong name\n", "同邮箱错误姓名", new GitCommitIdentity("错误姓名", calibrated.email()));
        commit(repo, git, "accepted\n", "校准提交", calibrated);
        String target = git.headCommit(repo);

        TeamWorkspaceQueryRepository queries = mock(TeamWorkspaceQueryRepository.class);
        ScmGitIdentityResolver identities = mock(ScmGitIdentityResolver.class);
        UserId member = new UserId("member-1");
        PersonalWorkspace personal = personal(repo, member, base);
        ApplicationWorkspaceVersion version = version(repo, member, target);
        when(queries.findPersonalWorkspace(false, member, personal.personalWorkspaceId()))
                .thenReturn(Optional.of(new PersonalWorkspaceView(personal, "server-a")));
        when(queries.findWorkspaceVersions(false, member, personal.applicationWorkspaceId().value()))
                .thenReturn(List.of(new WorkspaceVersionView(version)));
        when(identities.resolve(member)).thenReturn(calibrated);
        TeamWorkspaceApplicationService service = new TeamWorkspaceApplicationService(
                queries, ManagedWorkspacePathResolver.legacyOnly(), new WorkspaceServerIdentity("server-a"),
                git, identities);

        var page = service.commits(
                false, member, personal.personalWorkspaceId().value(), "PUBLISHED", 0, 20);

        assertThat(page.attributionConfirmed()).isTrue();
        assertThat(page.items()).singleElement().satisfies(commit -> {
            assertThat(commit.subject()).isEqualTo("校准提交");
            assertThat(commit.contributionType()).isEqualTo(TeamContributionType.PUBLISHED_COMMIT.name());
        });
    }

    private void commit(
            Path repo, GitWorkspaceService git, String content, String message, GitCommitIdentity identity)
            throws Exception {
        Files.writeString(repo.resolve("README.md"), content, StandardCharsets.UTF_8);
        git.stageFiles(repo, List.of("README.md"), null);
        git.commitStaged(repo, message, null, identity);
    }

    private PersonalWorkspace personal(Path repo, UserId member, String base) {
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        return new PersonalWorkspace(
                new PersonalWorkspaceId("pw-team"), new ApplicationWorkspaceVersionId("version-team"),
                new ApplicationId("app-team"), new ApplicationWorkspaceId("awp_team"), member,
                "default", "feature", repo.toString(), repo.toString(), new WorkspaceId("wrk_runtime_personal"),
                base, ManagedWorkspaceStatus.ACTIVE, now, now);
    }

    private ApplicationWorkspaceVersion version(Path repo, UserId member, String target) {
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        return new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId("version-team"),
                new ApplicationWorkspaceId("awp_team"), new ApplicationId("app-team"),
                new CodeRepositoryId("repo_team"), "20260921", "feature", repo.toString(), repo.toString(),
                new WorkspaceId("wrk_runtime_version"), member, ManagedWorkspaceStatus.ACTIVE,
                target, now, now, now);
    }
}
