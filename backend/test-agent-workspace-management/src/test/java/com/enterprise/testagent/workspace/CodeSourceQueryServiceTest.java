package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeSourceQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T04:00:00Z");
    private static final UserId USER_ID = new UserId("usr_code_reader");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_orders");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_orders_source");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    @TempDir
    Path tempDir;

    @Test
    void readsImmutableGenerationBaselineWithVersionedEvidence() throws Exception {
        Fixture fixture = fixture(true);
        Files.writeString(fixture.baseline().resolve("src/OrderService.java"),
                "package demo;\nclass OrderService {\n  void submit() {}\n}\n");
        Files.createDirectories(fixture.editable().resolve("src"));
        Files.writeString(fixture.editable().resolve("src/OrderService.java"), "locally changed\n");

        var result = fixture.service().read(
                USER_ID, REPOSITORY_ID.value(), "src/OrderService.java", 2, 3);

        assertThat(result.content()).isEqualTo("class OrderService {\n  void submit() {}");
        assertThat(result.evidence().repositoryId()).isEqualTo(REPOSITORY_ID.value());
        assertThat(result.evidence().branch()).isEqualTo("main");
        assertThat(result.evidence().commit()).isEqualTo("a".repeat(40));
        assertThat(result.evidence().generation()).isEqualTo(4L);
        assertThat(result.evidence().selectedPaths()).containsExactly(
                new CodeSourceQueryService.SourceSelection("src", "DIRECTORY"));
        assertThat(result.evidence().sourceState()).isEqualTo("IMMUTABLE_BASELINE");
        assertThat(result.sha256()).hasSize(64);
        verify(fixture.authorizer(), times(2)).requireClassifiedFileAccess(USER_ID, WORKSPACE_ID, false);
    }

    @Test
    void contentSearchReturnsLineEvidenceAndReportsLimitTruncation() throws Exception {
        Fixture fixture = fixture(true);
        Files.writeString(fixture.baseline().resolve("src/OrderService.java"),
                "submit(order);\nvalidate(order);\nsubmit(audit);\n");

        var result = fixture.service().search(
                USER_ID, REPOSITORY_ID.value(), "src", "submit", 1);

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.path()).isEqualTo("src/OrderService.java");
            assertThat(match.line()).isEqualTo(1);
            assertThat(match.excerpt()).contains("submit(order)");
        });
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo("RESULT_LIMIT");
    }

    @Test
    void contentSearchReportsWhenDepthBudgetLeavesDirectoriesUnsearched() throws Exception {
        Fixture fixture = fixture(true, 1);
        Files.createDirectories(fixture.baseline().resolve("src/deep"));
        Files.writeString(fixture.baseline().resolve("src/deep/OrderService.java"), "submit(order);\n");

        var result = fixture.service().search(
                USER_ID, REPOSITORY_ID.value(), "src", "submit", 10);

        assertThat(result.matches()).isEmpty();
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo("DEPTH_LIMIT");
    }

    @Test
    void missingKnowledgeBaselineFailsClosedInsteadOfReadingEditableWorkspace() throws Exception {
        Fixture fixture = fixture(false);
        Files.createDirectories(fixture.editable().resolve("src"));
        Files.writeString(fixture.editable().resolve("src/OrderService.java"), "editable only\n");

        var context = fixture.service().context(USER_ID, REPOSITORY_ID.value());
        assertThat(context.available()).isFalse();
        assertThat(context.preparationAction()).isEqualTo("OPEN_APP_SOURCE_PREPARATION");
        assertThat(context.evidence().sourceState()).isEqualTo("SOURCE_BASELINE_UNAVAILABLE");
        assertThat(context.evidence().commit()).isEqualTo("a".repeat(40));
        assertThat(context.evidence().selectedPaths()).containsExactly(
                new CodeSourceQueryService.SourceSelection("src", "DIRECTORY"));

        assertThatThrownBy(() -> fixture.service().read(
                        USER_ID, REPOSITORY_ID.value(), "src/OrderService.java", null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("reason", "SOURCE_BASELINE_UNAVAILABLE");
                });
    }

    @Test
    void readFailsClosedWhenBaselineFileChangesDuringRead() throws Exception {
        WorkspaceFileService mutatingFiles = new WorkspaceFileService() {
            @Override
            public FileContentResponse readContent(String rootPath, String relativePath) {
                FileContentResponse result = super.readContent(rootPath, relativePath);
                try {
                    Files.writeString(Path.of(rootPath).resolve(relativePath), result.content() + "changed");
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                return result;
            }
        };
        Fixture fixture = fixture(true, mutatingFiles);
        Files.writeString(fixture.baseline().resolve("src/OrderService.java"), "class OrderService {}\n");

        assertThatThrownBy(() -> fixture.service().read(
                        USER_ID, REPOSITORY_ID.value(), "src/OrderService.java", null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.details()).containsEntry("reason", "SOURCE_CHANGED_DURING_READ"));
    }

    @Test
    void readRechecksAuthorizationBeforeReturningContent() throws Exception {
        Fixture fixture = fixture(true);
        Files.writeString(fixture.baseline().resolve("src/OrderService.java"), "class OrderService {}\n");
        when(fixture.authorizer().requireClassifiedFileAccess(USER_ID, WORKSPACE_ID, false))
                .thenReturn(ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE)
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "应用成员权限已撤销"));

        assertThatThrownBy(() -> fixture.service().read(
                        USER_ID, REPOSITORY_ID.value(), "src/OrderService.java", null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private Fixture fixture(boolean createBaseline) throws Exception {
        return fixture(createBaseline, new WorkspaceFileService());
    }

    private Fixture fixture(boolean createBaseline, int maxSearchDepth) throws Exception {
        return fixture(createBaseline, new WorkspaceFileService(), maxSearchDepth);
    }

    private Fixture fixture(boolean createBaseline, WorkspaceFileService files) throws Exception {
        return fixture(createBaseline, files, 20);
    }

    private Fixture fixture(boolean createBaseline, WorkspaceFileService files, int maxSearchDepth) throws Exception {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ConversationWorkspaceAccessAuthorizer authorizer = mock(ConversationWorkspaceAccessAuthorizer.class);
        ManagedWorkspacePathResolver paths = mock(ManagedWorkspacePathResolver.class);
        AppSourceIndexManager indexes = new AppSourceIndexManager();
        Path editable = tempDir.resolve("appsource/orders");
        Files.createDirectories(editable);
        Path baseline = AppSourceKnowledgeBaseline.root(editable, 4L);
        AppSourceSnapshot snapshot = snapshot(indexes);
        if (createBaseline) {
            Files.createDirectories(baseline.resolve("src"));
            Files.write(baseline.resolve(AppSourceApplicationService.INDEX_FILE_NAME), indexes.canonicalBytes(snapshot));
        }
        CodeRepository repository = new CodeRepository(
                REPOSITORY_ID, "ssh://git.example/orders.git", "订单服务", "orders",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), false,
                NOW.minusSeconds(3600), NOW);
        AppSourceReplica replica = new AppSourceReplica(
                REPOSITORY_ID, 4L, SERVER_ID, WORKSPACE_ID, AppSourceReplicaStatus.READY,
                null, null, 1, null, null, null, NOW.minusSeconds(60), NOW);
        Workspace workspace = new Workspace(
                WORKSPACE_ID, "orders", "appsource:orders", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, SERVER_ID.value(), "trace_source");
        when(configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(repository));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op_source", 1L, NOW.minusSeconds(60), NOW)));
        when(appSources.findReplica(REPOSITORY_ID, 4L, SERVER_ID)).thenReturn(Optional.of(replica));
        when(workspaces.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(authorizer.requireClassifiedFileAccess(USER_ID, WORKSPACE_ID, false))
                .thenReturn(ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE);
        when(paths.resolve("appsource:orders")).thenReturn(editable);
        when(paths.appSourceValue("orders")).thenReturn("appsource:orders");
        CodeSourceQueryService service = new CodeSourceQueryService(
                appSources, configuration, workspaces, authorizer, paths,
                files, indexes, new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC), maxSearchDepth, 2L * 1024L * 1024L, 5000L, 2000);
        return new Fixture(service, authorizer, editable, baseline);
    }

    private AppSourceSnapshot snapshot(AppSourceIndexManager indexes) {
        AppSourceSnapshot raw = new AppSourceSnapshot(
                REPOSITORY_ID, 4L, "orders", AppSourcePurpose.TEAM, USER_ID,
                "main", "a".repeat(40), List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, NOW.minusSeconds(3600), NOW.plusSeconds(23 * 3600), AppSourceSnapshotStatus.ACTIVE,
                NOW.minusSeconds(3600), NOW);
        return new AppSourceSnapshot(
                raw.repositoryId(), raw.generation(), raw.repositoryEnglishName(), raw.purpose(), raw.ownerUserId(),
                raw.branch(), raw.targetCommit(), raw.selectedPaths(), indexes.canonicalSha256(raw),
                raw.acceptedAt(), raw.expiresAt(), raw.status(), raw.createdAt(), raw.updatedAt());
    }

    private record Fixture(
            CodeSourceQueryService service,
            ConversationWorkspaceAccessAuthorizer authorizer,
            Path editable,
            Path baseline) {
    }
}
