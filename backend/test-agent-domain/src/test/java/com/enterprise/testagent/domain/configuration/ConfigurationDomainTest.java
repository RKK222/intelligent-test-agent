package com.enterprise.testagent.domain.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConfigurationDomainTest {

    private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

    @Test
    void codeRepositoryKeepsGitUrlImmutableWhenEditingMetadata() {
        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_1234567890abcdef"),
                "git@example.com:demo/repo.git",
                "旧名称",
                "demo",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);

        CodeRepository edited = repository.editMetadata(
                "新名称",
                "demonew",
                CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                NOW.plusSeconds(1));

        assertThat(edited.gitUrl()).isEqualTo("git@example.com:demo/repo.git");
        assertThat(edited.name()).isEqualTo("新名称");
        assertThat(edited.englishName()).isEqualTo("demonew");
        assertThat(edited.repositoryType()).isEqualTo(CodeRepositoryType.TEST_WORK_REPOSITORY.value());
        assertThat(edited.standard()).isTrue();
        assertThat(edited.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void codeRepositoryDerivesLegacyStandardFlagFromRepositoryType() {
        CodeRepository testWorkRepository = new CodeRepository(
                new CodeRepositoryId("repo_test_work"),
                "git@example.com:demo/test-work.git",
                "测试工作库",
                "testwork",
                CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                false,
                NOW,
                NOW);
        CodeRepository applicationAssetRepository = new CodeRepository(
                new CodeRepositoryId("repo_asset"),
                "git@example.com:demo/asset.git",
                "应用资产库",
                "asset",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                true,
                NOW,
                NOW);

        assertThat(testWorkRepository.standard()).isTrue();
        assertThat(applicationAssetRepository.standard()).isFalse();
    }

    @Test
    void codeRepositoryDefaultsToExternalDeploymentMode() {
        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_external"),
                "git@example.com:demo/repo.git",
                "应用代码库",
                "repo",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);

        assertThat(repository.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.EXTERNAL.value());
        assertThat(repository.effectiveGitUrl("001177621")).isEqualTo("git@example.com:demo/repo.git");
        assertThat(repository.matchesStoredOrigin("git@example.com:demo/repo.git")).isTrue();
    }

    @Test
    void internalCodeRepositoryBuildsRuntimeGitUrlAndComparesOriginByStoredPart() {
        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_internal"),
                "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
                "接口平台",
                "hzefficiencytools-interfaceplatform",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                false,
                NOW,
                NOW);

        assertThat(repository.effectiveGitUrl("001177621"))
                .isEqualTo("ssh://001177621@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform");
        assertThat(repository.matchesStoredOrigin("ssh://009988776@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform"))
                .isTrue();
        assertThat(repository.matchesStoredOrigin("ssh://009988776@scm-share.sdc.cs.enterprise:29418/other/repo"))
                .isFalse();
    }

    @Test
    void applicationMemberDeletionOnlyMarksDeletedAt() {
        ApplicationMember member = ApplicationMember.active(
                new ApplicationId("app_gcms"),
                new UserId("usr_1234567890abcdef"),
                NOW);

        ApplicationMember deleted = member.markDeleted(NOW.plusSeconds(5));

        assertThat(deleted.deletedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void applicationWorkspaceRejectsBlankDirectoryPath() {
        assertThatThrownBy(() -> new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_1234567890abcdef"),
                new ApplicationId("app_gcms"),
                new CodeRepositoryId("repo_1234567890abcdef"),
                "main",
                " ",
                "demo",
                NOW,
                NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationWorkspaceCanChangeEnabledStateWithoutChangingItsLocation() {
        ApplicationWorkspace workspace = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_1234567890abcdef"),
                new ApplicationId("app_gcms"),
                new CodeRepositoryId("repo_1234567890abcdef"),
                "main",
                "F-GCMS/workspace",
                "demo",
                true,
                NOW,
                NOW);

        ApplicationWorkspace disabled = workspace.withEnabled(false, NOW.plusSeconds(10));

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.workspaceName()).isEqualTo("demo");
        assertThat(disabled.directoryPath()).isEqualTo("F-GCMS/workspace");
        assertThat(disabled.updatedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
