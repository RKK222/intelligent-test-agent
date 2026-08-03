package com.enterprise.testagent.model.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDailyRepository;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDelta;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

class ModelGatewayForwardingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T07:00:00Z");

    @Test
    void allowsWorkflowLongContextModelToWarmUpWithoutSlowingInteractiveCalls() {
        assertThat(ModelGatewayForwardingService.firstResponseTimeout(
                        new ModelGatewayCaller("usr_workflow", "AUTH_WORKFLOW", "workflow")))
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(ModelGatewayForwardingService.firstResponseTimeout(
                        new ModelGatewayCaller("usr_lobehub", "AUTH_LOBEHUB", "lobehub")))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rewritesPublicModelInjectsTrustedIdentityAndAggregatesUsage() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"id\":\"answer\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}")
                            .build());
                })
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayCatalogService catalog = new ModelGatewayCatalogService(
                new FixedProviders(),
                new FixedModels());
        ModelGatewayForwardingService service = new ModelGatewayForwardingService(
                catalog,
                usage,
                webClient,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        byte[] requestBody = "{\"model\":\"enterprise-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                        "/api/internal/platform/model-gateway/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer browser-must-not-forward")
                .header("X-Enterprise-Model-Provider", "evil-provider")
                .header("ucid", "evil-ucid")
                .contentType(MediaType.APPLICATION_JSON));

        PreparedModelGatewayRequest prepared = service.prepare(exchange, requestBody);
        assertThat(new String(prepared.upstreamBody(), StandardCharsets.UTF_8))
                .contains("\"model\":\"upstream-chat\"")
                .doesNotContain("enterprise-chat");

        service.forward(
                exchange,
                prepared,
                new ModelGatewayCaller("usr_lobehub", "AUTH_LOBEHUB", "lobehub"),
                "trace_gateway_123456").block();

        assertThat(captured.get().url().toString())
                .isEqualTo("http://models.internal/v1/chat/completions");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer provider-secret");
        assertThat(captured.get().headers().getFirst("ucid")).isEqualTo("AUTH_LOBEHUB");
        assertThat(captured.get().headers().get("X-Enterprise-Model-Provider")).isNull();
        assertThat(usage.delta).satisfies(delta -> {
            assertThat(delta.userId()).isEqualTo("usr_lobehub");
            assertThat(delta.providerId()).isEqualTo("provider-a");
            assertThat(delta.modelId()).isEqualTo("enterprise-chat");
            assertThat(delta.inputTokens()).isEqualTo(10);
            assertThat(delta.outputTokens()).isEqualTo(5);
            assertThat(delta.totalTokens()).isEqualTo(15);
            assertThat(delta.succeeded()).isTrue();
        });
    }

    @Test
    void acceptsEveryJsonModalityOnlyWhenItsCapabilityWasProbed() throws Exception {
        Set<ModelCapability> allCapabilities = Set.of(ModelCapability.values());
        InternalModelProviderModel model = model(allCapabilities, allCapabilities);
        ModelGatewayForwardingService service = service(
                WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{}")
                        .build())).build(),
                new CapturingUsage(),
                model);
        Map<String, String> requests = Map.of(
                "/chat/completions", """
                        {"model":"enterprise-chat","messages":[{"role":"user","content":[
                          {"type":"image_url","image_url":{"url":"data:image/png;base64,AA=="}}
                        ]}],"tools":[{"type":"function"}],"reasoning_effort":"low"}
                        """,
                "/responses", "{" +
                        "\"model\":\"enterprise-chat\",\"input\":\"hello\",\"reasoning\":{}}",
                "/embeddings", "{\"model\":\"enterprise-chat\",\"input\":\"hello\"}",
                "/rerank", "{\"model\":\"enterprise-chat\",\"query\":\"q\",\"documents\":[\"d\"]}",
                "/images/generations", "{\"model\":\"enterprise-chat\",\"prompt\":\"pixel\"}",
                "/audio/speech", "{\"model\":\"enterprise-chat\",\"input\":\"hello\",\"voice\":\"alloy\"}");

        for (Map.Entry<String, String> entry : requests.entrySet()) {
            MockServerWebExchange exchange = exchange(entry.getKey());
            PreparedModelGatewayRequest prepared = service.prepare(
                    exchange,
                    entry.getValue().getBytes(StandardCharsets.UTF_8));
            assertThat(new ObjectMapper().readTree(prepared.upstreamBody()).path("model").asText())
                    .isEqualTo("upstream-chat");
        }

        ModelGatewayForwardingService chatOnly = service(WebClient.create(), new CapturingUsage(),
                model(Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        assertThatThrownBy(() -> chatOnly.prepare(
                exchange("/chat/completions"),
                requests.get("/chat/completions").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("能力探测");
    }

    @Test
    void doesNotTreatCodexToolSchemaAsVisionInput() {
        Set<ModelCapability> textAgentCapabilities = Set.of(
                ModelCapability.CHAT,
                ModelCapability.TOOLS,
                ModelCapability.REASONING);
        ModelGatewayForwardingService service = service(
                WebClient.create(),
                new CapturingUsage(),
                model(textAgentCapabilities, textAgentCapabilities));
        String request = """
                {
                  "model":"enterprise-chat",
                  "input":"analyze the repository",
                  "tools":[{
                    "type":"function",
                    "name":"view_image",
                    "parameters":{
                      "type":"object",
                      "properties":{"image_url":{"type":"string"}}
                    }
                  }],
                  "reasoning":{"effort":"none"},
                  "text":{"format":{"type":"json_schema","schema":{
                    "type":"object",
                    "properties":{"image_url":{"type":"string"}}
                  }}}
                }
                """;

        PreparedModelGatewayRequest prepared = service.prepare(
                exchange("/responses"), request.getBytes(StandardCharsets.UTF_8));

        assertThat(prepared.requiredCapabilities())
                .containsExactlyInAnyOrder(
                        ModelCapability.CHAT,
                        ModelCapability.TOOLS,
                        ModelCapability.REASONING)
                .doesNotContain(ModelCapability.VISION);
    }

    @Test
    void sanitizesProviderErrorAndRecordsOnlyAggregateFailure() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"provider-secret at http://private-model\"}")
                        .build()))
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayForwardingService service = service(client, usage, model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8));

        service.forward(exchange, prepared, caller(), "trace_error").block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("企业模型供应商请求失败")
                .doesNotContain("provider-secret", "private-model");
        assertThat(usage.delta.succeeded()).isFalse();
        assertThat(usage.delta.inputTokens()).isZero();
    }

    @Test
    void releasesDiscardedProviderErrorBuffers() {
        var byteBuf = Unpooled.copiedBuffer("provider error", StandardCharsets.UTF_8);
        var dataBuffer = new NettyDataBufferFactory(byteBuf.alloc()).wrap(byteBuf);
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(dataBuffer))
                        .build()))
                .build();
        ModelGatewayForwardingService service = service(client, new CapturingUsage(), model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8));

        service.forward(exchange, prepared, caller(), "trace_release").block();

        assertThat(byteBuf.refCnt()).isZero();
    }

    @Test
    void extractsAggregateUsageFromSseWithoutPersistingStreamContent() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body("data: {\"choices\":[]}\n\n"
                                + "data: {\"usage\":{\"input_tokens\":7,\"output_tokens\":4,\"total_tokens\":11}}\n\n"
                                + "data: [DONE]\n\n")
                        .build()))
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayForwardingService service = service(client, usage, model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[],\"stream\":true}"
                        .getBytes(StandardCharsets.UTF_8));

        service.forward(exchange, prepared, caller(), "trace_sse").block();

        assertThat(usage.delta.inputTokens()).isEqualTo(7);
        assertThat(usage.delta.outputTokens()).isEqualTo(4);
        assertThat(usage.delta.totalTokens()).isEqualTo(11);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("data:", "[DONE]");
    }

    @Test
    void sanitizesSseInterruptionAndRecordsAggregateFailure() {
        var byteBuf = Unpooled.copiedBuffer("data: {\"choices\":[]}\n\n", StandardCharsets.UTF_8);
        var dataBuffer = new NettyDataBufferFactory(byteBuf.alloc()).wrap(byteBuf);
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(Flux.concat(
                                Flux.just(dataBuffer),
                                Flux.error(new IllegalStateException(
                                        "provider-secret at http://private-model"))))
                        .build()))
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayForwardingService service = service(client, usage, model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[],\"stream\":true}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.forward(exchange, prepared, caller(), "trace_sse_interrupted").block())
                .isInstanceOf(PlatformException.class)
                .hasMessage("企业模型供应商连接失败")
                .hasMessageNotContaining("provider-secret")
                .hasMessageNotContaining("private-model");
        assertThat(usage.delta.succeeded()).isFalse();
        assertThat(byteBuf.refCnt()).isZero();
    }

    @Test
    void masksConnectionFailureAndStillRecordsFailure() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException(
                        "provider-secret at http://private-model")))
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayForwardingService service = service(client, usage, model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.forward(exchange, prepared, caller(), "trace_failure").block())
                .isInstanceOf(PlatformException.class)
                .hasMessage("企业模型供应商连接失败")
                .hasMessageNotContaining("provider-secret")
                .hasMessageNotContaining("private-model");
        assertThat(usage.delta.succeeded()).isFalse();
    }

    @Test
    void masksConnectionFailureEvenAfterDownstreamResponseWasCommitted() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException(
                        "provider-secret at http://private-model")))
                .build();
        CapturingUsage usage = new CapturingUsage();
        ModelGatewayForwardingService service = service(client, usage, model(
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        MockServerWebExchange exchange = exchange("/chat/completions");
        PreparedModelGatewayRequest prepared = service.prepare(
                exchange,
                "{\"model\":\"enterprise-chat\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8));
        exchange.getResponse().setComplete().block();

        assertThatThrownBy(() -> service.forward(exchange, prepared, caller(), "trace_committed").block())
                .isInstanceOf(PlatformException.class)
                .hasMessage("企业模型供应商连接失败")
                .hasMessageNotContaining("provider-secret")
                .hasMessageNotContaining("private-model");
        assertThat(usage.delta.succeeded()).isFalse();
    }

    @Test
    void transcriptionRequiresARealFilePart() {
        ModelGatewayForwardingService service = service(
                WebClient.create(),
                new CapturingUsage(),
                model(Set.of(ModelCapability.TRANSCRIPTION), Set.of(ModelCapability.TRANSCRIPTION)));
        LinkedMultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();
        parts.add("model", formField("model", "enterprise-chat"));
        parts.add("file", formField("file", "not-a-file"));

        assertThatThrownBy(() -> service.prepareMultipart(
                exchange("/audio/transcriptions"), parts))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("file");
    }

    private static ModelGatewayForwardingService service(
            WebClient webClient,
            CapturingUsage usage,
            InternalModelProviderModel model) {
        return new ModelGatewayForwardingService(
                new ModelGatewayCatalogService(new FixedProviders(), new FixedModels(model)),
                usage,
                webClient,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockServerWebExchange exchange(String endpoint) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(
                        ModelGatewayForwardingService.GATEWAY_PATH + endpoint)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static ModelGatewayCaller caller() {
        return new ModelGatewayCaller("usr_lobehub", "AUTH_LOBEHUB", "lobehub");
    }

    private static FormFieldPart formField(String name, String value) {
        return new FormFieldPart() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.EMPTY;
            }

            @Override
            public Flux<DataBuffer> content() {
                return Flux.empty();
            }
        };
    }

    private static InternalModelProviderModel model(
            Set<ModelCapability> declared,
            Set<ModelCapability> probed) {
        return new InternalModelProviderModel(
                "provider-a", "enterprise-chat", "upstream-chat", "企业对话", 128_000L, true,
                declared, probed, NOW, NOW, NOW);
    }

    private static final class CapturingUsage implements ModelGatewayUsageDailyRepository {
        private ModelGatewayUsageDelta delta;

        @Override
        public void increment(ModelGatewayUsageDelta delta) {
            this.delta = delta;
        }
    }

    private static final class FixedProviders implements InternalModelProviderRepository {
        private final InternalModelProvider provider = new InternalModelProvider(
                "provider-a", "Provider A", "http://models.internal/v1", true, 0,
                1L, "token-a", true, NOW, NOW);

        @Override
        public List<InternalModelProvider> findAll() { return List.of(provider); }

        @Override
        public List<InternalModelProvider> findEnabled() { return List.of(provider); }

        @Override
        public List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs() {
            return List.of(new InternalModelProviderRuntimeConfig(provider, "provider-secret"));
        }

        @Override
        public Optional<InternalModelProvider> findByProviderId(String id) { return Optional.of(provider); }

        @Override
        public void replaceProviders(List<InternalModelProvider> providers, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> findAuthToken() { return Optional.empty(); }

        @Override
        public void saveAuthToken(String authToken, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FixedModels implements InternalModelProviderModelRepository {
        private final InternalModelProviderModel model;

        private FixedModels() {
            this(model(Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT)));
        }

        private FixedModels(InternalModelProviderModel model) {
            this.model = model;
        }

        @Override
        public List<InternalModelProviderModel> findByProviderId(String id) { return List.of(model); }

        @Override
        public List<InternalModelProviderModel> findEnabled() { return List.of(model); }

        @Override
        public Optional<InternalModelProviderModel> findByModelId(String id) { return Optional.of(model); }

        @Override
        public void replaceForProvider(String id, List<InternalModelProviderModel> models, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveProbeResult(ModelProbeResult result) { throw new UnsupportedOperationException(); }
    }
}
