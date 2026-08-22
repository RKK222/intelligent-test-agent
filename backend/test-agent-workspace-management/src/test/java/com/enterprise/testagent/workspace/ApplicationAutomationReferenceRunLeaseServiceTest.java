package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceReplica;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationAutomationReferenceRunLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T03:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_demo");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_auto");
    private static final UserId USER_ID = new UserId("usr_demo");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    private ConfigurationManagementRepository configurationRepository;
    private ApplicationAutomationReferenceRepository automationRepository;
    private AutomationWorkspaceReferenceCatalogService catalogService;
    private AgentConfigApplicationService agentConfigService;
    private PersonalAgentConfigRuntimeReloader runtimeReloader;
    private ApplicationAutomationReferenceRunLeaseService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        automationRepository = mock(ApplicationAutomationReferenceRepository.class);
        catalogService = mock(AutomationWorkspaceReferenceCatalogService.class);
        agentConfigService = mock(AgentConfigApplicationService.class);
        runtimeReloader = mock(PersonalAgentConfigRuntimeReloader.class);
        service = new ApplicationAutomationReferenceRunLeaseService(
                configurationRepository,
                automationRepository,
                catalogService,
                agentConfigService,
                runtimeReloader,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(catalogService.resolveHostApplication(new WorkspaceId("wrk_main")))
                .thenReturn(Optional.of(APP_ID));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(agentConfigService.readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null))
                .thenReturn(new FileContentResponse("opencode.jsonc", "{\"references\":{}}", 17));
    }

    @Test
    void acquireRecordsExactActiveGenerationAndServer() {
        when(automationRepository.findState(APP_ID, REPOSITORY_ID)).thenReturn(Optional.of(state(3L)));
        when(automationRepository.findGeneration(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(Optional.of(generation(3L)));
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(3L, ReferenceRepositoryReplicaStatus.READY, "main", "abc123")));

        AutomationReferenceRunPreparation preparation = service.prepare(workspace(), USER_ID, "trace-run");
        service.acquire(run(), preparation, "trace-run");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ApplicationAutomationReferenceRunLease>> leases =
                ArgumentCaptor.forClass(Collection.class);
        verify(automationRepository).replaceRunLeases(eq(new RunId("run_1")), leases.capture(), eq(NOW));
        assertThat(leases.getValue()).containsExactly(
                new ApplicationAutomationReferenceRunLease(APP_ID, REPOSITORY_ID, 3L, SERVER_ID));
    }

    @Test
    void prepareSkipsOnlyUnavailableReplicaAndKeepsRunUsable() {
        when(automationRepository.findState(APP_ID, REPOSITORY_ID)).thenReturn(Optional.of(state(3L)));
        when(automationRepository.findGeneration(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(Optional.of(generation(3L)));
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(3L, ReferenceRepositoryReplicaStatus.BLOCKED, "main", "abc123")));

        AutomationReferenceRunPreparation preparation = service.prepare(workspace(), USER_ID, "trace-run");

        assertThat(preparation.leases()).isEmpty();
        assertThat(preparation.warnings()).singleElement().asString().contains("本次运行已跳过");
        service.acquire(run(), preparation, "trace-run");
        verify(automationRepository).replaceRunLeases(new RunId("run_1"), List.of(), NOW);
    }

    @Test
    void releaseDeletesLeasesIdempotently() {
        service.release(new RunId("run_1"), "trace-run");
        verify(automationRepository).deleteRunLeases(new RunId("run_1"));
    }

    private CodeRepository repository() {
        return new CodeRepository(
                REPOSITORY_ID,
                "https://example.test/automation.git",
                "自动化库",
                "automation-repo",
                CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);
    }

    private ApplicationAutomationReferenceState state(long generation) {
        return new ApplicationAutomationReferenceState(
                APP_ID,
                REPOSITORY_ID,
                generation,
                null,
                generation + 1,
                4L,
                ReferenceRepositoryStatus.READY,
                AutomationReferenceOperationType.CONFIGURE,
                "trace-config",
                null,
                NOW,
                NOW);
    }

    private ApplicationAutomationReferenceGeneration generation(long generation) {
        return new ApplicationAutomationReferenceGeneration(
                APP_ID,
                REPOSITORY_ID,
                generation,
                "main",
                "src/test",
                "测试自动化引用",
                false,
                "abc123",
                AutomationReferenceGenerationStatus.READY,
                AutomationReferenceOperationType.CONFIGURE,
                USER_ID,
                "operation-1",
                "trace-config",
                null,
                NOW,
                NOW,
                NOW);
    }

    private ApplicationAutomationReferenceReplica replica(
            long generation,
            ReferenceRepositoryReplicaStatus status,
            String branch,
            String commit) {
        return new ApplicationAutomationReferenceReplica(
                APP_ID,
                REPOSITORY_ID,
                generation,
                SERVER_ID,
                status,
                branch,
                commit,
                0,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW,
                NOW);
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
