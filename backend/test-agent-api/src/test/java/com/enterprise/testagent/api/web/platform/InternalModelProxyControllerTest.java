package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.DisposableServer;
import reactor.test.StepVerifier;

class InternalModelProxyControllerTest {

    private static final String PROXY_KEY = "proxy-key";
    private static final String MODEL_TOKEN = "model-token";
    private static final String PROVIDER_ID = "qwen-prod";
    private static final String UCID = "001177621";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer upstream;
    private DisposableServer downstream;
    private AnnotationConfigApplicationContext downstreamContext;

    @BeforeEach
    void setUp() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void tearDown() {
        if (downstream != null) {
            downstream.disposeNow();
        }
        if (downstreamContext != null) {
            downstreamContext.close();
        }
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void streamsFirstSseEventBeforeUpstreamClosesAndAvoidsDoubleDataPrefix() throws Exception {
        CountDownLatch firstEventWritten = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        upstream.createContext("/enterprise/jdt/model/api/openai/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(("id: evt-1\n"
                                + "event: delta\n"
                                + "retry: 1000\n"
                                + "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                body.flush();
                firstEventWritten.countDown();
                try {
                    releaseUpstream.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                body.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
        });
        upstream.start();

        WebTestClient client = clientForProvider(upstreamBaseUrl());
        var result = client.post()
                .uri("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, PROVIDER_ID)
                .header(InternalModelProxyForwardingService.UCID_HEADER, UCID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"Qwen3.6-27B\",\"stream\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(byte[].class);

        assertThat(firstEventWritten.await(2, TimeUnit.SECONDS)).isTrue();
        StepVerifier.create(result.getResponseBody())
                .assertNext(bytes -> {
                    String firstFrame = new String(bytes, StandardCharsets.UTF_8);
                    assertThat(firstFrame).contains("data:");
                    assertThat(firstFrame).doesNotContain("data:data:");
                    assertThat(firstFrame).contains("你好");
                    assertThat(firstFrame).contains("id:evt-1", "event:delta", "retry:1000");
                    releaseUpstream.countDown();
                })
                .assertNext(bytes -> assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("[DONE]"))
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void forwardsNonSseErrorBodyAndStatusWithoutTryingToDecodeSse() {
        upstream.createContext("/enterprise/jdt/model/api/openai/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_ENCODING, "identity");
            exchange.getResponseHeaders().set(HttpHeaders.RETRY_AFTER, "5");
            exchange.getResponseHeaders().set("X-Trace-Id", "upstream-trace");
            byte[] body = "model rejected".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();

        clientForProvider(upstreamBaseUrl()).post()
                .uri("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, PROVIDER_ID)
                .header(InternalModelProxyForwardingService.UCID_HEADER, UCID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"Qwen3.6-27B\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_ENCODING, "identity")
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "5")
                .expectHeader().valueEquals("X-Trace-Id", "upstream-trace")
                .expectBody(String.class)
                .isEqualTo("model rejected");
    }

    @Test
    void forwardsFourHundredSseBodyWithoutThinkConversion() {
        upstream.createContext("/enterprise/jdt/model/api/openai/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            byte[] body = "data: upstream rejected\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();

        clientForProvider(upstreamBaseUrl()).post()
                .uri("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, PROVIDER_ID)
                .header(InternalModelProxyForwardingService.UCID_HEADER, UCID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"Qwen3.6-27B\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(String.class)
                .isEqualTo("data: upstream rejected\n\n");
    }

    @Test
    void preservesDeepSeekNativeReasoningToolCallsAndComment() {
        upstream.createContext("/enterprise/jdt/model/api/openai/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, 0);
            String response = """
                    : source-comment
                    event: delta
                    data: {"choices":[{"delta":{"reasoning_content":"已有思考","content":"<think>不要转换</think>最终回答","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"read_file","arguments":"{\\"path\\":\\"a.txt\\"}"}}]}}]}

                    data: [DONE]

                    """;
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        });
        upstream.start();

        clientForProvider(upstreamBaseUrl(), "deepseek-prod").post()
                .uri("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, "deepseek-prod")
                .header(InternalModelProxyForwardingService.UCID_HEADER, UCID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"DeepSeek-V4-Flash-W8A8\",\"stream\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("source-comment")
                        .contains("\"reasoning_content\":\"已有思考\"")
                        .contains("\"content\":\"<think>不要转换</think>最终回答\"")
                        .contains("\"name\":\"read_file\"")
                        .contains("[DONE]"));
    }

    @Test
    void cancelsUpstreamWhenDownstreamCancelsSseSubscription() throws Exception {
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        upstream.createContext("/enterprise/jdt/model/api/openai/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write("data: {\"choices\":[{\"delta\":{\"content\":\"首条\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                body.flush();
                while (true) {
                    try {
                        Thread.sleep(25);
                        body.write("data: {\"choices\":[{\"delta\":{\"content\":\"后续\"}}]}\n\n"
                                .getBytes(StandardCharsets.UTF_8));
                        body.flush();
                    } catch (IOException disconnected) {
                        upstreamCancelled.countDown();
                        break;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        upstream.start();

        var result = clientForProvider(upstreamBaseUrl()).post()
                .uri("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, PROVIDER_ID)
                .header(InternalModelProxyForwardingService.UCID_HEADER, UCID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"Qwen3.6-27B\",\"stream\":true}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(byte[].class);

        StepVerifier.create(result.getResponseBody().take(1))
                .assertNext(bytes -> assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("首条"))
                .expectComplete()
                .verify(Duration.ofSeconds(3));

        assertThat(upstreamCancelled.await(3, TimeUnit.SECONDS)).isTrue();
    }

    private WebTestClient clientForProvider(String baseUrl) {
        return clientForProvider(baseUrl, PROVIDER_ID);
    }

    private WebTestClient clientForProvider(String baseUrl, String providerId) {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        InternalModelProvider provider = new InternalModelProvider(
                providerId,
                providerId,
                baseUrl + "/enterprise/jdt/model/api/openai/v1",
                true,
                1,
                Instant.now(),
                Instant.now());
        when(repository.findEnabledRuntimeConfigs())
                .thenReturn(List.of(new InternalModelProviderRuntimeConfig(provider, MODEL_TOKEN)));

        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(repository);
        registry.refresh("trace_test", "test");

        BackendJavaProcessLifecycleService lifecycle = mock(BackendJavaProcessLifecycleService.class);
        InternalModelProxyRuntimeSettings settings = new InternalModelProxyRuntimeSettings(lifecycle, PROXY_KEY);
        InternalModelProxyForwardingService service = new InternalModelProxyForwardingService(
                registry,
                settings,
                WebClient.create(),
                OBJECT_MAPPER);
        downstreamContext = new AnnotationConfigApplicationContext();
        downstreamContext.register(ControllerTestConfiguration.class);
        downstreamContext.registerBean(
                InternalModelProxyController.class,
                () -> new InternalModelProxyController(service));
        downstreamContext.refresh();

        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(
                WebHttpHandlerBuilder.applicationContext(downstreamContext).build());
        downstream = reactor.netty.http.server.HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(adapter)
                .bindNow();
        return WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + downstream.port())
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    private String upstreamBaseUrl() {
        return "http://127.0.0.1:" + upstream.getAddress().getPort();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class ControllerTestConfiguration {
    }
}
