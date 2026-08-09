package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommandExecutor;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceBinding;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 平台体验工作区应用服务。
 *
 * <p>服务只接管管理员预先准备好的本地 Git 目录，不创建目录、不执行 git init，也不读取或修改 remote。
 * 同一服务器与真实路径生成稳定 Workspace ID，并在每次访问时重新核对用户资格与当前配置。
 */
@Service
public class ExperienceWorkspaceApplicationService implements ExperienceWorkspaceAccessAuthorizer {

    public static final String PARAM_EXPERIENCE_WORKSPACE_DIR = "OPENCODE_EXPERIENCE_WORKSPACE_DIR";
    private static final String UNCONFIGURED = "UNCONFIGURED";
    private static final String WORKSPACE_NAME = "体验工作区";
    private static final int OPEN_CONFIGURATION_RETRY_LIMIT = 3;

    private final ExperienceWorkspaceRepository experienceWorkspaceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CommonParameterValues commonParameterValues;
    private final ConfigurationManagementRepository configurationRepository;
    private final WorkspaceServerIdentity serverIdentity;
    private final GitWorkspaceService gitWorkspaceService;
    private final Clock clock;

    /** 生产构造路径使用本机 Git 命令与 UTC 时钟。 */
    @Autowired
    public ExperienceWorkspaceApplicationService(
            ExperienceWorkspaceRepository experienceWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceServerIdentity serverIdentity) {
        this(
                experienceWorkspaceRepository,
                workspaceRepository,
                commonParameterValues,
                configurationRepository,
                serverIdentity,
                new GitWorkspaceService(),
                Clock.systemUTC());
    }

