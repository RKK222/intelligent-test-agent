package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRuntimeImpactResolver;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 依据公共配置 Git 提交差异判断是否命中 OpenCode 的进程级 Tool 模块缓存。
 *
 * <p>该组件只依赖通用参数和只读 Git 命令，不能依赖发布协调器或工作区应用服务，
 * 避免运行时发布服务与工作区写链路形成 Bean 循环依赖。</p>
 */
@Service
public class GitPublicAgentConfigRuntimeImpactResolver implements PublicAgentConfigRuntimeImpactResolver {

    private static final String PARAM_PUBLIC_CONFIG_GIT_ROOT = "OPENCODE_PUBLIC_CONFIG_GIT_ROOT";

    private final CommonParameterValues commonParameterValues;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final WorkspaceServerIdentity serverIdentity;
    private final ManagedWorkspacePathResolver pathResolver;
    private final GitWorkspaceService gitWorkspaceService;

    @Autowired
    public GitPublicAgentConfigRuntimeImpactResolver(
            CommonParameterValues commonParameterValues,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver) {
        this(
                commonParameterValues,
                managedWorkspaceRepository,
                serverIdentity,
                pathResolver,
                new GitWorkspaceService());
    }

    GitPublicAgentConfigRuntimeImpactResolver(
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService) {
        this(commonParameterValues, null, null, null, gitWorkspaceService);
    }

    GitPublicAgentConfigRuntimeImpactResolver(
            CommonParameterValues commonParameterValues,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver,
            GitWorkspaceService gitWorkspaceService) {
        this.commonParameterValues = Objects.requireNonNull(
                commonParameterValues, "commonParameterValues must not be null");
        this.managedWorkspaceRepository = managedWorkspaceRepository;
        this.serverIdentity = serverIdentity;
        this.pathResolver = pathResolver;
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
    }

    @Override
    public boolean requiresProcessRestart(
            AgentConfigRolloutScope scope,
            String scopeKey,
            String previousCommitHash,
            String commitHash) {
        if ((scope != AgentConfigRolloutScope.PUBLIC && scope != AgentConfigRolloutScope.APPLICATION)
                || previousCommitHash == null
                || previousCommitHash.isBlank()
                || commitHash == null
                || commitHash.isBlank()
                || previousCommitHash.equals(commitHash)) {
            return false;
        }
        RuntimeGitScope gitScope = scope == AgentConfigRolloutScope.PUBLIC
                ? publicGitScope()
                : applicationGitScope(scopeKey);
        if (!gitWorkspaceService.isGitRepository(gitScope.gitRoot())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Agent 配置仓库在" + gitScope.gitRoot() + "目录中未初始化。",
                    Map.of("gitRootPath", gitScope.gitRoot().toString()));
        }
        String changes = gitWorkspaceService.diffNameStatusBetweenTrees(
                gitScope.gitRoot(), previousCommitHash, commitHash);
        return scope == AgentConfigRolloutScope.PUBLIC
                ? hasCommittedToolModuleChange(changes)
                : hasCommittedApplicationToolModuleChange(changes, gitScope.workspacePrefix());
    }

    private RuntimeGitScope publicGitScope() {
        Path gitRoot = commonParameterValues.resolvedValue(PARAM_PUBLIC_CONFIG_GIT_ROOT)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .map(Path::normalize)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + PARAM_PUBLIC_CONFIG_GIT_ROOT,
                        Map.of("parameter", PARAM_PUBLIC_CONFIG_GIT_ROOT)));
        return new RuntimeGitScope(gitRoot, "");
    }

    private RuntimeGitScope applicationGitScope(String scopeKey) {
        if (managedWorkspaceRepository == null || serverIdentity == null || pathResolver == null) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "应用 Tool 运行态影响解析服务未完整初始化");
        }
        if (scopeKey == null || scopeKey.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "应用 Agent 发布缺少版本范围");
        }
        ApplicationWorkspaceVersionReplica replica = managedWorkspaceRepository.findVersionReplica(
                        new ApplicationWorkspaceVersionId(scopeKey.trim()),
                        serverIdentity.linuxServerId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "当前服务器尚未准备应用版本副本",
                        Map.of("versionId", scopeKey.trim(), "linuxServerId", serverIdentity.linuxServerId())));
        Path gitRoot = pathResolver.resolve(replica.repoRootPath()).toAbsolutePath().normalize();
        Path workspaceRoot = pathResolver.resolve(replica.workspaceRootPath()).toAbsolutePath().normalize();
        if (!workspaceRoot.startsWith(gitRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用工作空间目录超出版本仓库边界",
                    Map.of("versionId", scopeKey.trim()));
        }
        String prefix = gitRoot.relativize(workspaceRoot).toString().replace('\\', '/');
        return new RuntimeGitScope(gitRoot, prefix);
    }

    static boolean hasWorkingTreeToolModuleChange(String porcelain) {
        for (String line : porcelain.split("\\R")) {
            if (line.length() < 4) {
                continue;
            }
            for (String path : line.substring(3).split(" -> ")) {
                if (isPublicToolModulePath(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasCommittedToolModuleChange(String nameStatus) {
        for (String line : nameStatus.split("\\R")) {
            String[] fields = line.split("\\t");
            for (int index = 1; index < fields.length; index += 1) {
                if (isPublicToolModulePath(fields[index])) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasCommittedApplicationToolModuleChange(String nameStatus, String workspacePrefix) {
        String prefix = workspacePrefix == null ? "" : normalizePath(workspacePrefix);
        for (String line : nameStatus.split("\\R")) {
            String[] fields = line.split("\\t");
            for (int index = 1; index < fields.length; index += 1) {
                String path = normalizePath(fields[index]);
                if (!prefix.isBlank()) {
                    if (!path.startsWith(prefix + "/")) {
                        continue;
                    }
                    path = path.substring(prefix.length() + 1);
                }
                if (isApplicationToolModulePath(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublicToolModulePath(String value) {
        if (value == null) {
            return false;
        }
        String path = normalizePath(value);
        return path.matches("^opencode/tools?/.+\\.(js|ts)$");
    }

    static boolean isApplicationToolModulePath(String value) {
        return normalizePath(value).matches("^\\.opencode/tools?/.+\\.(js|ts)$");
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private record RuntimeGitScope(Path gitRoot, String workspacePrefix) {
    }
}
