package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.session.BatchSessionApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 批量单项 Session HTTP 契约与认证归因测试。 */
class BatchSessionControllerTest {

    private static final UserId USER = new UserId("usr_batch_controller");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_batch_controller");
    private static final String TRACE_ID = "trace_batch_controller";

    @Test
    void createsBatchItemSessionForAuthenticatedUser() {
        BatchSessionApplicationService service = mock(BatchSessionApplicationService.class);
        when(service.create(eq(USER), eq(WORKSPACE), eq("需求一 登录 测试案例"),
                argThat(context -> context.batchId().equals("batch_01")
                        && context.itemRequestId().equals("batch_item_01")), eq(TRACE_ID)))
                .thenReturn(session());

        authenticatedClient(service).post()
                .uri("/api/internal/platform/opencode-runtime/sessions/batch-items")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "workspaceId":"wrk_batch_controller",
                          "title":"需求一 登录 测试案例",
                          "batchContext":{"batchId":"batch_01","itemRequestId":"batch_item_01"}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sessionId").isEqualTo("ses_batch_controller")
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_batch_controller");

        verify(service).create(eq(USER), eq(WORKSPACE), eq("需求一 登录 测试案例"),
                argThat(context -> context.batchId().equals("batch_01")
                        && context.itemRequestId().equals("batch_item_01")), eq(TRACE_ID));
    }

    @Test
    void rejectsMissingBatchContextBeforeCallingService() {
        authenticatedClient(mock(BatchSessionApplicationService.class)).post()
                .uri("/api/internal/platform/opencode-runtime/sessions/batch-items")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"workspaceId\":\"wrk_batch_controller\",\"title\":\"批量测试\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    private static WebTestClient authenticatedClient(BatchSessionApplicationService service) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER, "batch-user", "batch-user", List.of(), now, now.plusSeconds(3600));
        return WebTestClient.bindToController(new BatchSessionController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Session session() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return new Session(new SessionId("ses_batch_controller"), WORKSPACE,
                "需求一 登录 测试案例", SessionStatus.ACTIVE, now, now, TRACE_ID)
                .withSource(ConversationSourceType.MANUAL, null, USER);
    }
}
