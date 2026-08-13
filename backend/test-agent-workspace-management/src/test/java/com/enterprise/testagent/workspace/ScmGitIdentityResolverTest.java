package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.ScmGitIdentityRejectedException;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScmGitIdentityResolverTest {

    @Test
    void learnsExactRightControlNameWithoutRemovingDigitsHeuristically() {
        UserId userId = new UserId("usr-1");
        User user = new User(
                userId, "123456789", "测试用户1", "hash", null, null, null,
                com.enterprise.testagent.domain.user.UserStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUserId(userId)).thenReturn(Optional.of(user));
        UserScmGitIdentityRepository identities = mock(UserScmGitIdentityRepository.class);
        ScmGitIdentityResolver resolver = new ScmGitIdentityResolver(
                users,
                identities,
                Clock.fixed(Instant.parse("2026-08-13T11:00:00Z"), ZoneOffset.UTC));
        ScmGitIdentityRejectedException rejection = ScmGitIdentityRejectedException.parse(
                "commit:644d15a提交失败, 客户端提交者邮箱123456789@mails.icbc对应的姓名应为测试用户,"
                        + "您的提交者姓名为测试用户1,校验不一致",
                "git push",
                1).orElseThrow();

        assertThat(resolver.learnFromRejection(
                        userId,
                        GitCommitIdentity.forPlatformUser("测试用户1", "123456789"),
                        rejection))
                .contains(GitCommitIdentity.forPlatformUser("测试用户", "123456789"));
        ArgumentCaptor<UserScmGitIdentity> saved = ArgumentCaptor.forClass(UserScmGitIdentity.class);
        verify(identities).upsertRemoteRejection(saved.capture());
        assertThat(saved.getValue().gitName()).isEqualTo("测试用户");
        assertThat(saved.getValue().evidenceCommit()).isEqualTo("644d15a");
    }

    @Test
    void rejectsRightControlEvidenceThatDoesNotMatchAttemptedCommitIdentity() {
        UserId userId = new UserId("usr-1");
        User user = new User(
                userId, "123456789", "测试用户1", "hash", null, null, null,
                com.enterprise.testagent.domain.user.UserStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUserId(userId)).thenReturn(Optional.of(user));
        UserScmGitIdentityRepository identities = mock(UserScmGitIdentityRepository.class);
        ScmGitIdentityResolver resolver = new ScmGitIdentityResolver(
                users, identities, Clock.systemUTC());
        ScmGitIdentityRejectedException rejection = ScmGitIdentityRejectedException.parse(
                "客户端提交者邮箱123456789@mails.icbc对应的姓名应为测试用户,"
                        + "您的提交者姓名为测试用户1,校验不一致",
                "git push",
                1).orElseThrow();

        assertThat(resolver.learnFromRejection(
                        userId,
                        GitCommitIdentity.forPlatformUser("另一个本地提交者", "123456789"),
                        rejection))
                .isEmpty();
        verifyNoInteractions(identities);
    }
}
