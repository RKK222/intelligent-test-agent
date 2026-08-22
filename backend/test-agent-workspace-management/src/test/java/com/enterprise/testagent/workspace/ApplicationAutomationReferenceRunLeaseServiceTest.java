package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationAutomationReferenceRunLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T03:00:00Z");
    private static final UserId USER_ID = new UserId("usr_demo");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    private ApplicationAutomationReferenceRepository automationRepository;
    private ApplicationAutomationReferenceWorkspaceReconciliationService reconciliationService;
    private PersonalAgentConfigRuntimeReloader runtimeReloader;
    private ApplicationAutomationReferenceRunLeaseService service;

    @BeforeEach
    void setUp() {
        automationRepository = mock(ApplicationAutomationReferenceRepository.class);
        reconciliationService = mock(ApplicationAutomationReferenceWorkspaceReconciliationService.class);
        runtimeReloader = mock(PersonalAgentConfigRuntimeReloader.class);
        service = new ApplicationAutomationReferenceRunLeaseService(
                automationRepository,
                reconciliationService,
                runtimeReloader,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void prepareDelegatesReconciliationAndReloadsOnlyWhenJsoncChanged() {
        AutomationReferenceRunPreparation preparation = new AutomationReferenceRunPreparation(
                List.of(lease()), List.of(), true);
        when(reconciliationService.reconcile(workspace(), USER_ID, "trace-run")).thenReturn(preparation);

        assertThat(service.prepare(workspace(), USER_ID, "trace-run")).isEqualTo(preparation);

        verify(runtimeReloader).reloadWorkspaceConfiguration(USER_ID, SERVER_ID.value(), "trace-run");
    }

    @Test
    void prepareDoesNotReloadWhenReconciliationIsIdempotent() {
        when(reconciliationService.reconcile(workspace(), USER_ID, "trace-run"))
                .thenReturn(new AutomationReferenceRunPreparation(List.of(lease()), List.of(), false));

        service.prepare(workspace(), USER_ID, "trace-run");

        verify(runtimeReloader, never()).reloadWorkspaceConfiguration(USER_ID, SERVER_ID.value(), "trace-run");
    }

    @Test
    void acquireAndReleaseOnlyManageRunGenerationLeases() {
        AutomationReferenceRunPreparation preparation = new AutomationReferenceRunPreparation(
                List.of(lease()), List.of("局部告警"), false);

        service.acquire(run(), preparation, "trace-run");
        service.release(new RunId("run_1"), "trace-run");

        verify(automationRepository).replaceRunLeases(new RunId("run_1"), preparation.leases(), NOW);
        verify(automationRepository).deleteRunLeases(new RunId("run_1"));
    }

    private ApplicationAutomationReferenceRunLease lease() {
        return new ApplicationAutomationReferenceRunLease(
                new ApplicationId("app_demo"),
                new CodeRepositoryId("repo_auto"),
                3L,
                SERVER_ID);
    }

    private Run run() {
        return new Run(
                new RunId("run_1"),
                new SessionId("ses_1"),
                new WorkspaceId("wrk_main"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace-run");
    }

    private Workspace workspace() {
        return new Workspace(
                new WorkspaceId("wrk_main"),
                "测试工作区",
                "/logical/workspace",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                SERVER_ID.value(),
                "trace-workspace");
    }
}
