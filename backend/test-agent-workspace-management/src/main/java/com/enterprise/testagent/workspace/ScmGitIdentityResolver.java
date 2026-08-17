package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.ScmGitIdentityRejectedException;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 解析平台用户的 SCM Git 身份，并消费右控返回的可信姓名证据。 */
@Component
public class ScmGitIdentityResolver {

    private final UserRepository userRepository;
    private final UserScmGitIdentityRepository identityRepository;
    private final Clock clock;

    public ScmGitIdentityResolver(
            UserRepository userRepository,
            UserScmGitIdentityRepository identityRepository,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.identityRepository = Objects.requireNonNull(identityRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 优先使用已校准 Git 姓名；没有证据时兼容沿用平台展示名。 */
    public GitCommitIdentity resolve(UserId userId) {
        User user = existingUser(userId);
        String gitName = identityRepository.findByUserId(userId)
                .map(UserScmGitIdentity::gitName)
                .orElse(user.username());
        return GitCommitIdentity.forPlatformUser(gitName, user.unifiedAuthId());
    }

    /**
     * 仅当拒绝内容与本次实际身份逐项吻合时写入并返回纠正身份，防止解析到旁路日志后误改提交。
     */
    public Optional<GitCommitIdentity> learnFromRejection(
            UserId userId,
            GitCommitIdentity attempted,
            ScmGitIdentityRejectedException rejection) {
        User user = existingUser(userId);
        GitCommitIdentity platformIdentity = GitCommitIdentity.forPlatformUser(user.username(), user.unifiedAuthId());
        if (!platformIdentity.email().equalsIgnoreCase(rejection.email())
                || !attempted.email().equalsIgnoreCase(rejection.email())
                || !attempted.name().equals(rejection.actualName())) {
            return Optional.empty();
        }
        GitCommitIdentity corrected;
        try {
            corrected = new GitCommitIdentity(rejection.expectedName(), attempted.email().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidIdentity) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        identityRepository.upsertRemoteRejection(new UserScmGitIdentity(
                userId,
                corrected.name(),
                UserScmGitIdentitySource.REMOTE_REJECTION,
                rejection.evidenceCommit(),
                now,
                now));
        return Optional.of(corrected);
    }

    private User existingUser(UserId userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND, "用户不存在", Map.of("userId", userId.value())));
    }
}
