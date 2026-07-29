package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import com.enterprise.testagent.integration.lobehub.LobehubModelIdentity;
import com.enterprise.testagent.integration.lobehub.LobehubSsoRedeemResult;
import com.enterprise.testagent.integration.lobehub.LobehubSsoService;
import com.enterprise.testagent.integration.lobehub.LobehubSsoTicketIssue;
import com.enterprise.testagent.model.gateway.ModelGatewayCatalogService;
import com.enterprise.testagent.model.gateway.ModelGatewayCaller;
import com.enterprise.testagent.model.gateway.ModelGatewayForwarder;
import com.enterprise.testagent.model.gateway.PreparedModelGatewayRequest;
import com.enterprise.testagent.model.gateway.PreparedModelGatewayMultipartRequest;
import org.springframework.http.client.MultipartBodyBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class ModelGatewayControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T07:00:00Z");

    @Test
    void modelsRequiresDelegatedGrantAndDoesNotExposeProviderRouting() {
        FakeSsoService sso = new FakeSsoService();
        ModelGatewayCatalogService catalog = new ModelGatewayCatalogService(new Providers(), new Models());
        CapturingForwarder forwarder = new CapturingForwarder();
        WebTestClient client = WebTestClient.bindToController(new ModelGatewayController(sso, catalog, forwarder))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri(ModelGatewayController.MODELS_PATH)
                .header("Authorization", "Bearer delegated-grant")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("enterprise-chat")
                .jsonPath("$.data[0].owned_by").isEqualTo("enterprise")
                .jsonPath("$.data[0].providerId").doesNotExist();

        assertThat(sso.authenticatedGrant).isEqualTo("delegated-grant");

        client.get()
                .uri(ModelGatewayController.MODELS_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void transcriptionKeepsGrantServerSideAndUsesMultipartForwarder() {
        FakeSsoService sso = new FakeSsoService();
        CapturingForwarder forwarder = new CapturingForwarder();
        WebTestClient client = WebTestClient.bindToController(new ModelGatewayController(
                        sso,
                        new ModelGatewayCatalogService(new Providers(), new Models()),
                        forwarder))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("model", "enterprise-chat");
        body.part("file", new byte[] {0, 1, 2})
                .filename("sample.wav")
                .contentType(org.springframework.http.MediaType.parseMediaType("audio/wav"));

        client.post()
                .uri(ModelGatewayController.BASE_PATH + "/audio/transcriptions")
                .header("Authorization", "Bearer delegated-grant")
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isOk();

        assertThat(forwarder.multipartPrepared).isTrue();
        assertThat(forwarder.forwardedCaller.userId()).isEqualTo("usr_lobehub");
        assertThat(forwarder.forwardedCaller.unifiedAuthId()).isEqualTo("AUTH_LOBEHUB");
    }

    private static final class CapturingForwarder implements ModelGatewayForwarder {
        private boolean multipartPrepared;
        private ModelGatewayCaller forwardedCaller;

        @Override
        public PreparedModelGatewayRequest prepare(ServerWebExchange exchange, byte[] body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> forward(
                ServerWebExchange exchange,
                PreparedModelGatewayRequest request,
                ModelGatewayCaller caller,
                String traceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedModelGatewayMultipartRequest prepareMultipart(
                ServerWebExchange exchange,
                org.springframework.util.MultiValueMap<String, org.springframework.http.codec.multipart.Part> parts) {
            multipartPrepared = true;
            return new PreparedModelGatewayMultipartRequest(
                    "/audio/transcriptions",
                    "enterprise-chat",
                    new com.enterprise.testagent.model.gateway.ResolvedModel(
                            new InternalModelProviderRuntimeConfig(new Providers().provider, "provider-secret"),
                            new Models().model),
                    new MultipartBodyBuilder().build());
        }

        @Override
        public Mono<Void> forwardMultipart(
                ServerWebExchange exchange,
                PreparedModelGatewayMultipartRequest request,
                ModelGatewayCaller caller,
                String traceId) {
            forwardedCaller = caller;
            return exchange.getResponse().setComplete();
        }
    }

    private static final class FakeSsoService implements LobehubSsoService {
        private String authenticatedGrant;

        @Override
        public LobehubSsoTicketIssue issue(com.enterprise.testagent.domain.auth.AuthPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LobehubSsoRedeemResult redeem(String ticket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LobehubModelIdentity authenticateModelGrant(String grant) {
            if (grant == null || grant.isBlank()) {
                throw new com.enterprise.testagent.common.error.PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.UNAUTHENTICATED,
                        "模型委托凭证无效");
            }
            authenticatedGrant = grant;
            return new LobehubModelIdentity("usr_lobehub", "AUTH_LOBEHUB", "model-gateway");
        }

        @Override
        public void revokeModelGrant(String grant) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Providers implements InternalModelProviderRepository {
        private final InternalModelProvider provider = new InternalModelProvider(
                "provider-a", "Provider A", "http://models.internal/v1", true, 0,
                1L, "token-a", true, NOW, NOW);

        @Override public List<InternalModelProvider> findAll() { return List.of(provider); }
        @Override public List<InternalModelProvider> findEnabled() { return List.of(provider); }
        @Override public List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs() {
            return List.of(new InternalModelProviderRuntimeConfig(provider, "provider-secret"));
        }
        @Override public Optional<InternalModelProvider> findByProviderId(String id) { return Optional.of(provider); }
        @Override public void replaceProviders(List<InternalModelProvider> providers, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<String> findAuthToken() { return Optional.empty(); }
        @Override public void saveAuthToken(String token, Instant at) { throw new UnsupportedOperationException(); }
    }

    private static final class Models implements InternalModelProviderModelRepository {
        private final InternalModelProviderModel model = new InternalModelProviderModel(
                "provider-a", "enterprise-chat", "upstream-chat", "企业对话", 128_000L, true,
                Set.of(ModelCapability.CHAT), Set.of(ModelCapability.CHAT), NOW, NOW, NOW);

        @Override public List<InternalModelProviderModel> findByProviderId(String id) { return List.of(model); }
        @Override public List<InternalModelProviderModel> findEnabled() { return List.of(model); }
        @Override public Optional<InternalModelProviderModel> findByModelId(String id) { return Optional.of(model); }
        @Override public void replaceForProvider(String id, List<InternalModelProviderModel> models, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public void saveProbeResult(ModelProbeResult result) { throw new UnsupportedOperationException(); }
    }
}
