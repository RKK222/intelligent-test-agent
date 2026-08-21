package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunLeaseLifecycle;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 在根 Run 启动时固定其实际使用的应用自动化引用代次，并在终态后释放生命周期租约。
 *
 * <p>本服务不读取或改写提示词；OpenCode 仍只通过工作树 JSONC 发现引用。
 */
@Service
public class ApplicationAutomationReferenceRunLeaseService
        implements AutomationReferenceRunLeaseLifecycle {

    private final ConfigurationManagementRepository configurationRepository;
    private final ApplicationAutomationReferenceRepository automationRepository;
    private final AutomationWorkspaceReferenceCatalogService catalogService;
    private final Clock clock;

    public ApplicationAutomationReferenceRunLeaseService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            AutomationWorkspaceReferenceCatalogService catalogService,
            Clock clock) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.automationRepository = Objects.requireNonNull(automationRepository);
        this.catalogService = Objects.requireNonNull(catalogService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void acquire(Run run, Workspace workspace, UserId userId, String traceId) {
        ApplicationId appId = catalogService.resolveHostApplication(workspace.workspaceId()).orElse(null);
        if (appId == null) {
            automationRepository.replaceRunLeases(run.runId(), List.of(), clock.instant());
            return;
        }
        if (!configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户已无应用自动化引用访问权限");
        }
        if (workspace.linuxServerId() == null || workspace.linuxServerId().isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前工作区尚未绑定自动化引用副本服务器");
        }
        LinuxServerId linuxServerId = new LinuxServerId(workspace.linuxServerId());
        List<ApplicationAutomationReferenceRunLease> leases = new ArrayList<>();
        for (CodeRepository repository : automationRepositories(appId)) {
            ApplicationAutomationReferenceState state = automationRepository
                    .findState(appId, repository.repositoryId())
                    .orElse(null);
            if (state == null || state.activeGeneration() == null) {
                continue;
            }
            ApplicationAutomationReferenceGeneration generation = automationRepository
                    .findGeneration(appId, repository.repositoryId(), state.activeGeneration())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.CONFLICT, "自动化引用当前配置代次不存在，请联系管理员"));
            if (generation.status() != AutomationReferenceGenerationStatus.READY
                    || !hasReadyReplica(generation, linuxServerId)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "自动化引用共享副本尚未就绪，请稍后重试");
            }
            leases.add(new ApplicationAutomationReferenceRunLease(
                    appId, repository.repositoryId(), generation.generation(), linuxServerId));
        }
        automationRepository.replaceRunLeases(run.runId(), leases, clock.instant());
    }

    @Override
    public void release(RunId runId, String traceId) {
        automationRepository.deleteRunLeases(runId);
    }

    private List<CodeRepository> automationRepositories(ApplicationId appId) {
        return configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(repository -> CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value()
                        .equals(repository.repositoryType()))
                .toList();
    }

    private boolean hasReadyReplica(
            ApplicationAutomationReferenceGeneration generation,
            LinuxServerId linuxServerId) {
        return automationRepository.findReplicas(
                        generation.appId(), generation.repositoryId(), generation.generation()).stream()
                .anyMatch(replica -> replica.linuxServerId().equals(linuxServerId)
                        && replica.status() == ReferenceRepositoryReplicaStatus.READY
                        && generation.branch().equals(replica.currentBranch())
                        && generation.targetCommitHash().equals(replica.currentCommitHash()));
    }
}
