package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 按主工作空间所属应用实时校验自动化代码库共享只读代次。 */
@Service
public class AutomationWorkspaceReferenceCatalogService implements AutomationWorkspaceReferenceCatalog {

    private final ConfigurationManagementRepository configurationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final ApplicationAutomationReferenceService automationReferenceService;

    public AutomationWorkspaceReferenceCatalogService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            ApplicationAutomationReferenceService automationReferenceService) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository);
        this.automationReferenceService = Objects.requireNonNull(automationReferenceService);
    }

    @Override
    public Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation) {
        ApplicationId hostAppId = resolveHostApplication(hostWorkspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前工作空间不支持自动化引用"));
        if (!hostAppId.equals(appId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "自动化引用应用与当前工作空间不匹配");
        }
        requireApplicationMember(appId, userId);
        CodeRepository repository = configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(candidate -> candidate.repositoryId().equals(repositoryId))
                .filter(this::isAutomationRepository)
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "自动化代码库未关联当前应用"));
        ApplicationAutomationReferenceGeneration configuration = automationReferenceService
                .requireGeneration(appId, repositoryId, generation);
        Path selectedDirectory = automationReferenceService.requireReadyLocalDirectory(
                appId, repositoryId, generation);
        return new Reference(
                appId,
                repositoryId,
                generation,
                repository.name(),
                repository.name(),
                configuration.branch(),
                configuration.targetCommitHash(),
                selectedDirectory.toString(),
                configuration.directoryPath(),
                automationReferenceService.logicalConfigurationPath(appId, repository, configuration),
                configuration.description(),
                automationReferenceService.isActiveGeneration(appId, repositoryId, generation));
    }

    Optional<ApplicationId> resolveHostApplication(WorkspaceId workspaceId) {
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository
                .findPersonalWorkspaceByRuntimeWorkspace(workspaceId);
        if (personal.isPresent()) {
            ApplicationWorkspaceVersion version = managedWorkspaceRepository
                    .findVersion(personal.get().versionId()).orElse(null);
            return isAutomationVersion(version) ? Optional.empty() : Optional.of(personal.get().appId());
        }
        Optional<ApplicationWorkspaceVersion> direct = managedWorkspaceRepository
                .findVersionByRuntimeWorkspace(workspaceId);
        if (direct.isPresent()) {
            return isAutomationVersion(direct.get()) ? Optional.empty() : Optional.of(direct.get().appId());
        }
        return managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(workspaceId)
                .flatMap(replica -> managedWorkspaceRepository.findVersion(replica.versionId()))
                .filter(version -> !isAutomationVersion(version))
                .map(ApplicationWorkspaceVersion::appId);
    }

    private void requireApplicationMember(ApplicationId appId, UserId userId) {
        ApplicationDefinition application = configurationRepository.findApplication(appId)
                .filter(ApplicationDefinition::enabled)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "应用不可用"));
        if (userId == null || !configurationRepository.isActiveMember(application.appId(), userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无应用自动化代码库访问权限");
        }
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
}
