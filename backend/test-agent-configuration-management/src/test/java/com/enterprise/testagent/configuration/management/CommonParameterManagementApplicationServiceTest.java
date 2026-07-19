package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService.CommonParameterFilter;
import com.enterprise.testagent.configuration.management.CommonParameterManagementResponses.CommonParameterResponse;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterChangeLog;
import com.enterprise.testagent.domain.configuration.CommonParameterChangeLogRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CommonParameterManagementApplicationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-26T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-26T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(UPDATED_AT, ZoneOffset.UTC);

    @Test
    void findFiltersByPlatformAndPaginatesInMemory() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                parameter("param_opencode_workspace_root_linux", "OPENCODE_WORKSPACE_ROOT", "/opt/ws", ParameterPlatform.LINUX),
                parameter("param_opencode_workspace_root_windows", "OPENCODE_WORKSPACE_ROOT", "C:\\ws", ParameterPlatform.WINDOWS),
                parameter("param_opencode_workspace_root_macos", "OPENCODE_WORKSPACE_ROOT", "/tmp/ws", ParameterPlatform.MACOS),
                parameter("param_opencode_public_agent_git_url_all", "OPENCODE_PUBLIC_AGENT_GIT_URL", "https://x.git", ParameterPlatform.ALL),
                parameter("param_opencode_session_dir_linux", "OPENCODE_SESSION_DIR", "/opt/sess", ParameterPlatform.LINUX),
                parameter("param_opencode_session_dir_windows", "OPENCODE_SESSION_DIR", "C:\\sess", ParameterPlatform.WINDOWS)));
        CommonParameterManagementApplicationService service = newService(repository);

        PageResponse<CommonParameterResponse> linuxPage = service.find(
                new CommonParameterFilter(ParameterPlatform.LINUX), new PageRequest(1, 10));

        assertThat(linuxPage.total()).isEqualTo(2);
        assertThat(linuxPage.items()).extracting(CommonParameterResponse::platform).containsOnly("linux");

        PageResponse<CommonParameterResponse> allFirstPage = service.find(new CommonParameterFilter(null), new PageRequest(1, 2));
        PageResponse<CommonParameterResponse> macosPage = service.find(
                new CommonParameterFilter(ParameterPlatform.MACOS), new PageRequest(1, 10));
        assertThat(macosPage.total()).isEqualTo(1);
        assertThat(macosPage.items()).extracting(CommonParameterResponse::platform).containsOnly("macos");

        assertThat(allFirstPage.total()).isEqualTo(6);
        assertThat(allFirstPage.items()).hasSize(2);
        assertThat(allFirstPage.totalPages()).isEqualTo(3L);

        PageResponse<CommonParameterResponse> allSecondPage = service.find(new CommonParameterFilter(null), new PageRequest(2, 2));
        assertThat(allSecondPage.items()).hasSize(2);
    }

    @Test
    void parsePlatformFilterAcceptsMacosAndRejectsUnknownValue() {
        assertThat(CommonParameterFilter.parse("macos").platform()).isEqualTo(ParameterPlatform.MACOS);
        assertThatThrownBy(() -> CommonParameterFilter.parse("solaris"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateValuePersistsAndReturnsReloadedValue() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        CommonParameter existing = parameter("param_opencode_manager_max_processes_all", "OPENCODE_MANAGER_MAX_PROCESSES", "4", ParameterPlatform.ALL, true);
        CommonParameter updated = existing.withValue("6", UPDATED_AT);
        when(repository.findByParameterId("param_opencode_manager_max_processes_all"))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updated));
        when(repository.updateValue(eq("param_opencode_manager_max_processes_all"), eq("6"), eq(UPDATED_AT))).thenReturn(1);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CommonParameterChangeLogRepository changeLogRepository = mock(CommonParameterChangeLogRepository.class);
        CommonParameterManagementApplicationService service = newService(repository, changeLogRepository, publisher);

        CommonParameterResponse response = service.updateValue("param_opencode_manager_max_processes_all", "6", "trace_test", "usr_test", "testuser");

        assertThat(response.parameterValue()).isEqualTo("6");
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        verify(repository).updateValue("param_opencode_manager_max_processes_all", "6", UPDATED_AT);
        ArgumentCaptor<CommonParameterChangeLog> logCaptor = ArgumentCaptor.forClass(CommonParameterChangeLog.class);
        verify(changeLogRepository).save(logCaptor.capture());
        CommonParameterChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.parameterId()).isEqualTo("param_opencode_manager_max_processes_all");
        assertThat(savedLog.oldValue()).isEqualTo("4");
        assertThat(savedLog.newValue()).isEqualTo("6");
        assertThat(savedLog.changedByUserId()).isEqualTo("usr_test");
        assertThat(savedLog.changedByUsername()).isEqualTo("testuser");
        assertThat(savedLog.traceId()).isEqualTo("trace_test");
        assertThat(savedLog.createdAt()).isEqualTo(UPDATED_AT);
        verify(publisher).publishEvent(any(CommonParameterUpdatedEvent.class));
    }

    @Test
    void updateValueThrowsNotFoundWhenParameterMissing() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_missing")).thenReturn(Optional.empty());
        CommonParameterManagementApplicationService service = newService(repository);

        assertThatThrownBy(() -> service.updateValue("param_missing", "/new", "trace_test", "usr_test", "testuser"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void updateValueRejectsBlankValueAsValidationError() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_opencode_manager_max_processes_all"))
                .thenReturn(Optional.of(parameter("param_opencode_manager_max_processes_all", "OPENCODE_MANAGER_MAX_PROCESSES", "4", ParameterPlatform.ALL, true)));
        CommonParameterManagementApplicationService service = newService(repository);

        assertThatThrownBy(() -> service.updateValue("param_opencode_manager_max_processes_all", "  ", "trace_test", "usr_test", "testuser"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateValueRejectsDeploymentPathParametersAsReadonly() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_opencode_public_config_dir_linux"))
                .thenReturn(Optional.of(parameter(
                        "param_opencode_public_config_dir_linux",
                        "OPENCODE_PUBLIC_CONFIG_DIR",
                        "/old",
                        ParameterPlatform.LINUX)));
        CommonParameterChangeLogRepository changeLogRepository = mock(CommonParameterChangeLogRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CommonParameterManagementApplicationService service = newService(repository, changeLogRepository, publisher);

        assertThatThrownBy(() -> service.updateValue(
                        "param_opencode_public_config_dir_linux",
                        "/new",
                        "trace_test",
                        "usr_test",
                        "testuser"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("只读参数");
                });
        verify(repository, org.mockito.Mockito.never()).updateValue(any(), any(), any());
        verify(changeLogRepository, org.mockito.Mockito.never()).save(any());
        verify(publisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    @Test
    void updateValueAllowsAgentGitUrlAsEditableParameter() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        CommonParameter existing = parameter(
                "param_opencode_public_agent_git_url_all",
                "OPENCODE_PUBLIC_AGENT_GIT_URL",
                "https://old.git",
                ParameterPlatform.ALL,
                true);
        CommonParameter updated = existing.withValue("https://new.git", UPDATED_AT);
        when(repository.findByParameterId("param_opencode_public_agent_git_url_all"))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updated));
        when(repository.updateValue(eq("param_opencode_public_agent_git_url_all"), eq("https://new.git"), eq(UPDATED_AT))).thenReturn(1);
        CommonParameterChangeLogRepository changeLogRepository = mock(CommonParameterChangeLogRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CommonParameterManagementApplicationService service = newService(repository, changeLogRepository, publisher);

        CommonParameterResponse response = service.updateValue(
                "param_opencode_public_agent_git_url_all", "https://new.git", "trace_git", "usr_test", "testuser");

        assertThat(response.parameterValue()).isEqualTo("https://new.git");
        assertThat(response.editable()).isTrue();
        verify(repository).updateValue("param_opencode_public_agent_git_url_all", "https://new.git", UPDATED_AT);
        verify(publisher).publishEvent(any(CommonParameterUpdatedEvent.class));
    }

    @Test
    void updateValueNormalizesPositiveNightExecutionCapacity() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        CommonParameter existing = parameter(
                "param_night_execution_slot_capacity_all",
                "NIGHT_EXECUTION_SLOT_CAPACITY",
                "20",
                ParameterPlatform.ALL,
                true);
        CommonParameter updated = existing.withValue("21", UPDATED_AT);
        when(repository.findByParameterId(existing.parameterId()))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updated));
        when(repository.updateValue(existing.parameterId(), "21", UPDATED_AT)).thenReturn(1);
        CommonParameterChangeLogRepository changeLogRepository = mock(CommonParameterChangeLogRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CommonParameterManagementApplicationService service = newService(repository, changeLogRepository, publisher);

        CommonParameterResponse response = service.updateValue(
                existing.parameterId(), " 21 ", "trace_capacity", "usr_test", "testuser");

        assertThat(response.parameterValue()).isEqualTo("21");
        verify(repository).updateValue(existing.parameterId(), "21", UPDATED_AT);
        verify(changeLogRepository).save(any(CommonParameterChangeLog.class));
        verify(publisher).publishEvent(any(CommonParameterUpdatedEvent.class));
    }

    @Test
    void updateValueRejectsInvalidNightExecutionCapacityBeforeWriting() {
        for (String invalidValue : List.of("abc", "0", "-1", "2147483648")) {
            CommonParameterRepository repository = mock(CommonParameterRepository.class);
            CommonParameter existing = parameter(
                    "param_night_execution_slot_capacity_all",
                    "NIGHT_EXECUTION_SLOT_CAPACITY",
                    "20",
                    ParameterPlatform.ALL,
                    true);
            when(repository.findByParameterId(existing.parameterId())).thenReturn(Optional.of(existing));
            CommonParameterChangeLogRepository changeLogRepository = mock(CommonParameterChangeLogRepository.class);
            ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
            CommonParameterManagementApplicationService service = newService(repository, changeLogRepository, publisher);

            assertThatThrownBy(() -> service.updateValue(
                            existing.parameterId(), invalidValue, "trace_capacity", "usr_test", "testuser"))
                    .isInstanceOfSatisfying(PlatformException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(exception.getCause()).isNull();
                    });
            verify(repository, org.mockito.Mockito.never()).updateValue(any(), any(), any());
            verify(changeLogRepository, org.mockito.Mockito.never()).save(any());
            verify(publisher, org.mockito.Mockito.never()).publishEvent(any());
        }
    }

    @Test
    void updateValueThrowsNotFoundWhenRowDisappearsConcurrently() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_opencode_manager_max_processes_all"))
                .thenReturn(Optional.of(parameter("param_opencode_manager_max_processes_all", "OPENCODE_MANAGER_MAX_PROCESSES", "4", ParameterPlatform.ALL, true)));
        when(repository.updateValue(eq("param_opencode_manager_max_processes_all"), eq("6"), eq(UPDATED_AT))).thenReturn(0);
        CommonParameterManagementApplicationService service = newService(repository);

        assertThatThrownBy(() -> service.updateValue("param_opencode_manager_max_processes_all", "6", "trace_test", "usr_test", "testuser"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static CommonParameterManagementApplicationService newService(CommonParameterRepository repository) {
        return newService(repository, mock(CommonParameterChangeLogRepository.class), mock(ApplicationEventPublisher.class));
    }

    private static CommonParameterManagementApplicationService newService(
            CommonParameterRepository repository,
            CommonParameterChangeLogRepository changeLogRepository,
            ApplicationEventPublisher publisher) {
        return new CommonParameterManagementApplicationService(repository, changeLogRepository, publisher, FIXED_CLOCK);
    }

    private static CommonParameter parameter(String parameterId, String english, String value, ParameterPlatform platform) {
        return parameter(parameterId, english, value, platform, false);
    }

    private static CommonParameter parameter(String parameterId, String english, String value, ParameterPlatform platform, boolean editable) {
        return new CommonParameter(parameterId, english, english + "_CN", value, platform, editable, CREATED_AT, CREATED_AT);
    }
}
