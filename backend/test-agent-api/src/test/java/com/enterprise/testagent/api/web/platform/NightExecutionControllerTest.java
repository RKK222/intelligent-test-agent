package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
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
        when(service.create(eq(USER_ID), any(NightExecutionCreateCommand.class), eq(TRACE_ID)))
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
                .jsonPath("$.data.contentPreview").isEqualTo("安全预览")
                .jsonPath("$.data.runInputJson").doesNotExist()
                .jsonPath("$.data.prompt").doesNotExist();
    }

    private static WebTestClient authenticatedClient(NightExecutionTaskApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "night-user", "night-user", List.of("APP_ADMIN"),
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
