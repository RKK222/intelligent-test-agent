package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 托管运行 Workspace 的会话权限校验器。
 *
 * <p>应用版本和个人 Workspace 都回溯到应用；所有角色（包括 SUPER_ADMIN）沿用现有托管工作区规则，
 * 必须是已启用应用的有效成员。会话入口找不到托管映射时继续由 Session owner 约束；文件入口默认拒绝，
 * 仅允许 SUPER_ADMIN 的服务器工作区兼容入口显式放行。
 */
@Service
public class ManagedConversationWorkspaceAccessAuthorizer implements ConversationWorkspaceAccessAuthorizer {

    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ConfigurationManagementRepository configurationRepository;
    private final AppSourceRepository appSourceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer;
    private final Clock clock;
    private LocalClientWorkspaceRepository localClientWorkspaceRepository;

    public ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository) {
        this(managedWorkspaceRepository, configurationRepository, null, null, null, Clock.systemUTC());
    }

    /** 生产构造器同时接入 generation 专属 app-source Runtime Workspace 反查。 */
    @Autowired
    public ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository,
            AppSourceRepository appSourceRepository,
            WorkspaceRepository workspaceRepository,
            ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer) {
        this(
                managedWorkspaceRepository,
                configurationRepository,
                appSourceRepository,
                workspaceRepository,
                experienceWorkspaceAccessAuthorizer,
                Clock.systemUTC());
    }

    /** 兼容不需要体验策略的手工构造路径。 */
    public ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository,
            AppSourceRepository appSourceRepository,
            WorkspaceRepository workspaceRepository) {
        this(managedWorkspaceRepository, configurationRepository, appSourceRepository, workspaceRepository, null, Clock.systemUTC());
    }

    /** 测试构造器允许固定到期判断时间。 */
    ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository,
            AppSourceRepository appSourceRepository,
            Clock clock) {
        this(managedWorkspaceRepository, configurationRepository, appSourceRepository, null, null, clock);
    }

    /** 测试构造器可同时验证 Workspace 行与 replica server 的交叉绑定。 */
    ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository,
            AppSourceRepository appSourceRepository,
            WorkspaceRepository workspaceRepository,
            Clock clock) {
        this(managedWorkspaceRepository, configurationRepository, appSourceRepository, workspaceRepository, null, clock);
    }

    /** 测试构造器可同时验证体验、app-source 与 Workspace 服务器交叉绑定。 */
    ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository,
            AppSourceRepository appSourceRepository,
            WorkspaceRepository workspaceRepository,
            ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer,
            Clock clock) {
        this.managedWorkspaceRepository = Objects.requireNonNull(
                managedWorkspaceRepository,
                "managedWorkspaceRepository must not be null");
        this.configurationRepository = Objects.requireNonNull(
                configurationRepository,
                "configurationRepository must not be null");
        this.appSourceRepository = appSourceRepository;
        this.workspaceRepository = workspaceRepository;
        this.experienceWorkspaceAccessAuthorizer = experienceWorkspaceAccessAuthorizer;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void requireAccess(UserId userId, WorkspaceId workspaceId) {
        requireManagedAccess(userId, workspaceId, true);
    }

    @Override
    public void requireFileAccess(UserId userId, WorkspaceId workspaceId, boolean allowUnmanagedWorkspace) {
        requireClassifiedFileAccess(userId, workspaceId, allowUnmanagedWorkspace);
    }

    @Override
    public FileWorkspaceKind requireClassifiedFileAccess(
            UserId userId,
            WorkspaceId workspaceId,
            boolean allowUnmanagedWorkspace) {
        return requireManagedAccess(userId, workspaceId, allowUnmanagedWorkspace);
    }

    private FileWorkspaceKind requireManagedAccess(
            UserId userId,
            WorkspaceId workspaceId,
            boolean allowUnmanagedWorkspace) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (experienceWorkspaceAccessAuthorizer != null
                && experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(workspaceId)) {
            // 历史体验 ID 也必须进入实时策略；策略失败时不能降级为普通非托管目录。
            experienceWorkspaceAccessAuthorizer.requireAccess(userId, workspaceId);
            return FileWorkspaceKind.EXPERIENCE;
        }
        if (localClientWorkspaceRepository != null) {
            var localBinding = localClientWorkspaceRepository.findByWorkspaceId(workspaceId).orElse(null);
            if (localBinding != null) {
                if (!localBinding.userId().equals(userId)) {
                    throw new PlatformException(
                            ErrorCode.FORBIDDEN,
                            "无权访问其他用户的本地工作区",
                            Map.of("workspaceId", workspaceId.value()));
                }
                return FileWorkspaceKind.STANDARD;
            }
        }
        Optional<ApplicationWorkspaceVersion> version =
                managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId);
        ApplicationId appId;
        if (version.isPresent()) {
            appId = version.get().appId();
        } else {
            Optional<ApplicationWorkspaceVersionReplica> replica =
                    managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(workspaceId);
            if (replica.isPresent()) {
                // 运行节点上的版本副本同样属于托管应用，必须先回溯版本再执行成员校验，不能降级为历史工作区。
                appId = managedWorkspaceRepository.findVersion(replica.get().versionId())
                        .map(ApplicationWorkspaceVersion::appId)
                        .orElseThrow(() -> new PlatformException(
                                ErrorCode.FORBIDDEN,
                                "应用版本副本缺少有效的托管版本映射",
                                Map.of("workspaceId", workspaceId.value())));
            } else {
                Optional<PersonalWorkspace> personal =
                        managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
                if (personal.isEmpty()) {
                    if (requireAppSourceAccessIfMapped(userId, workspaceId)) {
                        return FileWorkspaceKind.APP_SOURCE;
                    }
                    if (allowUnmanagedWorkspace) {
                        return FileWorkspaceKind.STANDARD;
                    }
                    throw new PlatformException(
                            ErrorCode.FORBIDDEN,
                            "普通用户无权访问非托管工作区文件",
                            Map.of("workspaceId", workspaceId.value()));
                }
                if (!personal.get().userId().equals(userId)) {
                    throw new PlatformException(
                            ErrorCode.FORBIDDEN,
                            "无权使用其他用户的个人工作区创建会话运行上下文",
                            Map.of("workspaceId", workspaceId.value()));
                }
                appId = personal.get().appId();
            }
        }
        ApplicationDefinition application = configurationRepository.findApplication(appId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "应用不存在",
                        Map.of("appId", appId.value())));
        if (!application.enabled()) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "应用未启用，不能创建会话运行上下文",
                    Map.of("appId", appId.value(), "appName", application.appName()));
        }
        if (!configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "当前用户已不是应用有效成员，不能创建会话运行上下文",
                    Map.of("appId", appId.value(), "appName", application.appName()));
        }
        return FileWorkspaceKind.STANDARD;
    }

    /**
     * generation/server Workspace 不进入 managed-workspace 表，必须按 replica→snapshot→当前应用关联实时回溯。
     * 任一状态、到期、个人 owner 或成员校验失败都失败关闭，不能降级为历史非托管 Workspace。
     */
    private boolean requireAppSourceAccessIfMapped(UserId userId, WorkspaceId workspaceId) {
        if (appSourceRepository == null) {
            return false;
        }
        AppSourceReplica replica = appSourceRepository.findReplicaByRuntimeWorkspaceId(workspaceId.value()).orElse(null);
        if (replica == null) {
            return false;
        }
        if (replica.status() != AppSourceReplicaStatus.READY) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码工作区副本未就绪");
        }
        if (workspaceRepository != null) {
            Workspace workspace = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "应用源码工作区记录不存在"));
            if (!Objects.equals(workspace.linuxServerId(), replica.linuxServerId().value())) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码工作区服务器绑定不一致");
            }
        }
        boolean currentGeneration = appSourceRepository.findSlot(replica.repositoryId())
                .map(slot -> Objects.equals(slot.activeGeneration(), replica.generation()))
                .orElse(false);
        if (!currentGeneration) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码工作区已不是当前 generation");
        }
        AppSourceSnapshot snapshot = appSourceRepository
                .findSnapshot(replica.repositoryId(), replica.generation())
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "应用源码工作区缺少快照映射"));
        if (snapshot.status() != AppSourceSnapshotStatus.ACTIVE
                || !snapshot.expiresAt().isAfter(clock.instant())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码快照已失效");
        }
        if (snapshot.purpose() == AppSourcePurpose.PERSONAL
                && !snapshot.ownerUserId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "个人应用源码工作区只允许拥有者访问");
        }
        boolean activeLinkedMember = configurationRepository.findApplicationsByRepository(replica.repositoryId()).stream()
                .filter(ApplicationDefinition::enabled)
                .filter(application -> configurationRepository.findRepositoriesByApplication(application.appId()).stream()
                        .anyMatch(repository -> repository.repositoryId().equals(replica.repositoryId())
                                && CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value()
                                        .equals(repository.repositoryType())))
                .anyMatch(application -> configurationRepository.isActiveMember(application.appId(), userId));
        if (!activeLinkedMember) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户已不是应用源码关联应用的有效成员");
        }
        return true;
    }

    /** 本地工作区归属通过独立绑定表校验，避免把它误判为可放行的历史非托管工作区。 */
    @Autowired(required = false)
    void configureLocalClientWorkspaceRepository(
            LocalClientWorkspaceRepository localClientWorkspaceRepository) {
        this.localClientWorkspaceRepository = Objects.requireNonNull(
                localClientWorkspaceRepository, "localClientWorkspaceRepository must not be null");
    }
}