    /** 测试构造路径允许固定 Git 探测和时间。 */
    ExperienceWorkspaceApplicationService(
            ExperienceWorkspaceRepository experienceWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceServerIdentity serverIdentity,
            GitWorkspaceService gitWorkspaceService,
            Clock clock) {
        this.experienceWorkspaceRepository = Objects.requireNonNull(
                experienceWorkspaceRepository, "experienceWorkspaceRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.configurationRepository = Objects.requireNonNull(
                configurationRepository, "configurationRepository must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 打开当前后端服务器的共享体验目录；路径和服务器身份只来自服务端事实，不接受客户端覆盖。
     */
    public Workspace open(UserId userId, String assignedLinuxServerId, String traceId) {
        if (assignedLinuxServerId == null
                || !serverIdentity.linuxServerId().equals(assignedLinuxServerId.trim())) {
            throw unavailable("PROCESS_SERVER_MISMATCH");
        }
        return open(userId, traceId);
    }

    /** 测试及目标端内部入口；服务器身份已由调用方或当前服务固定。 */
    public synchronized Workspace open(UserId userId, String traceId) {
        String linuxServerId = serverIdentity.linuxServerId();
        for (int attempt = 0; attempt < OPEN_CONFIGURATION_RETRY_LIMIT; attempt++) {
            requireEligibleUser(userId);
            ExperienceConfiguration configuration = requireCurrentConfiguration(true);
            WorkspaceId workspaceId = stableWorkspaceId(linuxServerId, configuration.realRoot());
            var existing = experienceWorkspaceRepository.findCurrentByLinuxServerId(linuxServerId);
            if (existing.isEmpty()
                    || !existing.orElseThrow().workspaceId().equals(workspaceId)
                    || !existing.orElseThrow().configuredParameterValue().equals(configuration.rawValue())) {
                Instant now = clock.instant();
                Workspace workspace = new Workspace(
                        workspaceId,
                        WORKSPACE_NAME,
                        configuration.realRoot().toString(),
                        WorkspaceStatus.ACTIVE,
                        now,
                        now,
                        linuxServerId,
                        traceId);
                if (!experienceWorkspaceRepository.registerCurrentIfUnchanged(
                        workspace, configuration.rawValue(), traceId, existing)) {
                    // 同机另一 Java 实例已经切换绑定；必须从通用参数重新开始，禁止迟到覆盖。
                    continue;
                }
            }
            // 参数可能在目录探测或数据库登记期间被管理员改写；旧请求不得迟到覆盖新绑定。
            ExperienceConfiguration verified = requireCurrentConfiguration(false);
            if (!configuration.equals(verified)) {
                continue;
            }
            try {
                return requireAccess(userId, workspaceId);
            } catch (PlatformException exception) {
                if (exception.errorCode() != ErrorCode.FORBIDDEN
                        || attempt == OPEN_CONFIGURATION_RETRY_LIMIT - 1) {
                    throw exception;
                }
                // 最终权威读取与上方复核之间仍可能改配，重试整套读取和登记。
            }
        }
        throw unavailable("CONFIGURATION_CHANGED");
    }

    /**
     * 实时校验当前体验绑定。参数换目录、用户加入应用、服务器变化或 Workspace 归档后立即拒绝旧访问。
     */
    @Override
    public Workspace requireAccess(UserId userId, WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (!isExperienceWorkspace(workspaceId)) {
            throw forbiddenExperience();
        }
        requireEligibleUser(userId);
        // 高频 ticket/RPC 仍实时读取资格、参数、绑定、目录与 Workspace；Git 根探测只在 open 执行，
        // 避免每个文件分片都派生一个 git 子进程。配置或真实路径变化仍会由下方事实比对立即拒绝。
        ExperienceConfiguration configuration = requireCurrentConfiguration(false);
        String linuxServerId = serverIdentity.linuxServerId();
        ExperienceWorkspaceBinding binding = experienceWorkspaceRepository
                .findCurrentByLinuxServerId(linuxServerId)
                .filter(candidate -> candidate.workspaceId().equals(workspaceId))
                .orElseThrow(this::forbiddenExperience);
        if (!binding.configuredParameterValue().equals(configuration.rawValue())) {
            throw forbiddenExperience();
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(candidate -> candidate.status() == WorkspaceStatus.ACTIVE)
                .filter(candidate -> linuxServerId.equals(candidate.linuxServerId()))
                .filter(candidate -> sameRealRoot(candidate.rootPath(), configuration.realRoot()))
                .orElseThrow(this::forbiddenExperience);
        return workspace.withRootPath(configuration.realRoot().toString());
    }

    private void requireEligibleUser(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (configurationRepository.hasEnabledApplicationMembership(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "已有所属应用，不能使用平台体验工作区");
        }
    }

    private ExperienceConfiguration requireCurrentConfiguration(boolean verifyGitRoot) {
        ParameterPlatform platform = ParameterPlatform.current();
        CommonParameter parameter = commonParameterValues.raw(PARAM_EXPERIENCE_WORKSPACE_DIR, platform)
                .orElseThrow(() -> unavailable("UNCONFIGURED"));
        String rawValue = parameter.parameterValue();
        String resolvedValue = commonParameterValues.resolvedValue(PARAM_EXPERIENCE_WORKSPACE_DIR, platform)
                .orElseThrow(() -> unavailable("UNCONFIGURED"));
        if (UNCONFIGURED.equalsIgnoreCase(rawValue.trim())
                || UNCONFIGURED.equalsIgnoreCase(resolvedValue.trim())) {
            throw unavailable("UNCONFIGURED");
        }
        Path realRoot;
        try {
            Path configuredRoot = Path.of(resolvedValue.trim());
            if (!configuredRoot.isAbsolute()
                    || !Files.isDirectory(configuredRoot)
                    || !Files.isReadable(configuredRoot)
                    || !Files.isWritable(configuredRoot)) {
                throw unavailable("DIRECTORY_UNAVAILABLE");
            }
            realRoot = configuredRoot.toRealPath();
        } catch (InvalidPathException | IOException | SecurityException exception) {
            throw unavailable("DIRECTORY_UNAVAILABLE");
        }
        // Git 执行器通常会记录 -C 参数；体验目录属于管理员配置，探测日志必须隐藏物理路径。
        if (verifyGitRoot) {
            try (GitCommandExecutor.LogRedaction ignored =
                    GitCommandExecutor.redactSensitiveArguments(List.of(realRoot.toString()))) {
                if (!gitWorkspaceService.isGitWorkTreeRoot(realRoot)) {
                    throw unavailable("NOT_GIT_WORK_TREE");
                }
            }
        }
        return new ExperienceConfiguration(rawValue, realRoot);
    }

    private boolean sameRealRoot(String persistedRoot, Path expectedRoot) {
        try {
            return Path.of(persistedRoot).toRealPath().equals(expectedRoot);
        } catch (InvalidPathException | IOException | SecurityException exception) {
            return false;
        }
    }

    private WorkspaceId stableWorkspaceId(String linuxServerId, Path realRoot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((linuxServerId + "\0" + realRoot)
                    .getBytes(StandardCharsets.UTF_8));
            return new WorkspaceId("wrk_exp_" + HexFormat.of().formatHex(hash, 0, 20));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private PlatformException unavailable(String reason) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "体验工作区尚未就绪，请联系管理员",
                Map.of("reason", reason));
    }

    private PlatformException forbiddenExperience() {
        return new PlatformException(ErrorCode.FORBIDDEN, "体验工作区已失效，请重新进入");
    }

    private record ExperienceConfiguration(String rawValue, Path realRoot) {
    }
}
