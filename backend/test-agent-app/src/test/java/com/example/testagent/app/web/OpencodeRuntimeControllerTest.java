package com.example.testagent.app.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.testagent.app.runtime.OpencodeRuntimeApplicationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class OpencodeRuntimeControllerTest {

    @Test
    void runtimeControllerListsAgentsThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        WebTestClient client = WebTestClient.bindToController(new OpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/agents?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("build");
    }

    @Test
    void runtimeControllerRepliesToPermissionThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.replyPermission(
                        eq("ses_1234567890abcdef"),
                        eq("req_1"),
                        eq(Map.of("decision", "once")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("accepted", true));
        WebTestClient client = WebTestClient.bindToController(new OpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/sessions/ses_1234567890abcdef/permissions/req_1/reply")
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

    @Test
    void runtimeControllerListsMcpToolsThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.mcpTools(
                        eq("wrk_1234567890abcdef"),
                        eq("anthropic"),
                        eq("claude-sonnet"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "bash")));
        WebTestClient client = WebTestClient.bindToController(new OpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/mcp/tools?workspaceId=wrk_1234567890abcdef&provider=anthropic&model=claude-sonnet")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("bash");
    }
}
