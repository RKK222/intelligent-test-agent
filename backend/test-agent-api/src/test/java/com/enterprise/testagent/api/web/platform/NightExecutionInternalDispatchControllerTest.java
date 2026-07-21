package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchService;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchStatus;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** 验证内部入口只使用标准 XXL token，且请求体不携带完整任务输入。 */
class NightExecutionInternalDispatchControllerTest {

    @Test
    void acceptsValidXxlTokenWithoutUserPrincipal() {
        NightExecutionDispatchService service = mock(NightExecutionDispatchService.class);
        NightExecutionTaskId taskId = new NightExecutionTaskId("net_internal_dispatch");
        when(service.dispatchBatch(eq("linux-a"), eq(List.of(taskId)), eq("trace_internal_dispatch")))
                .thenReturn(Mono.just(new NightExecutionDispatchBatchResult(
                        "linux-a",
                        List.of(new NightExecutionDispatchResult(
                                taskId, NightExecutionDispatchStatus.STARTED, "run_internal", null)))));
        WebTestClient client = client(service, "xxl-secret");

        client.post().uri(NightExecutionInternalDispatchController.PATH)
                .header("X-Trace-Id", "trace_internal_dispatch")
                .header(NightExecutionInternalDispatchController.ACCESS_TOKEN_HEADER, "xxl-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"linuxServerId":"linux-a","taskIds":["net_internal_dispatch"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.results[0].status").isEqualTo("STARTED")
                .jsonPath("$.data.results[0].runId").isEqualTo("run_internal")
                .jsonPath("$.data.prompt").doesNotExist();
    }

    @Test
    void rejectsMismatchedXxlToken() {
        WebTestClient client = client(mock(NightExecutionDispatchService.class), "xxl-secret");

        client.post().uri(NightExecutionInternalDispatchController.PATH)
                .header(NightExecutionInternalDispatchController.ACCESS_TOKEN_HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"linuxServerId":"linux-a","taskIds":["net_internal_dispatch"]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void rejectsMoreThanFiftyTaskIdsBeforeCallingService() {
        NightExecutionDispatchService service = mock(NightExecutionDispatchService.class);
        WebTestClient client = client(service, "xxl-secret");
        List<String> taskIds = IntStream.range(0, 51)
                .mapToObj(index -> "net_internal_" + index)
                .toList();

        client.post().uri(NightExecutionInternalDispatchController.PATH)
                .header(NightExecutionInternalDispatchController.ACCESS_TOKEN_HEADER, "xxl-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("linuxServerId", "linux-a", "taskIds", taskIds))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingTokenWhenXxlTokenIsUnconfigured() {
        NightExecutionDispatchService service = mock(NightExecutionDispatchService.class);
        WebTestClient client = client(service, "");

        client.post().uri(NightExecutionInternalDispatchController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"linuxServerId":"linux-a","taskIds":["net_internal_dispatch"]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        verifyNoInteractions(service);
    }

    private WebTestClient client(NightExecutionDispatchService service, String token) {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setAccessToken(token);
        return WebTestClient.bindToController(
                        new NightExecutionInternalDispatchController(service, properties))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
