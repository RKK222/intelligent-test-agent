package com.example.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.testagent.api.web.common.TraceIdWebFilter;
import com.example.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class PlatformOpencodeRuntimeControllerTest {

    @Test
    void runtimeControllerListsAgentsThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        WebTestClient client = WebTestClient.bindToController(new PlatformOpencodeRuntimeController(service))
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
        WebTestClient client = WebTestClient.bindToController(new PlatformOpencodeRuntimeController(service))
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
        WebTestClient client = WebTestClient.bindToController(new PlatformOpencodeRuntimeController(service))
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

    @Test
    void runtimeControllerSharesSessionThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.shareSession(eq("ses_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("url", "https://opencode.ai/s/abc"));
        WebTestClient client = WebTestClient.bindToController(new PlatformOpencodeRuntimeController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/sessions/ses_1234567890abcdef/share")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.url").isEqualTo("https://opencode.ai/s/abc");
    }
}
