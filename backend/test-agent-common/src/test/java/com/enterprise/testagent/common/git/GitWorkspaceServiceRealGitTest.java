package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 使用临时真实 Git 仓库验证 index 白名单和三方冲突 stage，避免 fake executor 掩盖 Git 语义差异。
 */
class GitWorkspaceServiceRealGitTest {

    private static final GitCommitIdentity TEST_IDENTITY =
            GitCommitIdentity.forPlatformUser("test-user", "AUTH_TEST");

    @TempDir
    Path tempDir;

    @Test
    void initializesLocalRepositoryOnceWithoutRemoteOrOverwritingContent() throws Exception {
        Path repo = tempDir.resolve("experience");
        GitWorkspaceService service = new GitWorkspaceService();

        service.initializeLocalRepository(repo, "README.md", "initial\n", TEST_IDENTITY);
        Files.writeString(repo.resolve("README.md"), "user changed\n", StandardCharsets.UTF_8);
        service.initializeLocalRepository(repo, "README.md", "replacement\n", TEST_IDENTITY);

        assertThat(repo.resolve(".git")).isDirectory();
        assertThat(Files.readString(repo.resolve("README.md"))).isEqualTo("user changed\n");
        assertThat(git(repo, "rev-list", "--count", "HEAD").stdoutText().trim()).isEqualTo("1");
        assertThat(git(repo, "remote").stdoutText()).isBlank();
    }

