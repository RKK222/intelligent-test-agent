package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserWorkspaceQueryServiceTest {

    private static final UserId USER_ID = new UserId("usr_workspace_owner");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_personal_workspace");
    private static final String LOGICAL_ROOT =
            "personalworktree:20260806/usr_workspace_owner/demo/feature_usr_default/F-DEMO/workspace";
    private static final String PHYSICAL_ROOT =
            "/data/.testagent/agent-opencode/workspace/personalworktree/20260806/usr_workspace_owner/demo/feature_usr_default/F-DEMO/workspace";

    @Test
    void resolvesPersonalWorktreeLogicalPathForListAndDetailResponses() {
        Workspace stored = new Workspace(
                WORKSPACE_ID,
                "default",
                LOGICAL_ROOT,
                Instant.parse("2026-08-06T00:00:00Z"));
        UserWorkspaceQueryService service = new UserWorkspaceQueryService(
                new FixedRepository(stored),
                new ManagedWorkspacePathResolver(parameters()));

        PageResponse<Workspace> page = service.listUserWorkspaces(USER_ID, new PageRequest(1, 20));
        Workspace detail = service.requireUserWorkspace(USER_ID, WORKSPACE_ID);

        assertThat(page.items()).singleElement().extracting(Workspace::rootPath).isEqualTo(PHYSICAL_ROOT);
        assertThat(detail.rootPath()).isEqualTo(PHYSICAL_ROOT);
    }

    @Test
    void currentExperienceWorkspaceDetailUsesRealtimePolicyWithoutOrdinaryUserMapping() {
        WorkspaceId experienceId = new WorkspaceId("wrk_exp_current_workspace");
        Workspace experience = new Workspace(
                experienceId,
                "体验工作区",
                "/srv/experience",
                Instant.parse("2026-08-09T00:00:00Z"));
        ExperienceWorkspaceAccessAuthorizer authorizer = mock(ExperienceWorkspaceAccessAuthorizer.class);
        when(authorizer.isExperienceWorkspace(experienceId)).thenReturn(true);
        when(authorizer.requireAccess(USER_ID, experienceId)).thenReturn(experience);
        UserWorkspaceQueryService service = new UserWorkspaceQueryService(
                new FixedRepository(null),
                new ManagedWorkspacePathResolver(parameters()),
                authorizer);

        assertThat(service.requireUserWorkspace(USER_ID, experienceId)).isEqualTo(experience);
        verify(authorizer).requireAccess(USER_ID, experienceId);
    }

    private CommonParameterValues parameters() {
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                if (ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT.equals(englishName)) {
                    return Optional.of("/data/.testagent/agent-opencode/workspace/personalworktree");
                }
                return Optional.empty();
            }

            @Override
            public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
                return resolvedValue(englishName);
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }

    private record FixedRepository(Workspace workspace) implements UserWorkspaceQueryRepository {

        @Override
        public PageResponse<Workspace> findUserWorkspaces(UserId userId, PageRequest pageRequest) {
            List<Workspace> workspaces = workspace == null ? List.of() : List.of(workspace);
            return new PageResponse<>(workspaces, pageRequest.page(), pageRequest.size(), workspaces.size());
        }

        @Override
        public Optional<Workspace> findUserWorkspace(UserId userId, WorkspaceId workspaceId) {
            return workspace != null && workspace.workspaceId().equals(workspaceId)
                    ? Optional.of(workspace)
                    : Optional.empty();
        }
    }
}
