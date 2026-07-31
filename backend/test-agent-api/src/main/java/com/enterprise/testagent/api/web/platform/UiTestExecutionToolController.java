package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.integration.uitest.UiTestExecutionClient;
import com.enterprise.testagent.integration.uitest.UiTestExecutionCommand;
import com.enterprise.testagent.integration.uitest.UiTestExecutionResult;
import com.enterprise.testagent.opencode.runtime.process.UiTestExecutionToolTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 仅供公共 UI 执行子 agent Tool 回调的同节点桥接入口。 */
@RestController
public class UiTestExecutionToolController {

    private final UiTestExecutionToolTokenService tokenService;
    private final UiTestExecutionClient client;

    public UiTestExecutionToolController(
            UiTestExecutionToolTokenService tokenService,
            UiTestExecutionClient client) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /** 接收一行四列案例并只提交一次，202 不表示 UI 自动化已经完成。 */
    @PostMapping(UiTestExecutionToolTokenService.ENDPOINT_PATH)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<UiTestExecutionResult>> submit(
            @Valid @RequestBody UiTestExecutionRequest request,
            ServerWebExchange exchange) {
        authenticate(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return client.submit(new UiTestExecutionCommand(
                        request.requestId(),
                        request.caseName(),
                        request.testSteps(),
                        request.testData(),
                        request.expectedResult()), traceId)
                .map(result -> ApiResponse.ok(result, traceId));
    }

    /** 查询执行状态；轮询不会再次提交浏览器任务。 */
    @GetMapping(UiTestExecutionToolTokenService.ENDPOINT_PATH + "/{executionId}")
    public Mono<ApiResponse<UiTestExecutionResult>> get(
            @PathVariable String executionId,
            ServerWebExchange exchange) {
        authenticate(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return client.get(executionId, traceId)
                .map(result -> ApiResponse.ok(result, traceId));
    }

    private void authenticate(ServerWebExchange exchange) {
        tokenService.authenticate(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    /** Tool 只暴露四列案例和幂等键，不允许选择外部地址、Token 或执行引擎参数。 */
    public record UiTestExecutionRequest(
            @NotBlank
            @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9_.:-]+")
            String requestId,
            @Size(max = 500) String caseName,
            @NotBlank @Size(max = 20_000) String testSteps,
            @Size(max = 20_000) String testData,
            @Size(max = 20_000) String expectedResult) {
    }
}
