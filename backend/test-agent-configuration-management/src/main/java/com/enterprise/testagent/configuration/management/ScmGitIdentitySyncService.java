package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitCommitterIdentityEvidence;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityCandidate;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 从本机应用仓库的远端跟踪历史补偿用户 SCM Git 姓名。 */
@Service
public class ScmGitIdentitySyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScmGitIdentitySyncService.class);
    static final int USER_PAGE_SIZE = 500;
    static final int MAX_COMMITS_PER_REPOSITORY = 50_000;
    private static final String ENTERPRISE_EMAIL_SUFFIX = "@mails.icbc";

    private final UserScmGitIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final CommonParameterValues commonParameterValues;
    private final GitWorkspaceService gitWorkspaceService;
    private final Clock clock;

    @Autowired
    public ScmGitIdentitySyncService(
            UserScmGitIdentityRepository identityRepository,
            UserRepository userRepository,
            CommonParameterValues commonParameterValues,
            Clock clock) {
        this(identityRepository, userRepository, commonParameterValues, new GitWorkspaceService(), clock);
    }

    ScmGitIdentitySyncService(
            UserScmGitIdentityRepository identityRepository,
            UserRepository userRepository,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService,
            Clock clock) {
        this.identityRepository = Objects.requireNonNull(identityRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 夜间全量补偿：每个仓库只执行一次有界 git log，然后对 SSH Key 用户做游标分页比对。
     */
    public SyncResult synchronizeAll(ScheduledTaskContext context) {
        List<Path> repositories = applicationRepositories();
        Map<String, GitCommitterIdentityEvidence> latestByEmail = new HashMap<>();
        int failedRepositories = 0;
        for (int repositoryIndex = 0; repositoryIndex < repositories.size(); repositoryIndex++) {
            Path repository = repositories.get(repositoryIndex);
            context.throwIfStopRequested();
            try {
                for (GitCommitterIdentityEvidence evidence
                        : gitWorkspaceService.acceptedCommitterIdentities(repository, MAX_COMMITS_PER_REPOSITORY)) {
                    String email = evidence.email().toLowerCase(Locale.ROOT);
                    if (!email.endsWith(ENTERPRISE_EMAIL_SUFFIX)) {
                        continue;
                    }
                    latestByEmail.merge(email, evidence, ScmGitIdentitySyncService::newerEvidence);
                }
            } catch (RuntimeException exception) {
                failedRepositories++;
                LOGGER.warn(
                        "event=scm_git_identity_repository_scan_failed repositoryIndex={} errorType={}",
                        repositoryIndex,
                        exception.getClass().getSimpleName());
            }
        }

        int checkedUsers = 0;
        int updatedUsers = 0;
        String cursor = null;
        while (true) {
            context.throwIfStopRequested();
            List<UserScmGitIdentityCandidate> page =
                    identityRepository.findSyncCandidatesAfter(cursor, USER_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            checkedUsers += page.size();
            Instant verifiedAt = clock.instant();
            List<UserScmGitIdentity> changes = new ArrayList<>();
            for (UserScmGitIdentityCandidate candidate : page) {
                GitCommitterIdentityEvidence evidence = latestByEmail.get(scmEmail(candidate.unifiedAuthId()));
                if (evidence != null && shouldApplyAcceptedEvidence(candidate.currentIdentity(), evidence)) {
                    changes.add(new UserScmGitIdentity(
                            candidate.userId(), evidence.name(), UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY,
                            evidence.commitHash(), evidence.committedAt(), verifiedAt));
                }
            }
            updatedUsers += identityRepository.upsertAcceptedCommitIdentities(changes);
            cursor = page.get(page.size() - 1).userId().value();
            if (page.size() < USER_PAGE_SIZE) {
                break;
            }
        }
        return new SyncResult(repositories.size(), failedRepositories, checkedUsers, updatedUsers);
    }

    /** SSH Key 新增后的低频定向补偿；每个仓库最多返回该邮箱的一条提交。 */
    public boolean synchronizeUser(UserId userId) {
        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            return false;
        }
        String email = scmEmail(user.unifiedAuthId());
        GitCommitterIdentityEvidence latest = null;
        for (Path repository : applicationRepositories()) {
            try {
                Optional<GitCommitterIdentityEvidence> evidence =
                        gitWorkspaceService.latestAcceptedCommitterIdentity(repository, email);
                if (evidence.isPresent()) {
                    latest = latest == null ? evidence.get() : newerEvidence(latest, evidence.get());
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "event=scm_git_identity_user_scan_failed userId={} errorType={}",
                        userId.value(), exception.getClass().getSimpleName());
            }
        }
        if (latest == null) {
            return false;
        }
        UserScmGitIdentity current = identityRepository.findByUserId(userId).orElse(null);
        if (!shouldApplyAcceptedEvidence(current, latest)) {
            return false;
        }
        Instant now = clock.instant();
        return identityRepository.upsertAcceptedCommitIdentities(List.of(new UserScmGitIdentity(
                userId, latest.name(), UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY,
                latest.commitHash(), latest.committedAt(), now))) > 0;
    }

    private List<Path> applicationRepositories() {
        String configuredRoot = commonParameterValues
                .resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_WORKSPACE_ROOT)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR, "应用版本工作区根目录未配置"));
        Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用版本工作区根目录不可用");
        }
        List<Path> repositories = new ArrayList<>();
        try (var versions = Files.list(root)) {
            for (Path version : versions
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
                    .sorted()
                    .toList()) {
                try (var children = Files.list(version)) {
                    children
                            .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                            .filter(path -> !Files.isSymbolicLink(path))
                            .filter(path -> Files.exists(path.resolve(".git"), LinkOption.NOFOLLOW_LINKS))
                            .map(path -> path.toAbsolutePath().normalize())
                            .forEach(repositories::add);
                }
            }
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "枚举应用版本仓库失败", Map.of(), exception);
        }
        return repositories.stream().distinct().sorted().toList();
    }

    private static String scmEmail(String unifiedAuthId) {
        return GitCommitIdentity.forPlatformUser("scm-sync", unifiedAuthId).email().toLowerCase(Locale.ROOT);
    }

    private static GitCommitterIdentityEvidence newerEvidence(
            GitCommitterIdentityEvidence left,
            GitCommitterIdentityEvidence right) {
        Comparator<GitCommitterIdentityEvidence> comparator = Comparator
                .comparing(GitCommitterIdentityEvidence::committedAt)
                .thenComparing(GitCommitterIdentityEvidence::commitHash);
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private static boolean shouldApplyAcceptedEvidence(
            UserScmGitIdentity current,
            GitCommitterIdentityEvidence evidence) {
        if (current == null) {
            return true;
        }
        if (current.source() == UserScmGitIdentitySource.REMOTE_REJECTION) {
            return false;
        }
        int timeOrder = evidence.committedAt().compareTo(current.evidenceAt());
        return timeOrder > 0
                || (timeOrder == 0
                    && (evidence.commitHash().compareTo(Objects.toString(current.evidenceCommit(), "")) > 0
                        || (evidence.commitHash().equals(Objects.toString(current.evidenceCommit(), ""))
                            && !evidence.name().equals(current.gitName()))));
    }

    /** 任务结果不包含姓名、邮箱或认证号。 */
    public record SyncResult(int repositoryCount, int failedRepositoryCount, int checkedUserCount, int updatedUserCount) {
    }
}
