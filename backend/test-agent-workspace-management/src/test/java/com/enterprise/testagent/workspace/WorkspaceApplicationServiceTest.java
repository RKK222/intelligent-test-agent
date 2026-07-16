package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WorkspaceApplicationServiceTest {

    @TempDir
    Path root;

    @Test
    void serviceCreatesWorkspaceWithGeneratedIdAndTraceId() throws Exception {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        WorkspaceApplicationService service = new WorkspaceApplicationService(
                repository,
                new WorkspaceFileService(),
                new WorkspaceServerIdentity("10.8.0.12"));

        Workspace workspace = service.createWorkspace("Demo", root.toString(), "10.8.0.12", "trace_1234567890abcdef");

        assertThat(workspace.workspaceId().value()).startsWith("wrk_");
        assertThat(workspace.rootPath()).isEqualTo(root.toRealPath().toString());
        assertThat(workspace.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(workspace.traceId()).isEqualTo("trace_1234567890abcdef");
        assertThat(repository.saved).containsExactly(workspace);
    }

    @Test
    void serviceListsAndFindsPersistedWorkspacesThroughRepository() {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        Workspace workspace = repository.save(new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                root.toString(),
                java.time.Instant.parse("2026-06-20T00:00:00Z")));
        WorkspaceApplicationService service = new WorkspaceApplicationService(repository, new WorkspaceFileService());

        PageResponse<Workspace> page = service.listWorkspaces(new PageRequest(1, 20));

        assertThat(page.items()).containsExactly(workspace);
        assertThat(service.getWorkspace(workspace.workspaceId())).isEqualTo(workspace);
    }

    @Test
    void serviceRaisesNotFoundForUnknownWorkspace() {
        WorkspaceApplicationService service = new WorkspaceApplicationService(
                new FakeWorkspaceRepository(),
                new WorkspaceFileService());

        assertThatThrownBy(() -> service.getWorkspace(new WorkspaceId("wrk_missing")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void serviceDelegatesFileOperationsToWorkspaceRoot() {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        Workspace workspace = repository.save(new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                root.toString(),
                java.time.Instant.parse("2026-06-20T00:00:00Z")));
        WorkspaceApplicationService service = new WorkspaceApplicationService(repository, new WorkspaceFileService());

        service.writeFile(workspace.workspaceId(), "notes/todo.txt", "run tests");

        assertThat(service.readFile(workspace.workspaceId(), "notes/todo.txt").content()).isEqualTo("run tests");
        assertThat(service.fileStatus(workspace.workspaceId(), "notes/todo.txt").exists()).isTrue();
        assertThat(service.listFiles(workspace.workspaceId(), "notes"))
                .extracting(FileTreeEntryResponse::name)
                .containsExactly("todo.txt");

        service.deleteFile(workspace.workspaceId(), "notes/todo.txt");

        assertThat(service.fileStatus(workspace.workspaceId(), "notes/todo.txt").exists()).isFalse();
    }

    @Test
    void serviceRenamesWorkspaceFileThroughRegisteredWorkspaceRoot() throws Exception {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        Workspace workspace = repository.save(new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                root.toString(),
                java.time.Instant.parse("2026-06-20T00:00:00Z")));
        WorkspaceApplicationService service = new WorkspaceApplicationService(repository, new WorkspaceFileService());

        service.writeFile(workspace.workspaceId(), "notes/todo.md", "# 内容");
        service.renameFile(workspace.workspaceId(), "notes/todo.md", "design.md");

        assertThat(service.readFile(workspace.workspaceId(), "notes/design.md").content()).isEqualTo("# 内容");
        assertThat(service.fileStatus(workspace.workspaceId(), "notes/todo.md").exists()).isFalse();
    }

    @Test
    void historicalWorkspaceBindingUsesMutationGateAcrossDatabaseSave() {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        java.time.Instant now = java.time.Instant.parse("2026-07-10T00:00:00Z");
        repository.save(new Workspace(
                workspaceId,
                "Demo",
                root.toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                null,
                "trace_old"));
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationContextWorkspaceMutation mutation =
                new ConversationContextWorkspaceMutation(workspaceId, "mutation-workspace");
        Mockito.when(contextStore.beginWorkspaceMutation(workspaceId)).thenReturn(mutation);
        WorkspaceApplicationService service = new WorkspaceApplicationService(
                repository,
                new WorkspaceFileService(),
                new WorkspaceServerIdentity("server-a"),
                ManagedWorkspacePathResolver.legacyOnly(),
                contextStore);

        Workspace resolved = service.requireWorkspaceOnCurrentServer(workspaceId, "trace_bind");

        assertThat(resolved.linuxServerId()).isEqualTo("server-a");
        Mockito.verify(contextStore).beginWorkspaceMutation(workspaceId);
        Mockito.verify(contextStore).completeWorkspaceMutation(mutation);
        assertThat(repository.saved.getLast()).isEqualTo(resolved);
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {

        private final List<Workspace> saved = new ArrayList<>();

        @Override
        public Workspace save(Workspace workspace) {
            saved.add(workspace);
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId workspaceId) {
            return saved.stream().filter(workspace -> workspace.workspaceId().equals(workspaceId)).findFirst();
        }

        @Override
        public PageResponse<Workspace> findPage(PageRequest pageRequest) {
            return new PageResponse<>(saved, pageRequest.page(), pageRequest.size(), saved.size());
        }
    }
}
