package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunLeaseLifecycle;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 在根 Run 启动时固定其实际使用的应用自动化引用代次，并在终态后释放生命周期租约。
 *
 * <p>本服务不读取或改写提示词；OpenCode 仍只通过工作树 JSONC 发现引用。
 */
@Service
public class ApplicationAutomationReferenceRunLeaseService
        implements AutomationReferenceRunLeaseLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAutomationReferenceRunLeaseService.class);
    private static final String OPENCODE_CONFIG_PATH = "opencode.jsonc";

    private final ConfigurationManagementRepository configurationRepository;
    private final ApplicationAutomationReferenceRepository automationRepository;
    private final AutomationWorkspaceReferenceCatalogService catalogService;
    private final AgentConfigApplicationService agentConfigService;
    private final PersonalAgentConfigRuntimeReloader runtimeReloader;
    private final AutomationReferenceWorkspaceJsoncReconciler jsoncReconciler;
    private final Clock clock;

    @Autowired
    public ApplicationAutomationReferenceRunLeaseService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            AutomationWorkspaceReferenceCatalogService catalogService,
            AgentConfigApplicationService agentConfigService,
            PersonalAgentConfigRuntimeReloader runtimeReloader,
            ObjectMapper objectMapper,
            Clock clock) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.automationRepository = Objects.requireNonNull(automationRepository);
        this.catalogService = Objects.requireNonNull(catalogService);
        this.agentConfigService = Objects.requireNonNull(agentConfigService);
        this.runtimeReloader = Objects.requireNonNull(runtimeReloader);
        this.jsoncReconciler = new AutomationReferenceWorkspaceJsoncReconciler(
                Objects.requireNonNull(objectMapper));
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AutomationReferenceRunPreparation prepare(Workspace workspace, UserId userId, String traceId) {
        ApplicationId appId = catalogService.resolveHostApplication(workspace.workspaceId()).orElse(null);
        if (appId == null) {
            return AutomationReferenceRunPreparation.empty();
        }
        if (!configurationRepository.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户已无应用自动化引用访问权限");
        }
        if (workspace.linuxServerId() == null || workspace.linuxServerId().isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前工作区尚未绑定自动化引用副本服务器");
        }
        LinuxServerId linuxServerId = new LinuxServerId(workspace.linuxServerId());
        List<ApplicationAutomationReferenceRunLease> leases = new ArrayList<>();
        List<AutomationReferenceWorkspaceJsoncReconciler.Patch> patches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (CodeRepository repository : automationRepositories(appId)) {
            ApplicationAutomationReferenceState state = automationRepository
                    .findState(appId, repository.repositoryId())
                    .orElse(null);
            if (state == null || state.activeGeneration() == null) {
                continue;
            }
            ApplicationAutomationReferenceGeneration generation = automationRepository
                    .findGeneration(appId, repository.repositoryId(), state.activeGeneration())
                    .orElse(null);
            if (generation == null
                    || generation.status() != AutomationReferenceGenerationStatus.READY
                    || !hasReadyReplica(generation, linuxServerId)) {
                warnings.add(repository.name() + "：当前服务器共享只读副本尚未就绪，本次运行已跳过");
                continue;
            }
            leases.add(new ApplicationAutomationReferenceRunLease(
                    appId, repository.repositoryId(), generation.generation(), linuxServerId));
            patches.add(new AutomationReferenceWorkspaceJsoncReconciler.Patch(
                    appId.value(),
                    repository.repositoryId().value(),
                    generation.generation(),
                    AutomationReferencePathPolicy.alias(repository),
                    AutomationReferencePathPolicy.logicalPath(appId, repository, generation),
                    AutomationReferencePathPolicy.directoryName(repository, generation.directoryPath()),
                    generation.description()));
        }
        String current = readWorkspaceConfiguration(workspace);
        String reconciled = jsoncReconciler.reconcile(current, appId.value(), patches);
        boolean changed = !reconciled.equals(current);
        if (changed) {
            agentConfigService.writeWorkspaceAgentFile(
                    workspace.workspaceId().value(), OPENCODE_CONFIG_PATH, reconciled, null);
            runtimeReloader.reloadWorkspaceConfiguration(userId, linuxServerId.value(), traceId);
        }
        if (!warnings.isEmpty()) {
            LOGGER.warn(
                    "Automation references partially unavailable, appId={}, workspaceId={}, skippedCount={}, traceId={}",
                    appId.value(), workspace.workspaceId().value(), warnings.size(), traceId);
        }
        return new AutomationReferenceRunPreparation(leases, warnings, changed);
    }

    @Override
    public void acquire(Run run, AutomationReferenceRunPreparation preparation, String traceId) {
        AutomationReferenceRunPreparation safe = preparation == null
                ? AutomationReferenceRunPreparation.empty()
                : preparation;
        automationRepository.replaceRunLeases(run.runId(), safe.leases(), clock.instant());
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

    private String readWorkspaceConfiguration(Workspace workspace) {
        try {
            return agentConfigService.readWorkspaceAgentFile(
                    workspace.workspaceId().value(), OPENCODE_CONFIG_PATH, null).content();
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                return "";
            }
            throw exception;
        }
    }
}
