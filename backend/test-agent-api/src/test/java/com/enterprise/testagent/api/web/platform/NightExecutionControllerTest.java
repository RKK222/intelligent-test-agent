package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionCreateCommand;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionTaskApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 夜间执行 HTTP 鉴权、输入映射和安全响应契约。 */
class NightExecutionControllerTest {

    private static final UserId USER_ID = new UserId("usr_night_controller");
    private static final String TRACE_ID = "trace_night_controller";
    private static final Instant NOW = Instant.parse("2026-07-18T04:00:00Z");
    private static final Instant SLOT_START = Instant.parse("2026-07-18T13:15:00Z");

    @Test
    void rejectsUnauthenticatedSlotQuery() {
        WebTestClient.bindToController(new NightExecutionController(mock(NightExecutionTaskApplicationService.class)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get().uri("/api/internal/platform/opencode-runtime/night-execution/slots")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void createsTaskForAuthenticatedUserWithoutReturningFullInput() {
        NightExecutionTaskApplicationService service = mock(NightExecutionTaskApplicationService.class);
        when(service.create(eq(USER_ID), eq(false), any(NightExecutionCreateCommand.class), eq(TRACE_ID)))
                .thenReturn(task());
        WebTestClient client = authenticatedClient(service);

        client.post().uri("/api/internal/platform/opencode-runtime/night-execution/tasks")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "clientRequestId":"task-request-1",
                          "workspaceId":"wrk_night_controller",
                          "sessionTitle":"夜间回归",
                          "prompt":"不能回传的完整输入",
                          "slotStart":"2026-07-18T13:15:00Z"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskId").isEqualTo("net_night_controller")
                .jsonPath("$.data.scheduleMode").isEqualTo("NIGHT_WINDOW")
                .jsonPath("$.data.contentPreview").isEqualTo("安全预览")
                .jsonPath("$.data.runInputJson").doesNotExist()
                .jsonPath("$.data.prompt").doesNotExist();

        verify(service).create(eq(USER_ID), eq(false),
                argThat(command -> command.scheduleMode() == NightExecutionScheduleMode.NIGHT_WINDOW),
                eq(TRACE_ID));
    }

    @Test
    void passesSuperAdminFactAndCustomModeToApplicationService() {
        NightExecutionTaskApplicationService service = mock(NightExecutionTaskApplicationService.class);
        when(service.create(eq(USER_ID), eq(true), any(NightExecutionCreateCommand.class), eq(TRACE_ID)))
                .thenReturn(task());
        WebTestClient client = authenticatedClient(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post().uri("/api/internal/platform/opencode-runtime/night-execution/tasks")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "clientRequestId":"task-request-1",
                          "workspaceId":"wrk_night_controller",
                          "prompt":"执行白天验证",
                          "scheduleMode":"ADMIN_CUSTOM",
                          "slotStart":"2026-07-18T04:01:00Z"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).create(eq(USER_ID), eq(true),
                argThat(command -> command.scheduleMode() == NightExecutionScheduleMode.ADMIN_CUSTOM),
                eq(TRACE_ID));
    }

    @Test
    void rejectsForgedCustomModeWithUnifiedForbiddenResponse() {
        NightExecutionTaskApplicationService service = mock(NightExecutionTaskApplicationService.class);
        when(service.create(
                        eq(USER_ID),
                        eq(false),
                        argThat(command -> command.scheduleMode() == NightExecutionScheduleMode.ADMIN_CUSTOM),
                        eq(TRACE_ID)))
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "仅超级管理员可使用测试定时"));
        WebTestClient client = authenticatedClient(service);

        client.post().uri("/api/internal/platform/opencode-runtime/night-execution/tasks")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "clientRequestId":"task-request-forged",
                          "workspaceId":"wrk_night_controller",
                          "prompt":"伪造测试定时",
                          "scheduleMode":"ADMIN_CUSTOM",
                          "slotStart":"2026-07-18T04:01:00Z"
                        }
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);
    }

    private static WebTestClient authenticatedClient(NightExecutionTaskApplicationService service) {
        return authenticatedClient(service, List.of(Dictionary.ROLE_APP_ADMIN));
    }

    private static WebTestClient authenticatedClient(
            NightExecutionTaskApplicationService service,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "night-user", "night-user", roles,
                NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new NightExecutionController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static NightExecutionTask task() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_night_controller"), USER_ID,
                new SessionId("ses_night_controller"), new WorkspaceId("wrk_night_controller"),
                "task-request-1", "夜间回归", "安全预览", "{\"prompt\":\"secret\"}",
                NightExecutionTaskStatus.SCHEDULED, SLOT_START, SLOT_START.plusSeconds(900),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night", null, null, 0,
                true, null, null, null, null, null, TRACE_ID, NOW, NOW);
    }
}
