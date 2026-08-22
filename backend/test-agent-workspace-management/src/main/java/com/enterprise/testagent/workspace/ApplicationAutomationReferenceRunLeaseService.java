package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunLeaseLifecycle;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import java.time.Clock;
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

    private final ApplicationAutomationReferenceRepository automationRepository;
    private final ApplicationAutomationReferenceWorkspaceReconciliationService reconciliationService;
    private final PersonalAgentConfigRuntimeReloader runtimeReloader;
    private final Clock clock;

    public ApplicationAutomationReferenceRunLeaseService(
            ApplicationAutomationReferenceRepository automationRepository,
            ApplicationAutomationReferenceWorkspaceReconciliationService reconciliationService,
            PersonalAgentConfigRuntimeReloader runtimeReloader,
            Clock clock) {
        this.automationRepository = Objects.requireNonNull(automationRepository);
        this.reconciliationService = Objects.requireNonNull(reconciliationService);
        this.runtimeReloader = Objects.requireNonNull(runtimeReloader);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AutomationReferenceRunPreparation prepare(Workspace workspace, UserId userId, String traceId) {
        AutomationReferenceRunPreparation preparation = reconciliationService.reconcile(workspace, userId, traceId);
        if (preparation.configurationChanged()) {
            runtimeReloader.reloadWorkspaceConfiguration(userId, workspace.linuxServerId(), traceId);
        }
        return preparation;
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
}
