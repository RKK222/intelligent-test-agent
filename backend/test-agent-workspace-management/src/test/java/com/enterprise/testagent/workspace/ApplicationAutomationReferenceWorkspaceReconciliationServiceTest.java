package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceReplica;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationAutomationReferenceWorkspaceReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T03:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_demo");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_auto");
    private static final UserId USER_ID = new UserId("usr_demo");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    private ConfigurationManagementRepository configurationRepository;
    private ApplicationAutomationReferenceRepository automationRepository;
    private AutomationWorkspaceReferenceCatalogService catalogService;
    private AgentConfigApplicationService agentConfigService;
    private ApplicationAutomationReferenceWorkspaceReconciliationService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        automationRepository = mock(ApplicationAutomationReferenceRepository.class);
        catalogService = mock(AutomationWorkspaceReferenceCatalogService.class);
        agentConfigService = mock(AgentConfigApplicationService.class);
        service = new ApplicationAutomationReferenceWorkspaceReconciliationService(
                configurationRepository,
                automationRepository,
                catalogService,
                agentConfigService,
                new ObjectMapper());
        when(catalogService.resolveHostApplication(new WorkspaceId("wrk_main")))
                .thenReturn(Optional.of(APP_ID));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(automationRepository.findState(APP_ID, REPOSITORY_ID)).thenReturn(Optional.of(state(3L)));
        when(automationRepository.findGeneration(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(Optional.of(generation(3L)));
    }

    @Test
    void readyReplicaWritesOneManagedReferenceAndReturnsExactLease() {
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(ReferenceRepositoryReplicaStatus.READY)));
        when(agentConfigService.readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null))
                .thenReturn(new FileContentResponse("opencode.jsonc", "{\n  // keep\n  \"future\": true\n}\n", 34));
        when(agentConfigService.writeWorkspaceAgentFileIfUnchanged(
                        eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), anyString(), eq(null)))
                .thenReturn(true);

        var result = service.reconcile(workspace(), USER_ID, "trace-run");

        assertThat(result.configurationChanged()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.leases()).singleElement().satisfies(lease -> {
            assertThat(lease.appId()).isEqualTo(APP_ID);
            assertThat(lease.repositoryId()).isEqualTo(REPOSITORY_ID);
            assertThat(lease.generation()).isEqualTo(3L);
            assertThat(lease.linuxServerId()).isEqualTo(SERVER_ID);
        });
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(agentConfigService).writeWorkspaceAgentFileIfUnchanged(
                eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), content.capture(), eq(null));
        assertThat(content.getValue())
                .contains("// keep", "\"future\": true", "\"automation-tests\"")
                .contains("\"testagent-automation-generation\":3");
    }

    @Test
    void unavailableReplicaRemovesStaleReferenceAndReturnsLocalWarning() {
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(ReferenceRepositoryReplicaStatus.BLOCKED)));
        String source = """
                {
                  "references": {
                    "old": {
                      "path": "/old",
                      "testagent-reference-kind": "automation",
                      "testagent-automation-app-id": "app_demo",
                      "testagent-automation-repository-id": "repo_auto"
                    }
                  },
                  "permission": { "external_directory": { "/old/*": "allow" } }
                }
                """;
        when(agentConfigService.readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null))
                .thenReturn(new FileContentResponse("opencode.jsonc", source, source.length()));
        when(agentConfigService.writeWorkspaceAgentFileIfUnchanged(
                        eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), anyString(), eq(null)))
                .thenReturn(true);

        var result = service.reconcile(workspace(), USER_ID, "trace-run");

        assertThat(result.leases()).isEmpty();
        assertThat(result.warnings()).singleElement().asString().contains("共享只读副本尚未就绪");
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(agentConfigService).writeWorkspaceAgentFileIfUnchanged(
                eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), content.capture(), eq(null));
        assertThat(content.getValue()).doesNotContain("\"old\"", "/old/*");
    }

    @Test
    void concurrentEditIsReadAndPatchedAgainOnce() {
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(ReferenceRepositoryReplicaStatus.READY)));
        when(agentConfigService.readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null))
                .thenReturn(
                        new FileContentResponse("opencode.jsonc", "{\"references\":{}}", 17),
                        new FileContentResponse("opencode.jsonc", "{\"references\":{},\"user-field\":true}", 35));
        when(agentConfigService.writeWorkspaceAgentFileIfUnchanged(
                        eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), anyString(), eq(null)))
                .thenReturn(false, true);

        var result = service.reconcile(workspace(), USER_ID, "trace-run");

        assertThat(result.configurationChanged()).isTrue();
        verify(agentConfigService, times(2)).readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null);
        ArgumentCaptor<String> contents = ArgumentCaptor.forClass(String.class);
        verify(agentConfigService, times(2)).writeWorkspaceAgentFileIfUnchanged(
                eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), contents.capture(), eq(null));
        assertThat(contents.getAllValues().get(1)).contains("\"user-field\":true");
    }

    @Test
    void secondConcurrentEditFailsClosed() {
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(List.of(replica(ReferenceRepositoryReplicaStatus.READY)));
        when(agentConfigService.readWorkspaceAgentFile("wrk_main", "opencode.jsonc", null))
                .thenReturn(new FileContentResponse("opencode.jsonc", "{\"references\":{}}", 17));
        when(agentConfigService.writeWorkspaceAgentFileIfUnchanged(
                        eq("wrk_main"), eq("opencode.jsonc"), eq(true), anyString(), anyString(), eq(null)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.reconcile(workspace(), USER_ID, "trace-run"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
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
                "automation-tests",
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

    private ApplicationAutomationReferenceReplica replica(ReferenceRepositoryReplicaStatus status) {
        return new ApplicationAutomationReferenceReplica(
                APP_ID,
                REPOSITORY_ID,
                3L,
                SERVER_ID,
                status,
                "main",
                "abc123",
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
