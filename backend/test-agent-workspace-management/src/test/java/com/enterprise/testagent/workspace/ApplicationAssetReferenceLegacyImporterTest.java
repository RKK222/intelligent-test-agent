package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportCandidate;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationAssetReferenceLegacyImporterTest {
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final ApplicationId APP = new ApplicationId("app-demo");
    private static final CodeRepositoryId REPOSITORY = new CodeRepositoryId("repo_assets");
    private static final ImportSource SOURCE = new ImportSource("wrk_admin", APP, "server-a");

    @Test
    void publishesValidCandidateAndIsolatesInvalidAliasFromSameFile() {
        ApplicationAssetReferenceStore store = mock(ApplicationAssetReferenceStore.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AgentConfigApplicationService agentConfig = mock(AgentConfigApplicationService.class);
        ReferenceRepositoryApplicationService references = mock(ReferenceRepositoryApplicationService.class);
        ApplicationAssetReferenceImportFinalizer finalizer = mock(ApplicationAssetReferenceImportFinalizer.class);
        when(store.claimLocalSources(eq("server-a"), eq(NOW), any(), eq(25))).thenReturn(List.of(SOURCE));
        when(store.readyImportApplications()).thenReturn(List.of());
        when(configuration.findRepositoriesByApplication(APP)).thenReturn(List.of(repository()));
        String jsonc = """
                {"references":{
                  "docs-assets":{"path":"{env:OPENCODE_REFERENCES_DIR}/assets/docs",
                    "merge":true,"sdd-folder-name":"docs","description":"资料"},
                  "spec-assets":{"path":"{env:OPENCODE_REFERENCES_DIR}/assets/other",
                    "merge":true,"sdd-folder-name":"spec","description":"冲突"}
                }}
                """;
        when(agentConfig.readWorkspaceAgentFile("wrk_admin", "opencode.jsonc", null))
                .thenReturn(new FileContentResponse("opencode.jsonc", jsonc, jsonc.length()));
        when(references.tree("app-demo", "repo_assets", ""))
                .thenReturn(List.of(new ReferenceRepositoryResponses.TreeNode("docs", "docs", true, 0, true, true)));

        importer(store, configuration, agentConfig, references, finalizer).scan();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImportCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(store).finishSource(eq(SOURCE), candidates.capture(), eq(NOW));
        assertThat(candidates.getValue()).singleElement().satisfies(candidate -> {
            assertThat(candidate.alias()).isEqualTo("docs-assets");
            assertThat(candidate.directoryPath()).isEqualTo("docs");
        });
        verify(store).recordConflict(APP, "spec-assets", "旧个人资产引用字段或目录无效，未自动发布", NOW);
    }

    @Test
    void unavailableWorkspaceRemainsPendingForRetry() {
        ApplicationAssetReferenceStore store = mock(ApplicationAssetReferenceStore.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AgentConfigApplicationService agentConfig = mock(AgentConfigApplicationService.class);
        ReferenceRepositoryApplicationService references = mock(ReferenceRepositoryApplicationService.class);
        when(store.claimLocalSources(eq("server-a"), eq(NOW), any(), eq(25))).thenReturn(List.of(SOURCE));
        when(store.readyImportApplications()).thenReturn(List.of());
        when(agentConfig.readWorkspaceAgentFile("wrk_admin", "opencode.jsonc", null))
                .thenThrow(new PlatformException(ErrorCode.CONFLICT, "服务器暂不可用"));

        importer(store, configuration, agentConfig, references,
                mock(ApplicationAssetReferenceImportFinalizer.class)).scan();

        verify(store).retrySource(SOURCE, "工作区或资产副本暂不可用", NOW.plusSeconds(120));
    }

    private static ApplicationAssetReferenceLegacyImporter importer(ApplicationAssetReferenceStore store,
            ConfigurationManagementRepository configuration, AgentConfigApplicationService agentConfig,
            ReferenceRepositoryApplicationService references, ApplicationAssetReferenceImportFinalizer finalizer) {
        return new ApplicationAssetReferenceLegacyImporter(store, configuration, agentConfig,
                references, new WorkspaceServerIdentity("server-a"), finalizer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CodeRepository repository() {
        return new CodeRepository(REPOSITORY, "ssh://git.example.test/assets.git", "资产库", "assets",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(), false, NOW, NOW);
    }
}
