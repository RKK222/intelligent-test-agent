package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
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
                mock(WorkspaceRepository.class),
                mock(ConversationWorkspaceAccessAuthorizer.class),
                new ManagedWorkspacePathResolver(parameters()));

        PageResponse<Workspace> page = service.listUserWorkspaces(USER_ID, new PageRequest(1, 20));
        Workspace detail = service.requireUserWorkspace(USER_ID, WORKSPACE_ID);

        assertThat(page.items()).singleElement().extracting(Workspace::rootPath).isEqualTo(PHYSICAL_ROOT);
        assertThat(detail.rootPath()).isEqualTo(PHYSICAL_ROOT);
    }

    @Test
    void resolvesAuthorizedAppSourceWorkspaceBeforeItHasAnySessionReference() {
        Workspace source = new Workspace(
                new WorkspaceId("wrk_source_workspace"),
                "application-code",
                "appsource:application-code",
                WorkspaceStatus.ACTIVE,
                Instant.parse("2026-08-11T00:00:00Z"),
                Instant.parse("2026-08-11T00:01:00Z"),
                "server-a",
                "trace_source");
        UserWorkspaceQueryRepository userWorkspaces = mock(UserWorkspaceQueryRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ConversationWorkspaceAccessAuthorizer authorizer = mock(ConversationWorkspaceAccessAuthorizer.class);
        when(userWorkspaces.findUserWorkspace(USER_ID, source.workspaceId())).thenReturn(Optional.empty());
        when(authorizer.requireClassifiedFileAccess(USER_ID, source.workspaceId(), true))
                .thenReturn(ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE);
        when(workspaces.findById(source.workspaceId())).thenReturn(Optional.of(source));
        UserWorkspaceQueryService service = new UserWorkspaceQueryService(
                userWorkspaces,
                workspaces,
                authorizer,
                new ManagedWorkspacePathResolver(appSourceParameters()));

        Workspace detail = service.requireUserWorkspace(USER_ID, source.workspaceId());

        assertThat(detail.workspaceId()).isEqualTo(source.workspaceId());
        assertThat(detail.rootPath()).isEqualTo("/data/app-source/application-code");
    }

    @Test
    void doesNotExposeOtherAuthorizedWorkspaceKindsThroughTheAppSourceFallback() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_application_version");
        UserWorkspaceQueryRepository userWorkspaces = mock(UserWorkspaceQueryRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ConversationWorkspaceAccessAuthorizer authorizer = mock(ConversationWorkspaceAccessAuthorizer.class);
        when(userWorkspaces.findUserWorkspace(USER_ID, workspaceId)).thenReturn(Optional.empty());
        when(authorizer.requireClassifiedFileAccess(USER_ID, workspaceId, true))
                .thenReturn(ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.STANDARD);
        UserWorkspaceQueryService service = new UserWorkspaceQueryService(
                userWorkspaces,
                workspaces,
                authorizer,
                new ManagedWorkspacePathResolver(parameters()));

        assertThatThrownBy(() -> service.requireUserWorkspace(USER_ID, workspaceId))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
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

    private CommonParameterValues appSourceParameters() {
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                if (ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_SOURCE_ROOT.equals(englishName)) {
                    return Optional.of("/data/app-source");
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
            return new PageResponse<>(List.of(workspace), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public Optional<Workspace> findUserWorkspace(UserId userId, WorkspaceId workspaceId) {
            return workspace.workspaceId().equals(workspaceId) ? Optional.of(workspace) : Optional.empty();
        }
    }
}
