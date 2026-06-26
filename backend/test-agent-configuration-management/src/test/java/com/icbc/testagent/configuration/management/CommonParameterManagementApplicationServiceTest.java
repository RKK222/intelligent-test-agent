package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.configuration.management.CommonParameterManagementApplicationService.CommonParameterFilter;
import com.icbc.testagent.configuration.management.CommonParameterManagementResponses.CommonParameterResponse;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
                parameter("param_opencode_public_agent_git_url_all", "OPENCODE_PUBLIC_AGENT_GIT_URL", "https://x.git", ParameterPlatform.ALL),
                parameter("param_opencode_session_dir_linux", "OPENCODE_SESSION_DIR", "/opt/sess", ParameterPlatform.LINUX),
                parameter("param_opencode_session_dir_windows", "OPENCODE_SESSION_DIR", "C:\\sess", ParameterPlatform.WINDOWS)));
        CommonParameterManagementApplicationService service = new CommonParameterManagementApplicationService(repository, FIXED_CLOCK);

        PageResponse<CommonParameterResponse> linuxPage = service.find(
                new CommonParameterFilter(ParameterPlatform.LINUX), new PageRequest(1, 10));

        assertThat(linuxPage.total()).isEqualTo(2);
        assertThat(linuxPage.items()).extracting(CommonParameterResponse::platform).containsOnly("linux");

        PageResponse<CommonParameterResponse> allFirstPage = service.find(new CommonParameterFilter(null), new PageRequest(1, 2));
        assertThat(allFirstPage.total()).isEqualTo(5);
        assertThat(allFirstPage.items()).hasSize(2);
        assertThat(allFirstPage.totalPages()).isEqualTo(3L);

        PageResponse<CommonParameterResponse> allSecondPage = service.find(new CommonParameterFilter(null), new PageRequest(2, 2));
        assertThat(allSecondPage.items()).hasSize(2);
    }

    @Test
    void parsePlatformFilterRejectsUnknownValue() {
        assertThatThrownBy(() -> CommonParameterFilter.parse("macos"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateValuePersistsAndReturnsReloadedValue() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        CommonParameter existing = parameter("param_opencode_workspace_root_linux", "OPENCODE_WORKSPACE_ROOT", "/old", ParameterPlatform.LINUX);
        CommonParameter updated = existing.withValue("/new", UPDATED_AT);
        when(repository.findByParameterId("param_opencode_workspace_root_linux"))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updated));
        when(repository.updateValue(eq("param_opencode_workspace_root_linux"), eq("/new"), eq(UPDATED_AT))).thenReturn(1);
        CommonParameterManagementApplicationService service = new CommonParameterManagementApplicationService(repository, FIXED_CLOCK);

        CommonParameterResponse response = service.updateValue("param_opencode_workspace_root_linux", "/new", "trace_test");

        assertThat(response.parameterValue()).isEqualTo("/new");
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        verify(repository).updateValue("param_opencode_workspace_root_linux", "/new", UPDATED_AT);
    }

    @Test
    void updateValueThrowsNotFoundWhenParameterMissing() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_missing")).thenReturn(Optional.empty());
        CommonParameterManagementApplicationService service = new CommonParameterManagementApplicationService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.updateValue("param_missing", "/new", "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void updateValueRejectsBlankValueAsValidationError() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_opencode_workspace_root_linux"))
                .thenReturn(Optional.of(parameter("param_opencode_workspace_root_linux", "OPENCODE_WORKSPACE_ROOT", "/old", ParameterPlatform.LINUX)));
        CommonParameterManagementApplicationService service = new CommonParameterManagementApplicationService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.updateValue("param_opencode_workspace_root_linux", "  ", "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateValueThrowsNotFoundWhenRowDisappearsConcurrently() {
        CommonParameterRepository repository = mock(CommonParameterRepository.class);
        when(repository.findByParameterId("param_opencode_workspace_root_linux"))
                .thenReturn(Optional.of(parameter("param_opencode_workspace_root_linux", "OPENCODE_WORKSPACE_ROOT", "/old", ParameterPlatform.LINUX)));
        when(repository.updateValue(eq("param_opencode_workspace_root_linux"), eq("/new"), eq(UPDATED_AT))).thenReturn(0);
        CommonParameterManagementApplicationService service = new CommonParameterManagementApplicationService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.updateValue("param_opencode_workspace_root_linux", "/new", "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static CommonParameter parameter(String parameterId, String english, String value, ParameterPlatform platform) {
        return new CommonParameter(parameterId, english, english + "_CN", value, platform, CREATED_AT, CREATED_AT);
    }
}
