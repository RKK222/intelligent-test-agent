package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceViewApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_view");
    private static final ApplicationId APP_ID = new ApplicationId("app_view");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_requirements");

    @TempDir
    Path tempDir;

    private Path workspaceRoot;
    private Path referencesRoot;
    private WorkspaceApplicationService workspaceService;
    private ManagedWorkspaceRepository managedWorkspaceRepository;
    private ConfigurationManagementRepository configurationRepository;
    private ReferenceRepositoryRepository referenceRepository;
    private CommonParameterValues commonParameters;
    private WorkspaceViewApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        referencesRoot = Files.createDirectories(tempDir.resolve("references"));
        workspaceService = mock(WorkspaceApplicationService.class);
        managedWorkspaceRepository = mock(ManagedWorkspaceRepository.class);
        configurationRepository = mock(ConfigurationManagementRepository.class);
        referenceRepository = mock(ReferenceRepositoryRepository.class);
        commonParameters = mock(CommonParameterValues.class);

        when(workspaceService.getWorkspace(WORKSPACE_ID)).thenReturn(new Workspace(
                WORKSPACE_ID,
                "view",
                workspaceRoot.toString(),
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_view"));
        PersonalWorkspace personalWorkspace = mock(PersonalWorkspace.class);
        when(personalWorkspace.appId()).thenReturn(APP_ID);
        when(managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(personalWorkspace));
        when(configurationRepository.findApplication(APP_ID))
                .thenReturn(Optional.of(new ApplicationDefinition(APP_ID, "View App", true, NOW, NOW)));
        when(configurationRepository.findRepositoryByEnglishName("requirements"))
                .thenReturn(Optional.of(assetRepository()));
        when(configurationRepository.findRepositoriesByApplication(APP_ID))
                .thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(REPOSITORY_ID)).thenReturn(Optional.of(readyState()));
        when(referenceRepository.findReplicas(REPOSITORY_ID)).thenReturn(List.of(readyReplica()));
        when(commonParameters.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(referencesRoot.toString()));
        when(commonParameters.resolvedValue("REFERENCES_SDD_FOLDER_NAMES"))
                .thenReturn(Optional.of("docs,spec"));

        ReferenceRepositoryApplicationService referenceService = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                mock(UserRepository.class),
                mock(OpencodeProcessHeartbeatStore.class),
                commonParameters,
                mock(GitWorkspaceService.class),
                mock(SshKeyEncryptionService.class),
                new WorkspaceServerIdentity("server-a"),
                mock(ServerBroadcastPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new WorkspaceViewApplicationService(
                workspaceService,
                new WorkspaceFileService(1024 * 1024, 2000),
                managedWorkspaceRepository,
                referenceService);
    }

    @Test
    void parsesJsoncCommentsAndTrailingCommasAndMergesReferenceFolder() throws Exception {
        Files.createDirectories(workspaceRoot.resolve(".opencode"));
        Files.writeString(workspaceRoot.resolve(".opencode/opencode.jsonc"), """
                {
                  // 平台引用允许 JSONC 注释
                  "references": {
                    "docs-requirements": {
                      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                      "merge": true,
                      "sdd-folder-name": "docs",
                    },
                  },
                }
                """);
        Files.createDirectories(workspaceRoot.resolve("docs"));
        Files.writeString(workspaceRoot.resolve("docs/workspace.md"), "workspace");
        Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        Files.writeString(referencesRoot.resolve("requirements/docs/reference.md"), "reference");

        WorkspaceViewListResponse root = service.list(
                WORKSPACE_ID,
                new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, "", null));
        WorkspaceViewEntry docs = root.entries().stream()
                .filter(entry -> entry.name().equals("docs"))
                .findFirst()
                .orElseThrow();

        assertThat(root.warnings()).isEmpty();
        assertThat(docs.source()).isEqualTo(WorkspaceViewSource.MIXED);
        assertThat(docs.merged()).isTrue();
        assertThat(docs.locator().kind()).isEqualTo(WorkspaceViewLocatorKind.COMPOSITE);
        assertThat(docs.referenceAliases()).containsExactly("docs-requirements");

        WorkspaceViewListResponse children = service.list(WORKSPACE_ID, docs.locator());
        assertThat(children.entries())
                .extracting(WorkspaceViewEntry::name)
                .containsExactly("reference.md", "workspace.md");
        WorkspaceViewEntry reference = children.entries().stream()
                .filter(entry -> entry.name().equals("reference.md"))
                .findFirst()
                .orElseThrow();
        assertThat(reference.source()).isEqualTo(WorkspaceViewSource.REFERENCE);
        assertThat(reference.merged()).isTrue();
        assertThat(reference.readonly()).isTrue();
        assertThat(reference.workspacePath()).isNull();

        WorkspaceViewReadResponse content = service.read(WORKSPACE_ID, reference.locator());
        assertThat(content.content()).isEqualTo("reference");
        assertThat(content.readonly()).isTrue();
        assertThat(content.source()).isEqualTo(WorkspaceViewSource.REFERENCE);
        assertThat(content.referenceAlias()).isEqualTo("docs-requirements");
    }

    @Test
    void preservesLegalLeadingAndTrailingSpacesFromListedWorkspaceAndReferencePaths() throws Exception {
        Files.writeString(workspaceRoot.resolve(" workspace "), "workspace-spaces");
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Path docs = Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        Files.writeString(docs.resolve(" reference "), "reference-spaces");

        WorkspaceViewListResponse root = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        WorkspaceViewEntry workspaceFile = root.entries().stream()
                .filter(entry -> entry.name().equals(" workspace "))
                .findFirst()
                .orElseThrow();
        WorkspaceViewEntry docsEntry = root.entries().stream()
                .filter(entry -> entry.name().equals("docs"))
                .findFirst()
                .orElseThrow();
        WorkspaceViewEntry referenceFile = service.list(WORKSPACE_ID, docsEntry.locator()).entries().stream()
                .filter(entry -> entry.name().equals(" reference "))
                .findFirst()
                .orElseThrow();

        assertThat(service.read(WORKSPACE_ID, workspaceFile.locator()).content()).isEqualTo("workspace-spaces");
        assertThat(service.read(WORKSPACE_ID, referenceFile.locator()).content()).isEqualTo("reference-spaces");
    }

    @Test
    void exposesNonMergedReferenceUnderAliasWithoutMergedMetadata() throws Exception {
        writeConfig("""
                { "references": {
                  "docs-requirements": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                    "merge": false,
                    "sdd-folder-name": "docs"
                  }
                } }
                """);
        Files.createDirectories(referencesRoot.resolve("requirements/docs/nested"));
        Files.writeString(referencesRoot.resolve("requirements/docs/nested/readme.md"), "readonly");

        WorkspaceViewListResponse root = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        WorkspaceViewEntry alias = root.entries().getFirst();

        assertThat(alias.name()).isEqualTo("docs-requirements");
        assertThat(alias.source()).isEqualTo(WorkspaceViewSource.REFERENCE);
        assertThat(alias.merged()).isFalse();
        assertThat(alias.locator()).isEqualTo(new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "",
                "docs-requirements"));

        WorkspaceViewListResponse children = service.list(WORKSPACE_ID, alias.locator());
        assertThat(children.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("nested");
            assertThat(entry.merged()).isFalse();
            assertThat(entry.locator().kind()).isEqualTo(WorkspaceViewLocatorKind.REFERENCE);
            assertThat(entry.locator().path()).isEqualTo("nested");
        });
    }

    @Test
    void recursivelyCoalescesDirectoriesButKeepsFilesAndTypeConflictsParallelInAliasOrder() throws Exception {
        writeConfig("""
                { "references": {
                  "spec-zeta": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/zeta/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  },
                  "spec-alpha": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/alpha/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  }
                } }
                """);
        configureAsset("alpha", "repo_alpha");
        configureAsset("zeta", "repo_zeta");
        Files.createDirectories(workspaceRoot.resolve("spec/shared"));
        Files.writeString(workspaceRoot.resolve("spec/shared/duplicate.txt"), "workspace");
        Files.createDirectories(workspaceRoot.resolve("spec/conflict"));
        Files.writeString(workspaceRoot.resolve("spec/reverse-conflict"), "workspace-file");
        Files.createDirectories(referencesRoot.resolve("alpha/spec/shared"));
        Files.createDirectories(referencesRoot.resolve("zeta/spec/shared"));
        Files.createDirectories(referencesRoot.resolve("alpha/spec/reverse-conflict"));
        Files.writeString(referencesRoot.resolve("alpha/spec/shared/duplicate.txt"), "alpha");
        Files.writeString(referencesRoot.resolve("zeta/spec/shared/duplicate.txt"), "zeta");
        Files.writeString(referencesRoot.resolve("alpha/spec/conflict"), "file-conflict");

        WorkspaceViewEntry spec = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().getFirst();
        WorkspaceViewEntry shared = service.list(WORKSPACE_ID, spec.locator()).entries().stream()
                .filter(entry -> entry.name().equals("shared") && entry.directory())
                .findFirst()
                .orElseThrow();
        WorkspaceViewListResponse sharedChildren = service.list(WORKSPACE_ID, shared.locator());

        assertThat(shared.source()).isEqualTo(WorkspaceViewSource.MIXED);
        assertThat(shared.referenceAliases()).containsExactly("spec-alpha", "spec-zeta");
        assertThat(sharedChildren.entries())
                .extracting(entry -> entry.locator().referenceAlias())
                .containsExactly(null, "spec-alpha", "spec-zeta");
        assertThat(sharedChildren.entries())
                .extracting(WorkspaceViewEntry::collision)
                .containsExactly(false, true, true);

        WorkspaceViewListResponse specChildren = service.list(WORKSPACE_ID, spec.locator());
        assertThat(specChildren.entries().stream().filter(entry -> entry.name().equals("conflict")))
                .hasSize(2)
                .anySatisfy(entry -> {
                    assertThat(entry.directory()).isTrue();
                    assertThat(entry.collision()).isFalse();
                })
                .anySatisfy(entry -> {
                    assertThat(entry.directory()).isFalse();
                    assertThat(entry.collision()).isTrue();
                });
        assertThat(specChildren.entries().stream().filter(entry -> entry.name().equals("reverse-conflict")))
                .hasSize(2)
                .anySatisfy(entry -> {
                    assertThat(entry.source()).isEqualTo(WorkspaceViewSource.WORKSPACE);
                    assertThat(entry.collision()).isFalse();
                })
                .anySatisfy(entry -> {
                    assertThat(entry.source()).isEqualTo(WorkspaceViewSource.REFERENCE);
                    assertThat(entry.directory()).isTrue();
                    assertThat(entry.collision()).isTrue();
                });
        assertThat(specChildren.entries().stream()
                .filter(entry -> entry.name().equals("reverse-conflict"))
                .map(WorkspaceViewEntry::source))
                .containsExactly(WorkspaceViewSource.WORKSPACE, WorkspaceViewSource.REFERENCE);
    }

    @Test
    void compositeTraversalSkipsMountsThatDoNotContributeTheCurrentDirectory() throws Exception {
        writeConfig("""
                { "references": {
                  "spec-alpha": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/alpha/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  },
                  "spec-zeta": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/zeta/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  }
                } }
                """);
        configureAsset("alpha", "repo_alpha");
        configureAsset("zeta", "repo_zeta");
        Files.createDirectories(referencesRoot.resolve("alpha/spec/shared"));
        Files.writeString(referencesRoot.resolve("alpha/spec/shared/alpha.md"), "alpha");
        Files.createDirectories(referencesRoot.resolve("zeta/spec"));

        WorkspaceViewEntry spec = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().getFirst();
        WorkspaceViewEntry shared = service.list(WORKSPACE_ID, spec.locator()).entries().stream()
                .filter(entry -> entry.name().equals("shared"))
                .findFirst()
                .orElseThrow();
        WorkspaceViewListResponse children = service.list(WORKSPACE_ID, shared.locator());

        assertThat(children.entries()).extracting(WorkspaceViewEntry::name).containsExactly("alpha.md");
        assertThat(children.warnings()).isEmpty();
    }

    @Test
    void compositeDirectoryIdDoesNotChangeWhenContributingAliasesChange() throws Exception {
        configureAsset("alpha", "repo_alpha");
        configureAsset("zeta", "repo_zeta");
        Files.createDirectories(referencesRoot.resolve("alpha/spec"));
        Files.createDirectories(referencesRoot.resolve("zeta/spec"));
        writeConfig("""
                { "references": { "spec-alpha": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/alpha/spec",
                  "merge": true,
                  "sdd-folder-name": "spec"
                } } }
                """);
        String firstId = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().getFirst().id();

        writeConfig("""
                { "references": {
                  "spec-alpha": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/alpha/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  },
                  "spec-zeta": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/zeta/spec",
                    "merge": true,
                    "sdd-folder-name": "spec"
                  }
                } }
                """);
        WorkspaceViewEntry changed = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().getFirst();

        assertThat(changed.referenceAliases()).containsExactly("spec-alpha", "spec-zeta");
        assertThat(changed.id()).isEqualTo(firstId);
    }

    @Test
    void workspaceDirectoryIdDoesNotChangeWhenReferenceStartsMergingIntoIt() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("docs"));
        String workspaceOnlyId = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().stream()
                .filter(entry -> entry.name().equals("docs"))
                .findFirst()
                .orElseThrow()
                .id();

        Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        WorkspaceViewEntry mixed = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().stream()
                .filter(entry -> entry.name().equals("docs"))
                .findFirst()
                .orElseThrow();

        assertThat(mixed.source()).isEqualTo(WorkspaceViewSource.MIXED);
        assertThat(mixed.locator().kind()).isEqualTo(WorkspaceViewLocatorKind.COMPOSITE);
        assertThat(mixed.id()).isEqualTo(workspaceOnlyId);
    }

    @Test
    void malformedOrSpoofedPlatformReferencesBecomeSafeWarningsAndKeepWorkspaceFilesVisible() throws Exception {
        Files.writeString(workspaceRoot.resolve("visible.txt"), "visible");
        writeConfig("""
                { "references": {
                  "bad": { "path": "/private/secret", "merge": true, "sdd-folder-name": "docs" },
                  "docs-requirements": {
                    "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                    "merge": "true",
                    "sdd-folder-name": "docs"
                  }
                } }
                """);

        WorkspaceViewListResponse result = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(result.entries()).extracting(WorkspaceViewEntry::name).contains("visible.txt");
        assertThat(result.warnings()).hasSize(2).allSatisfy(warning -> {
            assertThat(warning.code()).isNotBlank();
            assertThat(warning.message()).doesNotContain(tempDir.toString(), "/private/secret");
        });
    }

    @Test
    void missingOrInvalidConfigDoesNotHideWorkspaceAndUnmanagedWorkspaceDoesNotMountReferences() throws Exception {
        Files.writeString(workspaceRoot.resolve("visible.txt"), "visible");

        WorkspaceViewListResponse missing = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        assertThat(missing.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(missing.warnings()).isEmpty();

        writeConfig("{ " + '"' + "references" + '"' + ": {");
        WorkspaceViewListResponse invalid = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        assertThat(invalid.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(invalid.warnings()).singleElement().satisfies(warning ->
                assertThat(warning.code()).isEqualTo("INVALID_JSONC"));

        when(managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.empty());
        when(managedWorkspaceRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.empty());
        when(managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.empty());
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        WorkspaceViewListResponse unmanaged = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        assertThat(unmanaged.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(unmanaged.warnings()).isEmpty();
    }

    @Test
    void oversizedPlatformConfigBecomesWarningWithoutHidingWorkspaceFiles() throws Exception {
        Files.writeString(workspaceRoot.resolve("visible.txt"), "visible");
        writeConfig("{\"references\":{},\"padding\":\"" + "x".repeat(1024 * 1024) + "\"}");

        WorkspaceViewListResponse result = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(result.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("CONFIG_UNAVAILABLE");
            assertThat(warning.message()).doesNotContain(tempDir.toString());
        });
    }

    @Test
    void rejectsGitReferenceObjectsThatAlsoContainPlatformMountFields() throws Exception {
        writeConfig("""
                { "references": { "docs-requirements": {
                  "repository": "https://git.example.test/spoof.git",
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Files.createDirectories(referencesRoot.resolve("requirements/docs"));

        WorkspaceViewListResponse result = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(result.entries()).isEmpty();
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.alias()).isEqualTo("docs-requirements");
            assertThat(warning.code()).isEqualTo("INVALID_REFERENCE");
            assertThat(warning.message()).doesNotContain("spoof.git", tempDir.toString());
        });
    }

    @Test
    void ignoresNativeGitStringAndLocalReferencesWithoutPlatformWarnings() throws Exception {
        Files.writeString(workspaceRoot.resolve("visible.txt"), "visible");
        writeConfig("""
                { "references": {
                  "git-object": {
                    "repository": "https://git.example.test/native.git",
                    "branch": "main"
                  },
                  "git-string": "https://git.example.test/shorthand.git",
                  "local-object": { "path": "../shared-specs" }
                } }
                """);

        WorkspaceViewListResponse result = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(result.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void staleOrMissingReferenceDirectoriesBecomeListWarningsInsteadOfBreakingTheTree() throws Exception {
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": false,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Path nested = Files.createDirectories(referencesRoot.resolve("requirements/docs/nested"));
        WorkspaceViewEntry alias = service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).entries().getFirst();
        WorkspaceViewEntry nestedEntry = service.list(WORKSPACE_ID, alias.locator()).entries().getFirst();

        Files.delete(nested);
        WorkspaceViewListResponse nestedMissing = service.list(WORKSPACE_ID, nestedEntry.locator());
        assertThat(nestedMissing.entries()).isEmpty();
        assertThat(nestedMissing.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.alias()).isEqualTo("docs-requirements");
            assertThat(warning.code()).isEqualTo(ErrorCode.NOT_FOUND.name());
        });

        Files.delete(referencesRoot.resolve("requirements/docs"));
        WorkspaceViewListResponse mountMissing = service.list(WORKSPACE_ID, alias.locator());
        assertThat(mountMissing.entries()).isEmpty();
        assertThat(mountMissing.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.alias()).isEqualTo("docs-requirements");
            assertThat(warning.code()).isEqualTo(ErrorCode.NOT_FOUND.name());
        });
    }

    @Test
    void keepsWorkspaceConfigFilesystemFailuresAsRpcErrors() {
        WorkspaceFileService failingFileService = mock(WorkspaceFileService.class);
        when(failingFileService.readContent(workspaceRoot.toString(), ".opencode/opencode.jsonc"))
                .thenThrow(new PlatformException(ErrorCode.INTERNAL_ERROR, "工作区磁盘暂不可用"));
        WorkspaceViewApplicationService failingService = new WorkspaceViewApplicationService(
                workspaceService,
                failingFileService,
                managedWorkspaceRepository,
                mock(ReferenceRepositoryApplicationService.class));

        assertThatThrownBy(() -> failingService.list(WORKSPACE_ID, WorkspaceViewLocator.root()))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void rejectsTraversalGitSymlinksAndCompositeReadsWithoutLeakingPhysicalPaths() throws Exception {
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Path docs = Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        Files.writeString(referencesRoot.resolve("requirements/secret.txt"), "secret");
        Path symlink = docs.resolve("link.txt");
        try {
            Files.createSymbolicLink(symlink, referencesRoot.resolve("requirements/secret.txt"));
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThatThrownBy(() -> service.read(WORKSPACE_ID, new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "../secret.txt",
                "docs-requirements")))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.read(WORKSPACE_ID, new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                ".git/config",
                "docs-requirements")))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.read(WORKSPACE_ID, new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "link.txt",
                "docs-requirements")))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.read(WORKSPACE_ID, WorkspaceViewLocator.root()))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.list(WORKSPACE_ID, new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.WORKSPACE,
                workspaceRoot.resolve("visible.txt").toString(),
                null)))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void appliesCapAfterGroupingAndProducesStableIds() throws Exception {
        for (int index = 0; index < 1001; index++) {
            Files.writeString(workspaceRoot.resolve("file-%04d.txt".formatted(index)), "x");
        }

        WorkspaceViewListResponse first = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());
        WorkspaceViewListResponse second = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(first.entries()).hasSize(1000);
        assertThat(first.truncated()).isTrue();
        assertThat(first.entries()).extracting(WorkspaceViewEntry::id)
                .containsExactlyElementsOf(second.entries().stream().map(WorkspaceViewEntry::id).toList());
    }

    @Test
    void doesNotMarkCompositeRootTruncatedOnlyBecauseAMountedFolderHasManyChildren() throws Exception {
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Path docs = Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        for (int index = 0; index < 1002; index++) {
            Files.writeString(docs.resolve("ref-%04d.txt".formatted(index)), "x");
        }

        WorkspaceViewListResponse root = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(root.entries()).singleElement().satisfies(entry -> assertThat(entry.name()).isEqualTo("docs"));
        assertThat(root.truncated()).isFalse();
    }

    @Test
    void mountsReferencesForCurrentReadyApplicationVersionReplica() throws Exception {
        when(managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.empty());
        when(managedWorkspaceRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.empty());
        ApplicationWorkspaceVersionReplica replica = mock(ApplicationWorkspaceVersionReplica.class);
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        when(replica.versionId()).thenReturn(new com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId("ver_view"));
        when(version.appId()).thenReturn(APP_ID);
        when(managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(replica));
        when(managedWorkspaceRepository.findVersion(replica.versionId())).thenReturn(Optional.of(version));
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Files.createDirectories(referencesRoot.resolve("requirements/docs"));

        WorkspaceViewListResponse result = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(result.entries()).extracting(WorkspaceViewEntry::name).contains("docs");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rebuildsCurrentFolderAndReplicaStateOnEveryCall() throws Exception {
        Files.writeString(workspaceRoot.resolve("visible.txt"), "visible");
        writeConfig("""
                { "references": { "docs-requirements": {
                  "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
                  "merge": true,
                  "sdd-folder-name": "docs"
                } } }
                """);
        Files.createDirectories(referencesRoot.resolve("requirements/docs"));
        assertThat(service.list(WORKSPACE_ID, WorkspaceViewLocator.root()).warnings()).isEmpty();

        when(commonParameters.resolvedValue("REFERENCES_SDD_FOLDER_NAMES"))
                .thenReturn(Optional.of("spec"));
        WorkspaceViewListResponse revoked = service.list(WORKSPACE_ID, WorkspaceViewLocator.root());

        assertThat(revoked.entries()).extracting(WorkspaceViewEntry::name).containsExactly("visible.txt");
        assertThat(revoked.warnings()).singleElement().satisfies(warning ->
                assertThat(warning.code()).isEqualTo(ErrorCode.FORBIDDEN.name()));
    }

    private void writeConfig(String content) throws Exception {
        Files.createDirectories(workspaceRoot.resolve(".opencode"));
        Files.writeString(workspaceRoot.resolve(".opencode/opencode.jsonc"), content);
    }

    private void configureAsset(String englishName, String id) {
        CodeRepository repository = assetRepository(new CodeRepositoryId(id), englishName);
        when(configurationRepository.findRepositoryByEnglishName(englishName)).thenReturn(Optional.of(repository));
        when(configurationRepository.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(
                assetRepository(),
                assetRepository(new CodeRepositoryId("repo_alpha"), "alpha"),
                assetRepository(new CodeRepositoryId("repo_zeta"), "zeta")));
        when(referenceRepository.findState(repository.repositoryId())).thenReturn(Optional.of(new ReferenceRepositoryState(
                repository.repositoryId(),
                "main",
                "commit",
                1L,
                ReferenceRepositoryStatus.READY,
                null,
                "trace",
                null,
                NOW,
                NOW,
                NOW)));
        when(referenceRepository.findReplicas(repository.repositoryId())).thenReturn(List.of(new ReferenceRepositoryReplica(
                repository.repositoryId(),
                new LinuxServerId("server-a"),
                1L,
                ReferenceRepositoryReplicaStatus.READY,
                "main",
                "commit",
                0,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW)));
    }

    private CodeRepository assetRepository() {
        return assetRepository(REPOSITORY_ID, "requirements");
    }

    private CodeRepository assetRepository(CodeRepositoryId repositoryId, String englishName) {
        return new CodeRepository(
                repositoryId,
                "https://git.example.test/" + englishName + ".git",
                englishName,
                englishName,
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                false,
                NOW,
                NOW);
    }

    private ReferenceRepositoryState readyState() {
        return new ReferenceRepositoryState(
                REPOSITORY_ID,
                "main",
                "commit",
                1L,
                ReferenceRepositoryStatus.READY,
                null,
                "trace",
                null,
                NOW,
                NOW,
                NOW);
    }

    private ReferenceRepositoryReplica readyReplica() {
        return new ReferenceRepositoryReplica(
                REPOSITORY_ID,
                new LinuxServerId("server-a"),
                1L,
                ReferenceRepositoryReplicaStatus.READY,
                "main",
                "commit",
                0,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }
}
