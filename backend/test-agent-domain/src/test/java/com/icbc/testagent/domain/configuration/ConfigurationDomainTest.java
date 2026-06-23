package com.icbc.testagent.domain.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.user.UserId;
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
                false,
                NOW,
                NOW);

        CodeRepository edited = repository.editMetadata("新名称", true, NOW.plusSeconds(1));

        assertThat(edited.gitUrl()).isEqualTo("git@example.com:demo/repo.git");
        assertThat(edited.name()).isEqualTo("新名称");
        assertThat(edited.standard()).isTrue();
        assertThat(edited.updatedAt()).isEqualTo(NOW.plusSeconds(1));
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
}
