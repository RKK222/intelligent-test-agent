package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    private static final String REQUEST_BODY = "{\"model\":\"Qwen3.6-27B\",\"stream\":true}";
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

    private InternalModelProxyForwardingService service(WebClient webClient) {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        InternalModelProvider provider = new InternalModelProvider(
                PROVIDER_ID,
                "Qwen",
                "http://model.example/v1",
                true,
                1,
                Instant.now(),
                Instant.now());
        when(repository.findEnabled()).thenReturn(List.of(provider));
        when(repository.findAuthToken()).thenReturn(Optional.of(MODEL_TOKEN));
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

    private MockServerWebExchange exchange() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post(InternalModelProxyRuntimeSettings.PROXY_PATH + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PROXY_KEY)
                .header(InternalModelProxyForwardingService.PROVIDER_HEADER, PROVIDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .body(REQUEST_BODY);
        return MockServerWebExchange.from(request);
    }
}