    @Test
    void gitWorkTreeRootRejectsParentRepositorySubdirectory() throws Exception {
        Path repo = initializeRepository();
        Path child = Files.createDirectories(repo.resolve("experience-child"));
        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.isGitWorkTreeRoot(repo)).isTrue();
        assertThat(service.isGitWorkTreeRoot(child)).isFalse();
    }

    @Test
    void whitelistCommitLeavesResidualStagedFileOutOfCommit() throws Exception {
        Path repo = initializeRepository();
        write(repo, "selected.txt", "base selected\n");
        write(repo, "other.txt", "base other\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        write(repo, "selected.txt", "selected change\n");
        write(repo, "other.txt", "other change\n");
        git(repo, "add", "--", "other.txt");

        GitWorkspaceService service = new GitWorkspaceService();
        service.resetIndexToHead(repo, null);
        service.stageFiles(repo, List.of("selected.txt"), null);
        service.commitStaged(repo, "selected only", null, TEST_IDENTITY);

        assertThat(git(repo, "show", "--name-only", "--pretty=format:", "HEAD").stdoutText().trim())
                .isEqualTo("selected.txt");
        assertThat(service.statusPorcelain(repo)).contains(" M other.txt");
    }

    @Test
    void hasStagedChangesDistinguishesCleanIndexFromStagedContent() throws Exception {
        Path repo = initializeRepository();
        write(repo, "tracked.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.hasStagedChanges(repo, null)).isFalse();

        write(repo, "tracked.txt", "changed\n");
        git(repo, "add", "--", "tracked.txt");
        assertThat(service.hasStagedChanges(repo, null)).isTrue();
    }

    @Test
    void readsBoundedCommitHistoryDetailsAndDiffFromRealRepository() throws Exception {
        Path repo = initializeRepository();
        Files.createDirectories(repo.resolve("workspace"));
        write(repo, "workspace/member.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        write(repo, "workspace/member.txt", "member change\n");
        git(repo, "add", "--all");
        new GitWorkspaceService().commitStaged(
                repo, "member commit", null,
                GitCommitIdentity.forPlatformUser("member", "MEMBER001"));
        String head = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        List<GitWorkspaceService.GitCommitSummary> history = service.listCommitHistory(
                repo, base, head, 0, 10, true, "workspace");

        assertThat(history).singleElement().satisfies(commit -> {
            assertThat(commit.commit()).isEqualTo(head);
            assertThat(commit.committerEmail()).isEqualTo("MEMBER001@mails.icbc");
            assertThat(commit.merge()).isFalse();
        });
        assertThat(service.commitChangedFiles(repo, head))
                .extracting(GitWorkspaceService.GitNameStatusEntry::path)
                .containsExactly("workspace/member.txt");
        assertThat(service.commitFileDiff(repo, head, "workspace/member.txt"))
                .contains("-base", "+member change");
        assertThat(service.isCommitAncestor(repo, base, head)).isTrue();
        assertThatThrownBy(() -> service.commitFileDiff(repo, head, "../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksMergeCommitsAndKeepsPaginationStable() throws Exception {
        Path repo = initializeRepository();
        write(repo, "base.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "checkout", "-b", "feature");
        write(repo, "feature.txt", "feature\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "feature");
        git(repo, "checkout", "main");
        write(repo, "main.txt", "main\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "main");
        git(repo, "merge", "--no-ff", "feature", "-m", "sync merge");
        String head = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        List<GitWorkspaceService.GitCommitSummary> first = service.listCommitHistory(
                repo, base, head, 0, 2, true);
        List<GitWorkspaceService.GitCommitSummary> second = service.listCommitHistory(
                repo, base, head, 2, 2, true);

        assertThat(first).hasSize(2);
        assertThat(first.getFirst().merge()).isTrue();
        assertThat(second).hasSize(1);
        assertThat(first).extracting(GitWorkspaceService.GitCommitSummary::commit)
                .doesNotContainAnyElementsOf(second.stream()
                        .map(GitWorkspaceService.GitCommitSummary::commit).toList());
    }

    @Test
    void commitFilesOnlyDoesNotIncludeOtherUsersStagedPaths() throws Exception {
        Path repo = initializeRepository();
        write(repo, "selected.txt", "base selected\n");
        write(repo, "other.txt", "base other\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        write(repo, "selected.txt", "selected change\n");
        write(repo, "other.txt", "other staged\n");
        git(repo, "add", "--", "other.txt");

        new GitWorkspaceService().commitFilesOnly(
                repo, List.of("selected.txt"), "selected only", null, TEST_IDENTITY);

        assertThat(git(repo, "show", "--name-only", "--pretty=format:", "HEAD").stdoutText().trim())
                .isEqualTo("selected.txt");
        assertThat(git(repo, "status", "--short").stdoutText()).contains("M  other.txt");
    }

    @Test
    void mergesFixedFeatureCommitAndCompletesConflictWithNativeGitState() throws Exception {
        Path repo = initializeRepository();
        Files.createDirectories(repo.resolve("workspace/docs"));
        write(repo, "workspace/docs/design.md", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        git(repo, "checkout", "-b", "feature");
        write(repo, "workspace/docs/design.md", "feature\n");
        git(repo, "commit", "-am", "publish feature docs");
        String feature = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        git(repo, "checkout", "-b", "personal", base);
        write(repo, "workspace/docs/design.md", "personal\n");
        git(repo, "commit", "-am", "personal docs");

        GitWorkspaceService service = new GitWorkspaceService();
        assertThatThrownBy(() -> service.mergeCommit(
                repo,
                feature,
                null,
                GitCommitIdentity.forPlatformUser("publisher", "AUTH_PUBLISHER")))
                .isInstanceOf(PlatformException.class);
        assertThat(service.isMergeInProgress(repo)).isTrue();
        assertThat(service.conflictPaths(repo)).containsExactly("workspace/docs/design.md");

        service.resolveAllConflicts(
                repo,
                GitWorkspaceService.ConflictResolutionSide.INCOMING,
                null);
        service.commitStaged(
                repo,
                "完成 feature 合并",
                null,
                GitCommitIdentity.forPlatformUser("personal-user", "AUTH_PERSONAL"));

        assertThat(service.isMergeInProgress(repo)).isFalse();
        assertThat(service.isAncestor(repo, feature, "HEAD")).isTrue();
        assertThat(Files.readString(repo.resolve("workspace/docs/design.md"))).isEqualTo("feature\n");
    }

    @Test
    void nativeMergeKeepsNonOverlappingLocalChanges() throws Exception {
        Path repo = initializeRepository();
        write(repo, "local.txt", "base local\n");
        write(repo, "remote.txt", "base remote\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "checkout", "-b", "remote-change");
        write(repo, "remote.txt", "remote update\n");
        git(repo, "commit", "-am", "remote update");
        String remoteCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "checkout", "main");
        assertThat(git(repo, "rev-parse", "HEAD").stdoutText().trim()).isEqualTo(base);
        write(repo, "local.txt", "uncommitted local change\n");

        new GitWorkspaceService().mergeCommit(repo, remoteCommit, null, TEST_IDENTITY);

        assertThat(Files.readString(repo.resolve("local.txt"))).isEqualTo("uncommitted local change\n");
        assertThat(Files.readString(repo.resolve("remote.txt"))).isEqualTo("remote update\n");
        assertThat(new GitWorkspaceService().statusPorcelain(repo)).contains(" M local.txt");
    }

    @Test
    void exactCommitMergeKeepsNonOverlappingStagedUnstagedAndUntrackedChanges() throws Exception {
        Path repo = initializeRepository();
        write(repo, "staged.txt", "base staged\n");
        write(repo, "unstaged.txt", "base unstaged\n");
        write(repo, "remote.txt", "base remote\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        git(repo, "checkout", "-b", "remote-change");
        write(repo, "remote.txt", "remote update\n");
        git(repo, "commit", "-am", "remote update");
        git(repo, "checkout", "main");
        write(repo, "staged.txt", "local staged\n");
        git(repo, "add", "staged.txt");
        write(repo, "unstaged.txt", "local unstaged\n");
        write(repo, "untracked.txt", "local untracked\n");

        GitWorkspaceService service = new GitWorkspaceService();
        service.mergeCommit(
                repo,
                git(repo, "rev-parse", "remote-change").stdoutText().trim(),
                null,
                TEST_IDENTITY);

        assertThat(Files.readString(repo.resolve("staged.txt"))).isEqualTo("local staged\n");
        assertThat(Files.readString(repo.resolve("unstaged.txt"))).isEqualTo("local unstaged\n");
        assertThat(Files.readString(repo.resolve("untracked.txt"))).isEqualTo("local untracked\n");
        assertThat(Files.readString(repo.resolve("remote.txt"))).isEqualTo("remote update\n");
        assertThat(service.statusPorcelain(repo))
                .contains("M  staged.txt")
                .contains(" M unstaged.txt")
                .contains("?? untracked.txt");
    }

    @Test
    void nativeMergeReportsOnlyOverlappingLocalChangesWithoutChangingWorktree() throws Exception {
        Path repo = initializeRepository();
        write(repo, "shared.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "checkout", "-b", "remote-change");
        write(repo, "shared.txt", "remote update\n");
        git(repo, "commit", "-am", "remote update");
        String remoteCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "checkout", "main");
        write(repo, "shared.txt", "uncommitted local change\n");

        GitWorkspaceService service = new GitWorkspaceService();
        assertThatThrownBy(() -> service.mergeCommit(repo, remoteCommit, null, TEST_IDENTITY))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.details()).containsEntry("gitFailureType", "LOCAL_CHANGES");
                    assertThat(exception.details().get("gitBlockingFiles"))
                            .isEqualTo(List.of("shared.txt"));
                });

        assertThat(service.headCommit(repo)).isEqualTo(base);
        assertThat(service.isMergeInProgress(repo)).isFalse();
        assertThat(Files.readString(repo.resolve("shared.txt"))).isEqualTo("uncommitted local change\n");
    }

    @Test
    void commitStagedUsesExplicitIdentityWhenRepositoryHasNoConfiguredIdentity() throws Exception {
        Path repo = initializeRepository();
        git(repo, "config", "--unset-all", "user.name");
        git(repo, "config", "--unset-all", "user.email");
        write(repo, "identity.txt", "committed by current user\n");
        git(repo, "add", "--all");

        GitWorkspaceService service = new GitWorkspaceService();
        service.commitStaged(
                repo,
                "identity commit",
                null,
                GitCommitIdentity.forPlatformUser("alice", "AUTH_ALICE"));

        assertThat(git(repo, "show", "-s", "--format=%an <%ae>|%cn <%ce>", "HEAD").stdoutText().trim())
                .isEqualTo("alice <AUTH_ALICE@mails.icbc>|alice <AUTH_ALICE@mails.icbc>");
    }

    @Test
    void readsAcceptedRemoteCommitterAndAmendsHeadIdentity() throws Exception {
        Path repo = initializeRepository();
        write(repo, "identity.txt", "accepted\n");
        git(repo, "add", "--all");
        new GitWorkspaceService().commitStaged(
                repo,
                "accepted identity",
                null,
                GitCommitIdentity.forPlatformUser("测试用户", "123456789"));
        git(repo, "update-ref", "refs/remotes/origin/main", "HEAD");

        GitWorkspaceService service = new GitWorkspaceService();
        assertThat(service.latestAcceptedCommitterIdentity(repo, "123456789@mails.icbc"))
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.name()).isEqualTo("测试用户");
                    assertThat(evidence.email()).isEqualTo("123456789@mails.icbc");
                });
        assertThat(service.acceptedCommitterIdentities(repo, 100))
                .anySatisfy(evidence -> assertThat(evidence.name()).isEqualTo("测试用户"));

        write(repo, "identity.txt", "wrong\n");
        git(repo, "add", "--all");
        service.commitStaged(
                repo,
                "wrong identity",
                null,
                GitCommitIdentity.forPlatformUser("测试用户1", "123456789"));
        String amended = service.amendHeadCommitIdentity(
                repo, null, GitCommitIdentity.forPlatformUser("测试用户", "123456789"));

        assertThat(amended).isEqualTo(service.headCommit(repo));
        assertThat(git(repo, "show", "-s", "--format=%an <%ae>|%cn <%ce>", "HEAD").stdoutText().trim())
                .isEqualTo("测试用户 <123456789@mails.icbc>|测试用户 <123456789@mails.icbc>");
    }

    @Test
    void listsAndReadsChineseSkillFilesAtExactCommitWithoutQuotedPathLeakage() throws Exception {
        Path repo = initializeRepository();
        String skillRoot = "F-SLB/F-SLB-CONSOLE/.opencode/skills/SLB快速检索环境应用所有端口策略";
        Files.createDirectories(repo.resolve(skillRoot));
        write(repo, skillRoot + "/.gitkeep", "");
        write(repo, skillRoot + "/SKILL.md", "---\nname: slb-port-policy\n---\n中文说明\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "add chinese skill");
        String commit = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        List<String> files = service.listFilesAtCommit(
                repo,
                commit,
                "F-SLB/F-SLB-CONSOLE/.opencode");

        assertThat(files).containsExactly(
                skillRoot + "/.gitkeep",
                skillRoot + "/SKILL.md");
        assertThat(new String(
                service.readFileAtCommit(repo, commit, skillRoot + "/SKILL.md"),
                StandardCharsets.UTF_8))
                .isEqualTo("---\nname: slb-port-policy\n---\n中文说明\n");
    }

    @Test
    void createsLinearPublicationCommitWithoutPollutedPersonalHistory() throws Exception {
        Path repo = initializeRepository();
        Path remote = tempDir.resolve("public-agent.git");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");
        git(repo, "remote", "add", "origin", remote.toString());
        write(repo, "opencode.jsonc", "{\"version\": 1}\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "remote base");
        String remoteCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        git(repo, "config", "user.name", "Legacy Agent");
        git(repo, "config", "user.email", "legacy@testagent.local");
        write(repo, "legacy.txt", "legacy content retained\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "polluted identity");
        String pollutedCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        write(repo, "opencode.jsonc", "{\"version\": 2}\n");
        git(repo, "commit", "-am", "current update");
        String personalCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        String publicationCommit = service.createLinearCommitFromTree(
                repo,
                personalCommit,
                remoteCommit,
                "发布公共 Agent 配置",
                GitCommitIdentity.forPlatformUser("admin", "001177621"));

        assertThat(git(repo, "rev-parse", publicationCommit + "^").stdoutText().trim())
                .isEqualTo(remoteCommit);
        assertThat(git(repo, "rev-parse", publicationCommit + "^{tree}").stdoutText().trim())
                .isEqualTo(git(repo, "rev-parse", personalCommit + "^{tree}").stdoutText().trim());
        assertThat(service.isAncestor(repo, pollutedCommit, publicationCommit)).isFalse();
        assertThat(git(repo, "show", "-s", "--format=%an <%ae>|%cn <%ce>", publicationCommit).stdoutText().trim())
                .isEqualTo("admin <001177621@mails.icbc>|admin <001177621@mails.icbc>");

        service.resetHardToCommit(repo, publicationCommit);
        service.pushRef(repo, "main", "main", null);
        assertThat(git(remote, "rev-parse", "refs/heads/main").stdoutText().trim()).isEqualTo(publicationCommit);
        assertThat(service.headCommit(repo)).isEqualTo(publicationCommit);
    }

    @Test
    void reusesRemoteCommitWhenPublicationTreeHasNoChanges() throws Exception {
        Path repo = initializeRepository();
        write(repo, "opencode.jsonc", "{}\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "remote base");
        String remoteCommit = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "commit", "--allow-empty", "-m", "polluted empty commit");

        String publicationCommit = new GitWorkspaceService().createLinearCommitFromTree(
                repo,
                "HEAD",
                remoteCommit,
                "发布公共 Agent 配置",
                TEST_IDENTITY);

        assertThat(publicationCommit).isEqualTo(remoteCommit);
    }

    @Test
    void exposesBaseCurrentAndIncomingForRealMergeConflict() throws Exception {
        Path repo = initializeRepository();
        write(repo, "conflict.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        git(repo, "checkout", "-b", "application");
        write(repo, "conflict.txt", "incoming\n");
        git(repo, "commit", "-am", "application change");
        git(repo, "checkout", "main");
        write(repo, "conflict.txt", "current\n");
        git(repo, "commit", "-am", "personal change");
        try {
            git(repo, "merge", "--no-ff", "application");
        } catch (RuntimeException ignored) {
            // 预期由真实 Git 生成冲突 index。
        }

        GitWorkspaceService service = new GitWorkspaceService();
        assertThat(service.conflictStages(repo, "conflict.txt")).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(service.conflictStageContent(repo, 1, "conflict.txt")).isEqualTo("base\n");
        assertThat(service.conflictStageContent(repo, 2, "conflict.txt")).isEqualTo("current\n");
        assertThat(service.conflictStageContent(repo, 3, "conflict.txt")).isEqualTo("incoming\n");
        assertThat(service.isMergeInProgress(repo)).isTrue();
    }

    @Test
    void resolvesChineseAddDeleteConflictsWithNativeCurrentAndIncomingStages() throws Exception {
        Path currentRepo = createAddDeleteConflict("current");
        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.conflictPaths(currentRepo))
                .containsExactlyInAnyOrder("中文/个人新增.txt", "中文/远程新增.txt");
        service.resolveAllConflicts(
                currentRepo,
                GitWorkspaceService.ConflictResolutionSide.CURRENT,
                null);

        assertThat(service.conflictPaths(currentRepo)).isEmpty();
        assertThat(Files.readString(currentRepo.resolve("中文/个人新增.txt"))).isEqualTo("current\n");
        assertThat(currentRepo.resolve("中文/远程新增.txt")).doesNotExist();

        Path incomingRepo = createAddDeleteConflict("incoming");
        service.resolveAllConflicts(
                incomingRepo,
                GitWorkspaceService.ConflictResolutionSide.INCOMING,
                null);

        assertThat(service.conflictPaths(incomingRepo)).isEmpty();
        assertThat(incomingRepo.resolve("中文/个人新增.txt")).doesNotExist();
        assertThat(Files.readString(incomingRepo.resolve("中文/远程新增.txt"))).isEqualTo("incoming\n");
    }

    @Test
    void fetchesRemoteAgentCommitIntoPersonalAndSharedRepositories() throws Exception {
        Path remote = tempDir.resolve("public-agent.git");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");

        Path writer = tempDir.resolve("remote-writer");
        git(tempDir, "clone", remote.toString(), writer.toString());
        git(writer, "config", "user.name", "Remote Admin");
        git(writer, "config", "user.email", "remote-admin@example.invalid");
        git(writer, "checkout", "-b", "main");
        Files.createDirectories(writer.resolve("opencode/agents"));
        write(writer, "opencode/agents/remote-review.md", "version 1\n");
        git(writer, "add", "--all");
        git(writer, "commit", "-m", "initial public agent");
        git(writer, "push", "-u", "origin", "main");

        Path personal = tempDir.resolve("public-admin-worktree");
        Path shared = tempDir.resolve("public-runtime");
        git(tempDir, "clone", "--branch", "main", remote.toString(), personal.toString());
        git(tempDir, "clone", "--branch", "main", remote.toString(), shared.toString());

        write(writer, "opencode/agents/remote-review.md", "version 2 from remote\n");
        git(writer, "commit", "-am", "update public agent remotely");
        git(writer, "push", "origin", "main");

        GitWorkspaceService service = new GitWorkspaceService();
        service.fetch(personal, null);
        service.mergeBranch(
                personal,
                "origin/main",
                null,
                GitCommitIdentity.forPlatformUser("admin", "AUTH_ADMIN"));
        service.fetch(shared, null);
        String remoteCommit = git(shared, "rev-parse", "origin/main").stdoutText().trim();
        service.resetHardToCommit(shared, remoteCommit);

        assertThat(Files.readString(personal.resolve("opencode/agents/remote-review.md")))
                .isEqualTo("version 2 from remote\n");
        assertThat(Files.readString(shared.resolve("opencode/agents/remote-review.md")))
                .isEqualTo("version 2 from remote\n");
        assertThat(git(shared, "rev-parse", "HEAD").stdoutText().trim()).isEqualTo(remoteCommit);
    }

    @Test
    void resolvesLatestCommitOfExactRemoteBranch() throws Exception {
        Path remote = tempDir.resolve("reference-assets.git");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");

        Path writer = tempDir.resolve("reference-writer");
        git(tempDir, "clone", remote.toString(), writer.toString());
        git(writer, "config", "user.name", "Reference Admin");
        git(writer, "config", "user.email", "reference-admin@example.invalid");
        git(writer, "checkout", "-b", "main");
        write(writer, "README.md", "version 1\n");
        git(writer, "add", "--all");
        git(writer, "commit", "-m", "initial reference asset");
        git(writer, "push", "-u", "origin", "main");
        write(writer, "README.md", "version 2\n");
        git(writer, "commit", "-am", "update reference asset");
        git(writer, "push", "origin", "main");
        String expectedCommit = git(writer, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.resolveRemoteBranchCommit(remote.toString(), "main", null))
                .isEqualTo(expectedCommit);
    }

    @Test
    void safelyChecksOutExistingTargetBranchOnlyWhenItCanFastForward() throws Exception {
        Path repo = initializeRepository();
        write(repo, "base.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String releaseBeforeReset = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "branch", "release");
        write(repo, "release.txt", "release\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "release target");
        String target = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        service.checkoutBranchForFixedCommit(repo, "release", target, null);

        assertThat(service.currentBranch(repo)).isEqualTo("release");
        assertThat(service.headCommit(repo)).isEqualTo(releaseBeforeReset);
        service.resetHardToCommit(repo, target);
        assertThat(service.headCommit(repo)).isEqualTo(target);

        git(repo, "checkout", "main");
        git(repo, "branch", "-D", "release");
        git(repo, "checkout", "--orphan", "release");
        git(repo, "rm", "-rf", ".");
        write(repo, "diverged.txt", "diverged\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "diverged release");
        git(repo, "checkout", "main");

        assertThatThrownBy(() -> service.checkoutBranchForFixedCommit(repo, "release", target, null))
                .isInstanceOf(PlatformException.class);
        assertThat(service.currentBranch(repo)).isEqualTo("main");
    }

    @Test
    void readOnlyCleanCheckDoesNotRefreshOrRewriteIndex() throws Exception {
        Path repo = initializeRepository();
        write(repo, "tracked.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        write(repo, "tracked.txt", "dirty\n");
        write(repo, "untracked.txt", "untracked\n");
        Path index = repo.resolve(".git/index");
        FileTime preservedTime = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"));
        Files.setLastModifiedTime(index, preservedTime);
        byte[] before = Files.readAllBytes(index);

        GitCommandExecutor.startRecording();
        boolean clean;
        List<String> commands;
        try {
            clean = new GitWorkspaceService().isWorktreeCleanReadOnly(repo);
        } finally {
            commands = GitCommandExecutor.stopRecording();
        }

        assertThat(clean).isFalse();
        assertThat(Files.readAllBytes(index)).containsExactly(before);
        assertThat(Files.getLastModifiedTime(index)).isEqualTo(preservedTime);
        assertThat(commands).singleElement().satisfies(command -> {
            assertThat(command).contains("git --no-optional-locks");
            assertThat(command).contains("status --porcelain --untracked-files=all");
            assertThat(command).contains("core.untrackedCache=false");
            assertThat(command).contains("core.fsmonitor=false");
        });

        Files.setLastModifiedTime(index, preservedTime);
        before = Files.readAllBytes(index);
        String porcelain = new GitWorkspaceService().statusPorcelainReadOnly(repo);
        assertThat(porcelain).contains("tracked.txt", "untracked.txt");
        assertThat(Files.readAllBytes(index)).containsExactly(before);
        assertThat(Files.getLastModifiedTime(index)).isEqualTo(preservedTime);
    }

    private Path createAddDeleteConflict(String suffix) throws Exception {
        Path repo = tempDir.resolve("repo-" + suffix);
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "Test Agent");
        git(repo, "config", "user.email", "test-agent@example.invalid");
        Files.createDirectories(repo.resolve("中文"));
        write(repo, "中文/个人新增.txt", "base-current\n");
        write(repo, "中文/远程新增.txt", "base-incoming\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "conflict base");
        git(repo, "checkout", "-b", "application");
        Files.delete(repo.resolve("中文/个人新增.txt"));
        write(repo, "中文/远程新增.txt", "incoming\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "application delete and modify");
        git(repo, "checkout", "main");
        write(repo, "中文/个人新增.txt", "current\n");
        Files.delete(repo.resolve("中文/远程新增.txt"));
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "personal modify and delete");
        try {
            git(repo, "merge", "--no-ff", "application");
        } catch (RuntimeException ignored) {
            // 预期生成 UD/DU 冲突。
        }
        return repo;
    }

    private Path initializeRepository() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "Test Agent");
        git(repo, "config", "user.email", "test-agent@example.invalid");
        return repo;
    }

    private void write(Path repo, String file, String content) throws Exception {
        Files.writeString(repo.resolve(file), content, StandardCharsets.UTF_8);
    }

    private GitCommandResult git(Path repo, String... args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repo.toString());
        command.addAll(List.of(args));
        return new ProcessGitCommandExecutor().execute(List.copyOf(command), null, Duration.ofSeconds(30));
    }
}
