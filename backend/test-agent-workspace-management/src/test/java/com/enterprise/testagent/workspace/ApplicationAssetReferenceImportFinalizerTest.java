package com.enterprise.testagent.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportCandidate;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportConflict;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationAssetReferenceImportFinalizerTest {
    private static final ApplicationId APP = new ApplicationId("app-demo");
    private static final CodeRepositoryId REPOSITORY = new CodeRepositoryId("repo_assets");
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    @Test
    void conflictingLegacyVariantsNeverPublishButIndependentAliasDoes() {
        ApplicationAssetReferenceStore store = mock(ApplicationAssetReferenceStore.class);
        when(store.claimImportApplication(APP, NOW)).thenReturn(true);
        when(store.conflicts(APP)).thenReturn(List.of());
        when(store.candidates(APP)).thenReturn(List.of(
                candidate("wrk_a", "docs", "docs-assets", "版本一"),
                candidate("wrk_b", "docs", "docs-assets", "版本二"),
                candidate("wrk_a", "spec", "spec-assets", "设计资料")));
        when(store.list(APP)).thenReturn(List.of());
        when(store.insert(any())).thenReturn(1);

        new ApplicationAssetReferenceImportFinalizer(store, Clock.fixed(NOW, ZoneOffset.UTC))
                .finalizeApplication(APP);

        verify(store).recordConflict(APP, "docs-assets", "多个管理员或版本的旧配置不一致，未自动发布", NOW);
        verify(store).insert(new ApplicationAssetReference(APP, REPOSITORY, "spec", "spec-assets",
                true, "spec", "设计资料", 1, NOW));
        verify(store, never()).insert(new ApplicationAssetReference(APP, REPOSITORY, "docs", "docs-assets",
                true, "docs", "版本一", 1, NOW));
    }

    @Test
    void invalidEntryReportedByScannerBlocksSameAliasFromOtherAdmin() {
        ApplicationAssetReferenceStore store = mock(ApplicationAssetReferenceStore.class);
        when(store.claimImportApplication(APP, NOW)).thenReturn(true);
        when(store.conflicts(APP)).thenReturn(List.of(new ImportConflict("docs-assets", "旧配置无效")));
        when(store.candidates(APP)).thenReturn(List.of(candidate("wrk_other", "docs", "docs-assets", "资料")));

        new ApplicationAssetReferenceImportFinalizer(store, Clock.fixed(NOW, ZoneOffset.UTC))
                .finalizeApplication(APP);

        verify(store, never()).insert(any());
    }

    private static ImportCandidate candidate(String workspace, String directory, String alias, String description) {
        return new ImportCandidate(workspace, APP, REPOSITORY, directory, alias, true,
                directory, description);
    }
}
