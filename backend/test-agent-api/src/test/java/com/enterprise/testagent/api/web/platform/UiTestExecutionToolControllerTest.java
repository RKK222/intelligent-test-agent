package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.uitest.UiTestExecutionClient;
import com.enterprise.testagent.integration.uitest.UiTestExecutionResult;
import com.enterprise.testagent.integration.uitest.UiTestExecutionStatus;
import com.enterprise.testagent.opencode.runtime.process.UiTestExecutionToolTokenService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class UiTestExecutionToolControllerTest {

    private static final String TRACE_ID = "trace_ui_tool_123456";

    @Test
    void validToolCredentialSubmitsOneFourColumnCase() {
        UiTestExecutionToolTokenService tokenService = mock(UiTestExecutionToolTokenService.class);
        when(tokenService.authenticate("Bearer signed-ui-token"))
                .thenReturn(new UserId("usr_ui_tool_123456"));
        UiTestExecutionClient executionClient = mock(UiTestExecutionClient.class);
        when(executionClient.submit(any(), eq(TRACE_ID))).thenReturn(Mono.just(result()));

        client(tokenService, executionClient).post()
                .uri(UiTestExecutionToolTokenService.ENDPOINT_PATH)
                .header("Authorization", "Bearer signed-ui-token")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requestId": "session-1:case-1",
                          "caseName": "登录成功",
                          "testSteps": "点击登录",
                          "testData": "用户名=tester",
                          "expectedResult": "进入首页"
                        }
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.executionId").isEqualTo("uiexec_abc")
                .jsonPath("$.data.status").isEqualTo("RUNNING")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);

        verify(executionClient).submit(any(), eq(TRACE_ID));
    }

    @Test
    void invalidDedicatedCredentialIsRejectedBeforeCallingExternalClient() {
        UiTestExecutionToolTokenService tokenService = mock(UiTestExecutionToolTokenService.class);
        when(tokenService.authenticate(null)).thenThrow(
                new PlatformException(ErrorCode.UNAUTHENTICATED, "UI 测试 Tool 凭据无效或已过期"));
        UiTestExecutionClient executionClient = mock(UiTestExecutionClient.class);

        client(tokenService, executionClient).get()
                .uri(UiTestExecutionToolTokenService.ENDPOINT_PATH + "/uiexec_abc")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);
    }

    @Test
    void blankTestStepsUseUnifiedValidationError() {
        UiTestExecutionToolTokenService tokenService = mock(UiTestExecutionToolTokenService.class);
        UiTestExecutionClient executionClient = mock(UiTestExecutionClient.class);

        client(tokenService, executionClient).post()
                .uri(UiTestExecutionToolTokenService.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"session-1:case-1","testSteps":"   "}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    private WebTestClient client(
            UiTestExecutionToolTokenService tokenService,
            UiTestExecutionClient executionClient) {
        return WebTestClient.bindToController(new UiTestExecutionToolController(tokenService, executionClient))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UiTestExecutionResult result() {
        return new UiTestExecutionResult(
                "uiexec_abc",
                "session-1:case-1",
                "登录成功",
                UiTestExecutionStatus.RUNNING,
                null,
                "",
                List.of(),
                0,
                0,
                null,
                Instant.parse("2026-07-31T00:00:00Z"),
                Instant.parse("2026-07-31T00:00:01Z"),
                null);
    }
}
