package com.example.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
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
