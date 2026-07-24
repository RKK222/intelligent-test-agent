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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InternalModelProxyForwardingServiceTest {

    private static final String PROXY_KEY = "proxy-key";
    private static final String MODEL_TOKEN = "model-token";
    private static final String PROVIDER_ID = "qwen-prod";
    private static final String REQUEST_JSON = "{\"model\":\"Qwen3.6-27B\",\"stream\":true}";
    private static final byte[] REQUEST_BODY = REQUEST_JSON.getBytes(StandardCharsets.UTF_8);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(80);

    @Test
    void timesOutWhenUpstreamDoesNotReturnResponseHeaders() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.never())
                .build();
        InternalModelProxyForwardingService service = service(webClient);

        StepVerifier.create(service.forward(exchange(), REQUEST_BODY, "trace_header_timeout"))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void timesOutWhenSuccessfulSseDoesNotProduceFirstEvent() {
        InternalModelProxyForwardingService service = service(WebClient.create());

        StepVerifier.create(service.withStreamingTimeouts(Flux.never()))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void resetsIdleTimeoutAfterFirstSseEvent() {
        InternalModelProxyForwardingService service = service(WebClient.create());

        StepVerifier.create(service.withStreamingTimeouts(Flux.just("第一条").concatWith(Flux.never())))
                .expectNext("第一条")
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void forwardsEachProviderWithItsProviderIdMappedToken() {
        List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    authorizationHeaders.add(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                    return Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                            .create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{}")
                            .build());
                })
                .build();
        InternalModelProxyForwardingService service = service(webClient, List.of(
                runtimeConfig("qwen-prod", "qwen-token"),
                runtimeConfig("deepseek-prod", "deepseek-token")));

        StepVerifier.create(Mono.when(
                        service.forward(exchange("qwen-prod"), REQUEST_BODY, "trace_qwen"),
                        service.forward(exchange("deepseek-prod"), REQUEST_BODY, "trace_deepseek")))
                .verifyComplete();

        assertThat(authorizationHeaders).containsExactlyInAnyOrder("Bearer qwen-token", "Bearer deepseek-token");
    }

    private InternalModelProxyForwardingService service(WebClient webClient) {
        return service(webClient, List.of(runtimeConfig(PROVIDER_ID, MODEL_TOKEN)));
    }

    private InternalModelProxyForwardingService service(
            WebClient webClient,
            List<InternalModelProviderRuntimeConfig> runtimeConfigs) {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        when(repository.findEnabledRuntimeConfigs()).thenReturn(runtimeConfigs);
        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(repository);
        registry.refresh("trace_test", "test");

        BackendJavaProcessLifecycleService lifecycle = mock(BackendJavaProcessLifecycleService.class);
        InternalModelProxyRuntimeSettings settings = new InternalModelProxyRuntimeSettings(lifecycle, PROXY_KEY);
        return new InternalModelProxyForwardingService(
                registry,
                settings,
                webClient,
                new ObjectMapper(),
                SHORT_TIMEOUT,
                SHORT_TIMEOUT,
                SHORT_TIMEOUT);
    }

    private InternalModelProviderRuntimeConfig runtimeConfig(String providerId, String authToken) {
        InternalModelProvider provider = new InternalModelProvider(
                providerId,
                providerId,
                "http://model.example/v1",
                true,
                1,
                1L,
                providerId + " Token",
                true,
                Instant.now(),
                Instant.now());
        return new InternalModelProviderRuntimeConfig(provider, authToken);
    }

    private MockServerWebExchange exchange() {
        return exchange(PROVIDER_ID);
    }

    private MockServerWebExchange exchange(String providerId) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post(InternalModelProxyRuntimeSettings.PROXY_PATH + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(REQUEST_JSON);
        return MockServerWebExchange.from(request);
    }
}
