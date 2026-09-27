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
import com.enterprise.testagent.domain.team.TeamMembershipState;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.ApplicationView;
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
        when(queries.findWorkspaceVersions(false, member, personal.applicationWorkspaceId().value(), null))
                .thenReturn(List.of(new WorkspaceVersionView(version, TeamMembershipState.CURRENT)));
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

    @Test
    void memberCatalogPreservesHistoricalMembership() {
        TeamWorkspaceQueryRepository queries = mock(TeamWorkspaceQueryRepository.class);
        UserId owner = new UserId("owner-1");
        UserId member = new UserId("member-1");
        when(queries.findApplications(false, owner, member)).thenReturn(List.of(new ApplicationView(
                "app-1", "历史应用", true, 0, 1, TeamMembershipState.HISTORICAL)));
        TeamWorkspaceApplicationService service = new TeamWorkspaceApplicationService(
                queries, ManagedWorkspacePathResolver.legacyOnly(), new WorkspaceServerIdentity("server-a"),
                new GitWorkspaceService(), mock(ScmGitIdentityResolver.class));

        assertThat(service.applications(false, owner, member)).singleElement().satisfies(item -> {
            assertThat(item.appId()).isEqualTo("app-1");
            assertThat(item.membershipState()).isEqualTo("HISTORICAL");
        });
    }

    private void commit(
            Path repo, GitWorkspaceService git, String content, String message, GitCommitIdentity identity)
            throws Exception {
        Files.writeString(repo.resolve("README.md"), content, StandardCharsets.UTF_8);
        git.stageFiles(repo, List.of("README.md"), null);
        git.commitStaged(repo, message, null, identity);
    }

    @Test
    void reviewReadsBeyond24FilesAndDetectsSameSizeSameTimeChanges() throws Exception {
        Path repo = tempDir.resolve("review-repo");
        GitWorkspaceService git = new GitWorkspaceService();
        var author = GitCommitIdentity.forPlatformUser("真正修改人", "AUTHOR001");
        git.initializeLocalRepository(repo, "README.md", "base\n", author);
        Files.createDirectories(repo.resolve("spec"));
        for (int i = 1; i <= 30; i++) Files.writeString(repo.resolve("spec/file-" + i + ".md"), "正文-" + i);
        git.stageFiles(repo, List.of("spec"), null);
        git.commitStaged(repo, "30 个文件", null, author);
        Files.writeString(repo.resolve("spec/.env"), "private");
        Files.writeString(repo.resolve("spec/.envrc"), "private");
        Files.writeString(repo.resolve("spec/id_ed25519"), "private");
        Files.createSymbolicLink(repo.resolve("spec/external"), tempDir);
        var queries = mock(TeamWorkspaceQueryRepository.class);
        var identities = mock(ScmGitIdentityResolver.class);
        var owner = new UserId("owner");
        var member = new UserId("member");
        var personal = personal(repo, member, git.headCommit(repo));
        when(queries.findPersonalWorkspace(false, owner, personal.personalWorkspaceId())).thenReturn(Optional.of(new PersonalWorkspaceView(personal, "server-a")));
        when(queries.findWorkspaceVersions(false, owner, personal.applicationWorkspaceId().value(), null))
                .thenReturn(List.of(new WorkspaceVersionView(version(repo, member, git.headCommit(repo)), TeamMembershipState.CURRENT)));
        when(identities.resolve(member)).thenReturn(author);
        var service = new TeamWorkspaceApplicationService(queries, ManagedWorkspacePathResolver.legacyOnly(), new WorkspaceServerIdentity("server-a"), git, identities);
        for (String protectedFile : List.of("spec/.env", "spec/.envrc", "spec/id_ed25519"))
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.reviewRead(false, owner,
                    personal.personalWorkspaceId().value(), protectedFile, "ignored", 0)).hasMessageContaining("敏感凭据");
        var listing = service.reviewList(false, owner, personal.personalWorkspaceId().value(), "spec");
        assertThat(listing).hasSize(30).allSatisfy(file -> {
            assertThat(file.author()).isEqualTo("真正修改人");
            assertThat(file.timeType()).isEqualTo("GIT_COMMIT");
        });
        var chosen = listing.stream().filter(file -> file.path().equals("spec/file-30.md")).findFirst().orElseThrow();
        assertThat(service.reviewRead(false, owner, personal.personalWorkspaceId().value(), chosen.path(), chosen.contentVersion(), 0).content()).isEqualTo("正文-30");
        Path target = repo.resolve(chosen.path());
        var time = Files.getLastModifiedTime(target);
        Files.writeString(target, "正文-99");
        Files.setLastModifiedTime(target, time);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.reviewRead(false, owner, personal.personalWorkspaceId().value(), chosen.path(), chosen.contentVersion(), 0))
                .hasMessageContaining("已变化");
        var dirty = service.reviewList(false, owner, personal.personalWorkspaceId().value(), "spec").stream().filter(file -> file.path().equals(chosen.path())).findFirst().orElseThrow();
        assertThat(dirty.author()).isNull();
        assertThat(dirty.timeType()).isEqualTo("FILE_TIME");
        Files.delete(target);
        assertThat(service.reviewList(false, owner, personal.personalWorkspaceId().value(), "spec")).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo(chosen.path()); assertThat(file.deleted()).isTrue();
        });
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
