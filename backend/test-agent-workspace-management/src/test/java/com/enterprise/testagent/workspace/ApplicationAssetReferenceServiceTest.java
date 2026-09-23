package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.reference.ApplicationAssetReference;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationAssetReferenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final ApplicationId APP = new ApplicationId("app-demo");
    private static final CodeRepositoryId REPOSITORY = new CodeRepositoryId("repo_assets");
    private static final UserId MEMBER = new UserId("usr_member");

    private ConfigurationManagementRepository configuration;
    private ApplicationAssetReferenceStore store;
    private ReferenceRepositoryApplicationService referenceService;
    private ApplicationAssetReferenceService service;

    @BeforeEach
    void setUp() {
        configuration = mock(ConfigurationManagementRepository.class);
        store = mock(ApplicationAssetReferenceStore.class);
        referenceService = mock(ReferenceRepositoryApplicationService.class);
        service = new ApplicationAssetReferenceService(configuration, store, referenceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configuration.findApplication(APP))
                .thenReturn(Optional.of(new ApplicationDefinition(APP, "演示应用", true, NOW, NOW)));
        when(configuration.findRepositoriesByApplication(APP)).thenReturn(List.of(repository()));
    }

    @Test
    void activeMemberWithoutAssetGitPermissionReadsOnlySafeFields() {
        when(configuration.isActiveMember(APP, MEMBER)).thenReturn(true);
        when(store.list(APP)).thenReturn(List.of(reference()));

        var listing = service.list(APP.value(), MEMBER, false, false);

        assertThat(listing.configurations()).singleElement().satisfies(item -> {
            assertThat(item.alias()).isEqualTo("docs-assets");
            assertThat(item.directoryPath()).isEqualTo("docs");
            assertThat(item.description()).isEqualTo("产品资料");
        });
        assertThat(listing.importPending()).isFalse();
        assertThat(listing.conflicts()).isEmpty();
        verify(configuration).isActiveMember(APP, MEMBER);
    }

    @Test
    void removedApplicationMemberIsRejected() {
        assertThatThrownBy(() -> service.list(APP.value(), MEMBER, false, false))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void administratorSavesOnlySelectableReadyDirectoryAndUsesVersionCas() {
        when(referenceService.tree("app-demo", "repo_assets", "ai-agent"))
                .thenReturn(List.of(new ReferenceRepositoryResponses.TreeNode(
                        "ai-agent/spec", "spec", true, 0, true, true)));
        when(store.insert(any())).thenReturn(1);

        var saved = service.save(APP.value(), REPOSITORY.value(), "ai-agent/spec", true, "  设计资料  ", 0);

        assertThat(saved.alias()).isEqualTo("spec-assets");
        assertThat(saved.directoryPath()).isEqualTo("ai-agent/spec");
        assertThat(saved.description()).isEqualTo("设计资料");
        assertThat(saved.version()).isEqualTo(1);
        verify(store).insert(new ApplicationAssetReference(APP, REPOSITORY, "ai-agent/spec",
                "spec-assets", true, "spec", "设计资料", 1, NOW));
        verify(store).clearConflict(APP, "spec-assets");
    }

    @Test
    void unavailableDirectoryAndConcurrentVersionCannotBePublished() {
        when(referenceService.tree("app-demo", "repo_assets", ""))
                .thenReturn(List.of(new ReferenceRepositoryResponses.TreeNode(
                        "docs", "docs", true, 0, false, false)));
        assertThatThrownBy(() -> service.save(APP.value(), REPOSITORY.value(), "docs", true, "资料", 0))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.save(APP.value(), REPOSITORY.value(), "../docs", true, "资料", 0))
                .isInstanceOf(PlatformException.class);
        when(referenceService.tree("app-demo", "repo_assets", ""))
                .thenReturn(List.of(new ReferenceRepositoryResponses.TreeNode(
                        "space docs", "space docs", true, 0, true, true)));
        assertThatThrownBy(() -> service.save(APP.value(), REPOSITORY.value(), "space docs", true, "资料", 0))
                .isInstanceOf(PlatformException.class);
        when(referenceService.tree("app-demo", "repo_assets", ""))
                .thenReturn(List.of(new ReferenceRepositoryResponses.TreeNode(
                        "docs", "docs", true, 0, true, true)));
        assertThatThrownBy(() -> service.save(APP.value(), REPOSITORY.value(), "docs", true, "资料", 1))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private static CodeRepository repository() {
        return new CodeRepository(REPOSITORY, "ssh://git.example.test/assets.git", "资产库", "assets",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(), false, NOW, NOW);
    }

    private static ApplicationAssetReference reference() {
        return new ApplicationAssetReference(APP, REPOSITORY, "docs", "docs-assets", true,
                "docs", "产品资料", 1, NOW);
    }
}
