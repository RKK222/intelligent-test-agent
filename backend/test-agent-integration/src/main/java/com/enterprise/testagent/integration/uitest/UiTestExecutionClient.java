package com.enterprise.testagent.integration.uitest;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/** 受控调用独立 uitest6 平台，不把外部凭据下发给 OpenCode Tool。 */
@Service
public class UiTestExecutionClient {

    private static final String EXECUTIONS_PATH = "/api/integration/v1/ui-executions";

    private final UiTestExecutionSettings settings;
    private final WebClient webClient;

    @Autowired
    public UiTestExecutionClient(UiTestExecutionSettings settings, WebClient.Builder builder) {
        this(settings, builder.build());
    }

    UiTestExecutionClient(UiTestExecutionSettings settings, WebClient webClient) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
    }

    /** 提交一行四列案例；requestId 由 uitest6 用于保证只创建一次。 */
    public Mono<UiTestExecutionResult> submit(UiTestExecutionCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        ExternalSubmitRequest body = new ExternalSubmitRequest(
                command.requestId(),
                command.caseName(),
                command.testSteps(),
                command.testData(),
                command.expectedResult(),
                settings.maxSteps());
        return exchange(webClient.post()
                .uri(target(EXECUTIONS_PATH))
                .headers(headers -> applyHeaders(headers, traceId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body));
    }

    /** 查询既有执行，不创建第二次 UI 自动化。 */
    public Mono<UiTestExecutionResult> get(String executionId, String traceId) {
        String normalized = executionId == null ? "" : executionId.trim();
        if (!normalized.matches("uiexec_[A-Za-z0-9]+")) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "UI 执行 ID 格式无效"));
        }
        return exchange(webClient.get()
                .uri(target(EXECUTIONS_PATH + "/" + normalized))
                .headers(headers -> applyHeaders(headers, traceId)));
    }

    private Mono<UiTestExecutionResult> exchange(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(this::decode)
                .timeout(settings.requestTimeout())
                .onErrorMap(TimeoutException.class, ignored ->
                        new PlatformException(ErrorCode.UI_TEST_TIMEOUT))
                .onErrorMap(WebClientRequestException.class, exception ->
                        new PlatformException(
                                ErrorCode.UI_TEST_UNAVAILABLE,
                                ErrorCode.UI_TEST_UNAVAILABLE.defaultMessage(),
                                java.util.Map.of(),
                                exception))
                .onErrorMap(
                        exception -> !(exception instanceof PlatformException),
                        exception -> new PlatformException(
                                ErrorCode.UI_TEST_BAD_GATEWAY,
                                ErrorCode.UI_TEST_BAD_GATEWAY.defaultMessage(),
                                java.util.Map.of(),
                                exception));
    }

    private Mono<UiTestExecutionResult> decode(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(ExternalExecutionResponse.class)
                    .switchIfEmpty(Mono.error(new PlatformException(
                            ErrorCode.UI_TEST_BAD_GATEWAY,
                            "UI 自动化平台返回空响应")))
                    .map(this::toResult);
        }
        PlatformException failure;
        int status = response.statusCode().value();
        if (status == 409) {
            failure = new PlatformException(ErrorCode.CONFLICT, "UI 测试执行幂等键冲突");
        } else if (status == 401 || status == 403) {
            failure = new PlatformException(ErrorCode.UI_TEST_UNAVAILABLE, "UI 自动化平台鉴权失败");
        } else if (status == 404) {
            failure = new PlatformException(ErrorCode.UI_TEST_BAD_GATEWAY, "UI 自动化执行不存在或已失效");
        } else {
            failure = new PlatformException(ErrorCode.UI_TEST_BAD_GATEWAY);
        }
        return response.releaseBody().then(Mono.error(failure));
    }

    private UiTestExecutionResult toResult(ExternalExecutionResponse source) {
        UiTestExecutionStatus status;
        try {
            status = UiTestExecutionStatus.valueOf(source.status());
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.UI_TEST_BAD_GATEWAY, "UI 自动化平台返回未知状态");
        }
        return new UiTestExecutionResult(
                source.executionId(),
                source.requestId(),
                source.caseName(),
                status,
                source.success(),
                source.message(),
                source.errors(),
                source.stepCount(),
                source.durationSeconds(),
                resolveReportUrl(source.reportUrl()),
                source.createdAt(),
                source.startedAt(),
                source.completedAt());
    }

    private String resolveReportUrl(String reportUrl) {
        if (reportUrl == null || reportUrl.isBlank()) {
            return null;
        }
        try {
            return settings.requireBaseUri().resolve(reportUrl).toString();
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.UI_TEST_BAD_GATEWAY, "UI 自动化平台报告地址无效");
        }
    }

    private URI target(String path) {
        return URI.create(settings.requireBaseUri() + path);
    }

    private void applyHeaders(HttpHeaders headers, String traceId) {
        headers.setBearerAuth(settings.requireToken());
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId);
        }
    }

    private record ExternalSubmitRequest(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("case_name") String caseName,
            @JsonProperty("test_steps") String testSteps,
            @JsonProperty("test_data") String testData,
            @JsonProperty("expected_result") String expectedResult,
            @JsonProperty("max_steps") int maxSteps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalExecutionResponse(
            @JsonProperty("execution_id") String executionId,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("case_name") String caseName,
            String status,
            Boolean success,
            String message,
            List<String> errors,
            @JsonProperty("step_count") int stepCount,
            @JsonProperty("duration_seconds") double durationSeconds,
            @JsonProperty("report_url") String reportUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("completed_at") Instant completedAt) {
    }
}
