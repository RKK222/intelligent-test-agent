package com.enterprise.testagent.integration.tcds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

/** 锁定统一部署地址、固定头、服务端补齐字段和 TCDS 业务错误收敛。 */
class TcdsCaseMaintenanceServiceTest {

    private static final String TASK_TYPES_RESPONSE = """
            {"code":0,"msg":"请求成功","data":{"subItemTypes":[
              {"name":"探索性测试任务","value":"12"},
              {"name":"功能测试任务","value":"3"}
            ]}}
            """;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsTaskTypesFromConfiguredGetEndpointWithToolId() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"code":0,"msg":"请求成功","data":{"subItemTypes":[
                  {"property":"需求子条目测试任务任务类型","name":"准入测试任务","value":"5"},
                  {"property":"需求子条目测试任务任务类型","name":"功能测试任务","value":"3"}
                ]}}
                """);
        TcdsCaseMaintenanceService service = service(httpClient);

        assertThat(service.getTaskTypes("trace_tcds_task_types")).containsExactly(
                new TcdsTaskTypeOption("准入测试任务", "5"),
                new TcdsTaskTypeOption("功能测试任务", "3"));
        assertThat(httpClient.request.uri()).isEqualTo(
                URI.create("http://tcds-prod.sdc.icbc:9080/task/getTaskTypes"));
        assertThat(httpClient.request.method()).isEqualTo("GET");
        assertThat(httpClient.request.bodyPublisher()).isEmpty();
        assertThat(httpClient.request.headers().firstValue("toolId"))
                .contains("66f36bfa5c1c6105572b0118880261d6");
    }

    @Test
    void rejectsInvalidOrDuplicateTaskTypeResponses() {
        for (String response : List.of(
                "{\"code\":3,\"msg\":\"失败\"}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"准入测试任务\",\"value\":\"5\"},{\"name\":\"重复\",\"value\":\"5\"}]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"准入\",\"value\":\"5\"}]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"测试任务\",\"value\":\"5\"}]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"准入测试任务\",\"value\":\"5\"},{\"name\":\"准入测试任务\",\"value\":\"6\"}]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"非法\\n名称\",\"value\":\"5\"}]}}",
                "{\"code\":0,\"data\":{\"subItemTypes\":[{\"name\":\"缺少值\"}]}}")) {
            TcdsCaseMaintenanceService service = service(new RecordingHttpClient(200, response));

            assertThatThrownBy(() -> service.getTaskTypes("trace_tcds_task_types"))
                    .isInstanceOfSatisfying(PlatformException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE));
        }
    }

    @Test
    void buildsFixedProductionRequestWithDynamicCommaSeparatedTaskTypeNames() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                200,
                TASK_TYPES_RESPONSE,
                "{\"code\":0,\"msg\":\"请求成功\"}");
        TcdsCaseMaintenanceService service = service(httpClient);

        service.maintain(
                "S20260703-000081",
                "555033606",
                List.of(input("案例一", "探索性,功能")),
                "trace_tcds_case");

        HttpRequest request = httpClient.request;
        assertThat(request.uri()).isEqualTo(
                URI.create("http://tcds-prod.sdc.icbc:9080/graphDesign/createGraphCase"));
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("toolId"))
                .contains("66f36bfa5c1c6105572b0118880261d6");

        JsonNode body = objectMapper.readTree(httpClient.requestBody);
        assertThat(body.path("aiFlag").asText()).isEqualTo("1");
        assertThat(body.path("method").asText()).isEqualTo("文本理解生成法");
        assertThat(body.path("itemNo").asText()).isEqualTo("S20260703-000081");
        assertThat(body.path("userId").asText()).isEqualTo("555033606");
        assertThat(body.path("caseList").size()).isEqualTo(1);
        assertThat(body.path("caseList").get(0).path("taskType").asText()).isEqualTo("探索性,功能");
        assertThat(body.path("caseList").get(0).path("caseFlag").asText()).isEqualTo("2");
        assertThat(body.path("caseList").get(0).path("dataDependencies").asText()).isEmpty();
        assertThat(body.path("caseList").get(0).path("isAICase").asText()).isEqualTo("是");
        assertThat(body.path("caseList").get(0).path("isUpdate").asText()).isEqualTo("否");
    }

    @Test
    void mapsTcdsBusinessFailureToSafeConflict() {
        TcdsCaseMaintenanceService service = service(
                new RecordingHttpClient(200, TASK_TYPES_RESPONSE, "{\"code\":3,\"msg\":\"业务异常\"}"));

        assertThatThrownBy(() -> service.maintain(
                "S20260703-000081", "555033606", List.of(input("案例一", "探索性")), "trace_tcds_case"))
                .isInstanceOfSatisfying(PlatformException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("业务异常");
                });
    }

    @Test
    void rejectsTaskTypeMissingFromCurrentTaskTypesBeforeCreatingCases() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, TASK_TYPES_RESPONSE);
        TcdsCaseMaintenanceService service = service(httpClient);

        assertThatThrownBy(() -> service.maintain(
                "S20260703-000081", "555033606", List.of(input("案例一", "准入")), "trace_tcds_case"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(httpClient.request.uri()).isEqualTo(
                URI.create("http://tcds-prod.sdc.icbc:9080/task/getTaskTypes"));
        assertThat(httpClient.request.headers().firstValue("toolId"))
                .contains("66f36bfa5c1c6105572b0118880261d6");
    }

    @Test
    void rejectsInvalidTaskTypeSeparatorBeforeCallingTcds() {
        assertThatThrownBy(() -> input("案例一", "准入，功能测试"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void rejectsOversizedResponseBeforeJsonParsing() {
        String oversizedBody = " ".repeat(512 * 1024 + 1);
        TcdsCaseMaintenanceService service = service(
                new RecordingHttpClient(200, TASK_TYPES_RESPONSE, oversizedBody));

        assertThatThrownBy(() -> service.maintain(
                "S20260703-000081", "555033606", List.of(input("案例一", "探索性")), "trace_tcds_case"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE));
    }

    private static TcdsCaseInput input(String name, String taskType) {
        return new TcdsCaseInput(name, "步骤", "数据", "预期", taskType);
    }

    private TcdsCaseMaintenanceService service(HttpClient httpClient) {
        TcdsProperties properties = new TcdsProperties();
        properties.setBaseUrl("http://tcds-prod.sdc.icbc:9080");
        return new TcdsCaseMaintenanceService(
                new TcdsHttpRequestFactory(properties),
                httpClient,
                objectMapper);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int status;
        private final List<byte[]> responseBodies;
        private int responseIndex;
        private HttpRequest request;
        private String requestBody;

        private RecordingHttpClient(int status, String... responseBodies) {
            this.status = status;
            this.responseBodies = java.util.Arrays.stream(responseBodies)
                    .map(responseBody -> responseBody.getBytes(StandardCharsets.UTF_8))
                    .toList();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            this.request = request;
            this.requestBody = request.bodyPublisher().map(ignored -> readRequestBody(request)).orElse(null);
            if (responseIndex >= responseBodies.size()) {
                throw new AssertionError("没有为第 " + (responseIndex + 1) + " 次 HTTP 请求准备响应");
            }
            return (HttpResponse<T>) new InputStreamResponse(
                    status,
                    new ByteArrayInputStream(responseBodies.get(responseIndex++)),
                    request);
        }

        private static String readRequestBody(HttpRequest request) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompletableFuture<String> completed = new CompletableFuture<>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.writeBytes(bytes);
                }
                @Override public void onError(Throwable throwable) { completed.completeExceptionally(throwable); }
                @Override public void onComplete() { completed.complete(output.toString(StandardCharsets.UTF_8)); }
            });
            return completed.join();
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record InputStreamResponse(
            int statusCode,
            InputStream body,
            HttpRequest request) implements HttpResponse<InputStream> {
        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (left, right) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
