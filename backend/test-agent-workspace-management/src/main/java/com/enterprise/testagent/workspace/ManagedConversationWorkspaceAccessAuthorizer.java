package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 托管运行 Workspace 的会话权限校验器。
 *
 * <p>应用版本和个人 Workspace 都回溯到应用；所有角色（包括 SUPER_ADMIN）沿用现有托管工作区规则，
 * 必须是已启用应用的有效成员。找不到托管映射时视为历史非托管 Workspace，继续由 Session owner 约束。
 */
@Service
public class ManagedConversationWorkspaceAccessAuthorizer implements ConversationWorkspaceAccessAuthorizer {

    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ConfigurationManagementRepository configurationRepository;

    public ManagedConversationWorkspaceAccessAuthorizer(
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ConfigurationManagementRepository configurationRepository) {
        this.managedWorkspaceRepository = Objects.requireNonNull(
                managedWorkspaceRepository,
                "managedWorkspaceRepository must not be null");
        this.configurationRepository = Objects.requireNonNull(
                configurationRepository,
                "configurationRepository must not be null");
    }

    @Override
    public void requireAccess(UserId userId, WorkspaceId workspaceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Optional<ApplicationWorkspaceVersion> version =
                managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId);
        ApplicationId appId;
        if (version.isPresent()) {
            appId = version.get().appId();
        } else {
            Optional<PersonalWorkspace> personal =
                    managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
            if (personal.isEmpty()) {
                return;
            }
            if (!personal.get().userId().equals(userId)) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "无权使用其他用户的个人工作区创建会话运行上下文",
                        Map.of("workspaceId", workspaceId.value()));
            }
            appId = personal.get().appId();
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
    }
}
