package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.platform.ProtectedAgentMcpController.McpRequest;
import com.enterprise.testagent.api.web.platform.ProtectedAgentMcpController.McpResponse;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentMcpService;
import com.enterprise.testagent.observability.TraceConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ProtectedAgentMcpControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsJsonRpcWireFormatWhileLogSummariesOmitAuthorizationAndFileContent() throws Exception {
        ProtectedAgentMcpService service = mock(ProtectedAgentMcpService.class);
        ProtectedAgentMcpController controller = new ProtectedAgentMcpController(service, objectMapper);
        JsonNode id = objectMapper.readTree("1");
        JsonNode params = objectMapper.readTree(
                "{\"name\":\"write_file\",\"arguments\":{\"path\":\"secret.txt\","
                        + "\"content\":\"file-content-must-not-enter-api-log\"}}");
        JsonNode serviceResponse = objectMapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\","
                        + "\"text\":\"workspace-content-must-not-enter-api-log\"}]}}");
        when(service.handle("Bearer pag_secret", objectMapper.valueToTree(
                        new McpRequest("2.0", id, "tools/call", params)), "trace_mcp_controller"))
                .thenReturn(serviceResponse);
        MockServerWebExchange exchange = exchange("Bearer pag_secret");
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_mcp_controller");

        ResponseEntity<McpResponse> response = controller.invoke(
                        new McpRequest("2.0", id, "tools/call", params), exchange)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.writeValueAsString(response.getBody())).isEqualTo(serviceResponse.toString());
        assertThat(new McpRequest("2.0", id, "tools/call", params).apiRequestLogSummary().toString())
                .contains("tools/call")
                .doesNotContain("pag_secret", "secret.txt", "file-content-must-not-enter-api-log");
        assertThat(response.getBody().apiRequestLogSummary().toString())
                .doesNotContain("workspace-content-must-not-enter-api-log");
        verify(service).handle(
                "Bearer pag_secret",
                objectMapper.valueToTree(new McpRequest("2.0", id, "tools/call", params)),
                "trace_mcp_controller");
    }

    @Test
    void notificationReturnsAcceptedWithoutJsonBody() {
        ProtectedAgentMcpService service = mock(ProtectedAgentMcpService.class);
        ProtectedAgentMcpController controller = new ProtectedAgentMcpController(service, objectMapper);
        McpRequest request = new McpRequest("2.0", null, "notifications/initialized", objectMapper.createObjectNode());
        when(service.handle("Bearer pag_secret", objectMapper.valueToTree(request), "trace_mcp_notification"))
                .thenReturn(null);
        MockServerWebExchange exchange = exchange("Bearer pag_secret");
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_mcp_notification");

        ResponseEntity<McpResponse> response = controller.invoke(request, exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    private MockServerWebExchange exchange(String authorization) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/protected-agent/mcp")
                .header("Authorization", authorization)
                .build());
    }
}
