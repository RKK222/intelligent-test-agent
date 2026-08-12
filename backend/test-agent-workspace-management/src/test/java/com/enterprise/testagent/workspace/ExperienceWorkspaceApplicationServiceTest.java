package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceBinding;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class ExperienceWorkspaceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T13:00:00Z");
    private static final UserId USER_ID = new UserId("usr_experience");
    private static final String SERVER_ID = "server-a";

    @TempDir
    Path tempDir;

    private final CommonParameterValues parameterValues = Mockito.mock(CommonParameterValues.class);
    private final GitWorkspaceService gitWorkspaceService = Mockito.mock(GitWorkspaceService.class);
    private final FakeWorkspaceRepository workspaceRepository = new FakeWorkspaceRepository();
    private final FakeExperienceWorkspaceRepository experienceRepository =
            new FakeExperienceWorkspaceRepository(workspaceRepository);

    @Test
    void openRegistersOneStableWorkspaceForSameServerAndDirectory() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("shared"));
        configured(root, "${SYS_DATA_ROOT_DIR}/experience");
        ExperienceWorkspaceApplicationService service = service();

        Workspace first = service.open(USER_ID, "trace_first");
        Workspace second = service.open(USER_ID, "trace_second");

        assertThat(first.workspaceId()).isEqualTo(second.workspaceId());
        assertThat(first.workspaceId().value()).startsWith("wrk_exp_");
        assertThat(first.name()).isEqualTo("体验工作区");
        assertThat(first.rootPath()).isEqualTo(root.toRealPath().toString());
        assertThat(first.linuxServerId()).isEqualTo(SERVER_ID);
        assertThat(workspaceRepository.saved).hasSize(1);
        assertThat(experienceRepository.registrations).hasSize(1);
    }

    @Test
    void changingConfiguredDirectoryCreatesNewCurrentWorkspaceAndRejectsOldOne() throws Exception {
        Path firstRoot = Files.createDirectories(tempDir.resolve("first"));
        Path secondRoot = Files.createDirectories(tempDir.resolve("second"));
        configured(firstRoot, firstRoot.toString());
        ExperienceWorkspaceApplicationService service = service();
        Workspace oldWorkspace = service.open(USER_ID, "trace_first");

        configured(secondRoot, secondRoot.toString());
        Workspace currentWorkspace = service.open(USER_ID, "trace_second");

        assertThat(currentWorkspace.workspaceId()).isNotEqualTo(oldWorkspace.workspaceId());
        assertThat(workspaceRepository.findById(oldWorkspace.workspaceId())).isPresent();
        assertThatThrownBy(() -> service.requireAccess(USER_ID, oldWorkspace.workspaceId()))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(service.requireAccess(USER_ID, currentWorkspace.workspaceId()))
                .isEqualTo(currentWorkspace);
    }

    @Test
    void openRepairsBindingWhenConfigurationChangesDuringRegistration() throws Exception {
        Path firstRoot = Files.createDirectories(tempDir.resolve("race-first"));
        Path secondRoot = Files.createDirectories(tempDir.resolve("race-second"));
        when(parameterValues.raw(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(
                        Optional.of(parameter(firstRoot.toString())),
                        Optional.of(parameter(secondRoot.toString())),
                        Optional.of(parameter(secondRoot.toString())),
                        Optional.of(parameter(secondRoot.toString())));
        when(parameterValues.resolvedValue(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(
                        Optional.of(firstRoot.toString()),
                        Optional.of(secondRoot.toString()),
                        Optional.of(secondRoot.toString()),
                        Optional.of(secondRoot.toString()));
        Workspace opened = service().open(USER_ID, "trace_race");

        assertThat(opened.rootPath()).isEqualTo(secondRoot.toRealPath().toString());
        assertThat(experienceRepository.current.workspaceId()).isEqualTo(opened.workspaceId());
        assertThat(experienceRepository.registrations).hasSize(2);
    }

    @Test
    void usersWithOrWithoutApplicationsShareTheSameServerWorkspace() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("shared"));
        configured(root, root.toString());
        ExperienceWorkspaceApplicationService service = service();

        Workspace first = service.open(USER_ID, "trace_first_user");
        Workspace second = service.open(new UserId("usr_with_applications"), "trace_second_user");

        assertThat(second.workspaceId()).isEqualTo(first.workspaceId());
        assertThat(experienceRepository.registrations).hasSize(1);
    }

    @Test
    void unconfiguredDirectoryReturnsSafeUnavailableError() {
        when(parameterValues.raw(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(Optional.of(parameter("UNCONFIGURED")));
        when(parameterValues.resolvedValue(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(Optional.of("UNCONFIGURED"));
        ExperienceWorkspaceApplicationService service = service();

        assertSafeUnavailable(() -> service.open(USER_ID, "trace_unconfigured"));

    }

    @Test
    void openAcceptsPlainDirectoryWithoutGitValidation() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("plain-directory"));
        configured(root, root.toString());

        Workspace workspace = service().open(USER_ID, "trace_plain_directory");

        assertThat(workspace.rootPath()).isEqualTo(root.toRealPath().toString());
        verify(gitWorkspaceService, Mockito.never()).isGitWorkTreeRoot(root.toRealPath());
    }

    @Test
    void startupCreatesDirectoryAndInitializesLocalRepositoryOnce() throws Exception {
        Path root = tempDir.resolve("startup-created");
        configured(root, root.toString());

        service().initializeLocalRepository();

        assertThat(root).isDirectory();
        verify(gitWorkspaceService).initializeLocalRepository(
                Mockito.eq(root.toRealPath()),
                Mockito.eq("README.md"),
                Mockito.contains("不提供远程推送"),
                Mockito.any(GitCommitIdentity.class));
    }

    @Test
    void repeatedAccessRechecksParameterAndDirectoryWithoutGitValidation() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("high-frequency"));
        configured(root, root.toString());
        ExperienceWorkspaceApplicationService service = service();
        Workspace workspace = service.open(USER_ID, "trace_open");

        service.requireAccess(USER_ID, workspace.workspaceId());
        service.requireAccess(USER_ID, workspace.workspaceId());

        verify(parameterValues, times(5)).raw(
                ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                ParameterPlatform.current());
        verify(parameterValues, times(5)).resolvedValue(
                ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                ParameterPlatform.current());
    }

    private ExperienceWorkspaceApplicationService service() {
        return new ExperienceWorkspaceApplicationService(
                experienceRepository,
                workspaceRepository,
                parameterValues,
                new WorkspaceServerIdentity(SERVER_ID),
                gitWorkspaceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void configured(Path resolvedRoot, String rawValue) {
        when(parameterValues.raw(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(Optional.of(parameter(rawValue)));
        when(parameterValues.resolvedValue(
                        ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                        ParameterPlatform.current()))
                .thenReturn(Optional.of(resolvedRoot.toString()));
    }

    private CommonParameter parameter(String value) {
        return new CommonParameter(
                "param_opencode_experience_workspace_dir_all",
                ExperienceWorkspaceApplicationService.PARAM_EXPERIENCE_WORKSPACE_DIR,
                "平台体验工作区目录",
                value,
                ParameterPlatform.ALL,
                true,
                NOW,
                NOW);
    }

    private void assertSafeUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain(tempDir.toString());
                    assertThat(exception.details().values())
                            .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain(tempDir.toString()));
                });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private static final class FakeExperienceWorkspaceRepository implements ExperienceWorkspaceRepository {

        private final FakeWorkspaceRepository workspaceRepository;
        private final List<ExperienceWorkspaceBinding> registrations = new ArrayList<>();
        private ExperienceWorkspaceBinding current;

        private FakeExperienceWorkspaceRepository(FakeWorkspaceRepository workspaceRepository) {
            this.workspaceRepository = workspaceRepository;
        }

        @Override
        public Optional<ExperienceWorkspaceBinding> findCurrentByLinuxServerId(String linuxServerId) {
            return current == null || !current.linuxServerId().equals(linuxServerId)
                    ? Optional.empty()
                    : Optional.of(current);
        }

        @Override
        public boolean registerCurrentIfUnchanged(
                Workspace workspace,
                String configuredParameterValue,
                String traceId,
                Optional<ExperienceWorkspaceBinding> expectedCurrent) {
            if (!expectedCurrent.equals(Optional.ofNullable(current))) {
                return false;
            }
            if (workspaceRepository.findById(workspace.workspaceId()).isEmpty()) {
                workspaceRepository.save(workspace);
            }
            current = new ExperienceWorkspaceBinding(
                    workspace.linuxServerId(),
                    workspace.workspaceId(),
                    configuredParameterValue,
                    traceId,
                    workspace.createdAt(),
                    workspace.updatedAt());
            registrations.add(current);
            return true;
        }
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {

        private final List<Workspace> saved = new ArrayList<>();

        @Override
        public Workspace save(Workspace workspace) {
            saved.removeIf(candidate -> candidate.workspaceId().equals(workspace.workspaceId()));
            saved.add(workspace);
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId workspaceId) {
            return saved.stream().filter(workspace -> workspace.workspaceId().equals(workspaceId)).findFirst();
        }

        @Override
        public PageResponse<Workspace> findPage(PageRequest pageRequest) {
            return new PageResponse<>(List.copyOf(saved), pageRequest.page(), pageRequest.size(), saved.size());
        }
    }
}
