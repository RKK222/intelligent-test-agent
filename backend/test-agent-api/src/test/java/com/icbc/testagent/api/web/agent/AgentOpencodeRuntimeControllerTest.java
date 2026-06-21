package com.icbc.testagent.api.web.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AgentOpencodeRuntimeControllerTest {

    @Test
    void agentControllerExposesOpencodeOriginalAgentPath() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        when(service.withAgent(eq("opencode"), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        WebTestClient client = WebTestClient.bindToController(new AgentOpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/internal/agent/opencode/api/agent?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("build");
    }

    @Test
    void agentControllerRepliesToPermissionThroughOpencodePath() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.replyPermission(
                        eq("ses_1234567890abcdef"),
                        eq("req_1"),
                        eq(Map.of("decision", "once")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("accepted", true));
        when(service.withAgent(eq("opencode"), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        WebTestClient client = WebTestClient.bindToController(new AgentOpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/internal/agent/opencode/permission/req_1/reply?sessionId=ses_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"decision":"once"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.accepted").isEqualTo(true);
    }
}
