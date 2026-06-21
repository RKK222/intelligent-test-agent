package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceApplicationServiceTest {

    @TempDir
    Path root;

    @Test
    void serviceCreatesWorkspaceWithGeneratedIdAndTraceId() throws Exception {
        FakeWorkspaceRepository repository = new FakeWorkspaceRepository();
        WorkspaceApplicationService service = new WorkspaceApplicationService(repository, new WorkspaceFileService());

        Workspace workspace = service.createWorkspace("Demo", root.toString(), "trace_1234567890abcdef");

        assertThat(workspace.workspaceId().value()).startsWith("wrk_");
        assertThat(workspace.rootPath()).isEqualTo(root.toRealPath().toString());
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
