package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersion;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersionRepository;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 按主工作空间、用户和本服务器实时解析自动化代码库只读引用。 */
@Service
public class AutomationWorkspaceReferenceCatalogService implements AutomationWorkspaceReferenceCatalog {

    private final ConfigurationManagementRepository configurationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final AutomationWorkspaceActiveVersionRepository activeVersionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ManagedWorkspacePathResolver pathResolver;
    private final WorkspaceServerIdentity serverIdentity;

    public AutomationWorkspaceReferenceCatalogService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            AutomationWorkspaceActiveVersionRepository activeVersionRepository,
            WorkspaceRepository workspaceRepository,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository);
        this.activeVersionRepository = Objects.requireNonNull(activeVersionRepository);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.pathResolver = Objects.requireNonNull(pathResolver);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
    }

    @Override
    public Resolution resolveActive(UserId userId, WorkspaceId hostWorkspaceId) {
        Optional<ApplicationId> applicationId = resolveHostApplication(hostWorkspaceId);
        if (applicationId.isEmpty()) {
            return Resolution.empty();
        }
        ApplicationDefinition application = requireApplicationMember(applicationId.get(), userId);
        Map<String, CodeRepository> repositories = configurationRepository
                .findRepositoriesByApplication(application.appId())
                .stream()
                .collect(Collectors.toMap(repository -> repository.repositoryId().value(), Function.identity()));
        List<ApplicationWorkspace> templates = configurationRepository.findWorkspaces(application.appId()).stream()
                .filter(ApplicationWorkspace::enabled)
                .filter(template -> isAutomationRepository(repositories.get(template.repositoryId().value())))
                .sorted(Comparator.comparing(ApplicationWorkspace::workspaceName)
                        .thenComparing(template -> template.workspaceId().value()))
                .toList();
        if (templates.isEmpty()) {
            return new Resolution(application.appId(), 0, List.of(), List.of());
        }
        Map<String, AutomationWorkspaceActiveVersion> activeByTemplate = activeVersionRepository
                .findByApplicationWorkspaceIds(templates.stream().map(ApplicationWorkspace::workspaceId).toList())
                .stream()
                .collect(Collectors.toMap(
                        active -> active.applicationWorkspaceId().value(),
                        Function.identity()));
        Map<String, Long> duplicateNames = templates.stream()
                .collect(Collectors.groupingBy(ApplicationWorkspace::workspaceName, Collectors.counting()));
        List<Reference> references = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (ApplicationWorkspace template : templates) {
            AutomationWorkspaceActiveVersion active = activeByTemplate.get(template.workspaceId().value());
            if (active == null) {
                warnings.add(warning(template, "NO_ACTIVE_VERSION", "自动化代码库尚未激活版本"));
                continue;
            }
            ApplicationWorkspaceVersion version = managedWorkspaceRepository.findVersion(active.versionId()).orElse(null);
            if (!matches(template, version)) {
                warnings.add(warning(template, "INVALID_ACTIVE_VERSION", "自动化代码库激活版本无效"));
                continue;
            }
            CodeRepository repository = repositories.get(template.repositoryId().value());
            try {
                references.add(resolveReadyReference(
                        application.appId(),
                        template,
                        version,
                        repository,
                        duplicateNames.getOrDefault(template.workspaceName(), 0L) > 1));
            } catch (PlatformException exception) {
                warnings.add(warning(template, exception.errorCode().name(), "自动化代码库当前服务器副本不可用"));
            }
        }
        return new Resolution(application.appId(), templates.size(), references, warnings);
    }

    @Override
    public Reference resolveVersion(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationWorkspaceId applicationWorkspaceId,
            ApplicationWorkspaceVersionId versionId) {
        ApplicationId appId = resolveHostApplication(hostWorkspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前工作空间不支持自动化代码库引用"));
        ApplicationDefinition application = requireApplicationMember(appId, userId);
        ApplicationWorkspace template = configurationRepository.findWorkspace(applicationWorkspaceId)
                .filter(ApplicationWorkspace::enabled)
                .filter(value -> value.appId().equals(application.appId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "自动化引用定位器与当前应用不匹配"));
        CodeRepository repository = configurationRepository.findRepository(template.repositoryId())
                .filter(this::isAutomationRepository)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "自动化引用版本库类型无效"));
        ApplicationWorkspaceVersion version = managedWorkspaceRepository.findVersion(versionId)
                .filter(value -> matches(template, value))
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "自动化引用版本与配置不匹配"));
        return resolveReadyReference(application.appId(), template, version, repository, false);
    }

    private Reference resolveReadyReference(
            ApplicationId appId,
            ApplicationWorkspace template,
            ApplicationWorkspaceVersion version,
            CodeRepository repository,
            boolean duplicateName) {
        if (version.status() != ManagedWorkspaceStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化代码库版本不可用");
        }
        ApplicationWorkspaceVersionReplica replica = managedWorkspaceRepository
                .findVersionReplica(version.versionId(), serverIdentity.linuxServerId())
                .filter(value -> value.syncStatus() == WorkspaceReplicaSyncStatus.READY)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "自动化代码库副本未就绪"));
        if (version.targetCommitHash() != null
                && !version.targetCommitHash().equals(replica.currentCommitHash())) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化代码库副本提交不匹配");
        }
        Workspace runtimeWorkspace = workspaceRepository.findById(replica.runtimeWorkspaceId())
                .filter(value -> value.status() == WorkspaceStatus.ACTIVE)
                .filter(value -> serverIdentity.linuxServerId().equals(value.linuxServerId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "自动化代码库运行工作空间不可用"));
        Path resolvedRoot = pathResolver.resolve(runtimeWorkspace.rootPath()).toAbsolutePath().normalize();
        String displayName = duplicateName
                ? template.workspaceName() + "（" + repository.name() + " / " + version.branch() + "）"
                : template.workspaceName();
        return new Reference(
                appId,
                template.workspaceId(),
                version.versionId(),
                template.workspaceName(),
                displayName,
                repository.name(),
                version.version(),
                version.branch(),
                version.targetCommitHash(),
                resolvedRoot.toString());
    }

    private Optional<ApplicationId> resolveHostApplication(WorkspaceId workspaceId) {
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
        if (personal.isPresent()) {
            ApplicationWorkspaceVersion version = managedWorkspaceRepository.findVersion(personal.get().versionId()).orElse(null);
            return isAutomationVersion(version) ? Optional.empty() : Optional.of(personal.get().appId());
        }
        Optional<ApplicationWorkspaceVersion> direct = managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId);
        if (direct.isPresent()) {
            return isAutomationVersion(direct.get()) ? Optional.empty() : Optional.of(direct.get().appId());
        }
        return managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(workspaceId)
                .flatMap(replica -> managedWorkspaceRepository.findVersion(replica.versionId()))
                .filter(version -> !isAutomationVersion(version))
                .map(ApplicationWorkspaceVersion::appId);
    }

    private ApplicationDefinition requireApplicationMember(ApplicationId appId, UserId userId) {
        ApplicationDefinition application = configurationRepository.findApplication(appId)
                .filter(ApplicationDefinition::enabled)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "应用不可用"));
        if (userId == null || !configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无应用自动化代码库访问权限");
        }
        return application;
    }

    private boolean isAutomationVersion(ApplicationWorkspaceVersion version) {
        return version != null
                && configurationRepository.findRepository(version.repositoryId())
                        .map(this::isAutomationRepository)
                        .orElse(false);
    }

    private boolean isAutomationRepository(CodeRepository repository) {
        return repository != null
                && CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value().equals(repository.repositoryType());
    }

    private boolean matches(ApplicationWorkspace template, ApplicationWorkspaceVersion version) {
        return version != null
                && version.applicationWorkspaceId().equals(template.workspaceId())
                && version.appId().equals(template.appId())
                && version.repositoryId().equals(template.repositoryId());
    }

    private Warning warning(ApplicationWorkspace template, String code, String message) {
        return new Warning(template.workspaceId(), template.workspaceName(), code, message);
    }
}
