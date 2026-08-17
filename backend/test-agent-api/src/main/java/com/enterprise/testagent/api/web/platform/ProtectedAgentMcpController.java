package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.api.web.common.ApiRequestLogSummary;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentExecutionService;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentMcpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 服务器 OpenCode 专用的 stateless Streamable HTTP MCP 入口，不接受平台登录 Token。 */
@RestController
public class ProtectedAgentMcpController {

    private final ProtectedAgentMcpService mcpService;
    private final ObjectMapper objectMapper;

    public ProtectedAgentMcpController(ProtectedAgentMcpService mcpService, ObjectMapper objectMapper) {
        this.mcpService = mcpService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = ProtectedAgentExecutionService.MCP_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<McpResponse>> invoke(
            @RequestBody McpRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return Mono.fromCallable(() -> Optional.ofNullable(
                        mcpService.handle(authorization, objectMapper.valueToTree(request), traceId)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> response.isEmpty()
                        ? ResponseEntity.status(HttpStatus.ACCEPTED).<McpResponse>build()
                        : ResponseEntity.ok(new McpResponse(response.orElseThrow())));
    }

    /** JSON-RPC 请求保持开放 params，同时只把 method/id 摘要交给通用 API 日志。 */
    public record McpRequest(String jsonrpc, JsonNode id, String method, JsonNode params)
            implements ApiRequestLogSummary {

        @Override
        public Object apiRequestLogSummary() {
            return java.util.Map.of(
                    "jsonrpc", jsonrpc == null ? "" : jsonrpc,
                    "id", id == null || id.isNull() ? "notification" : id.asText(),
                    "method", method == null ? "" : method,
                    "paramsPresent", params != null && !params.isNull());
        }
    }

    /** @JsonValue 保持 MCP wire body 不增加 envelope；日志切面则只读取安全摘要。 */
    public record McpResponse(JsonNode body) implements ApiRequestLogSummary {

        @JsonValue
        public JsonNode jsonValue() {
            return body;
        }

        @Override
        public Object apiRequestLogSummary() {
            return java.util.Map.of(
                    "jsonrpc", body.path("jsonrpc").asText("2.0"),
                    "id", body.path("id").isNull() ? "notification" : body.path("id").asText(),
                    "hasResult", body.has("result"),
                    "hasError", body.has("error"));
        }
    }
}
