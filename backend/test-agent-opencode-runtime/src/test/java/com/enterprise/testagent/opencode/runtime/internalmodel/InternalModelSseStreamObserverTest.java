package com.enterprise.testagent.opencode.runtime.internalmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class InternalModelSseStreamObserverTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(80);
    private final InternalModelSseStreamObserver observer =
            new InternalModelSseStreamObserver(new ObjectMapper());

    @Test
    void onlyRecognizesStructuredModelOutput() {
        assertThat(observer.inspect(data("{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"))
                .output()).isFalse();
        assertThat(observer.inspect(data("ping")).output()).isFalse();
        assertThat(observer.inspect(data("\"ping\"")).output()).isFalse();
        assertThat(observer.inspect(data("{\"choices\":[" )).output()).isFalse();
        assertThat(observer.inspect(data("{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}"))
                .output()).isTrue();
        assertThat(observer.inspect(data("{\"choices\":[{\"delta\":{\"refusal\":\"blocked\"}}]}"))
                .output()).isTrue();
        assertThat(observer.inspect(data("{\"choices\":[{\"delta\":{\"function_call\":{\"name\":\"lookup\"}}}]}"))
                .output()).isTrue();
        assertThat(observer.inspect(data("[DONE]"))).satisfies(event -> {
            assertThat(event.protocolComplete()).isTrue();
            assertThat(event.done()).isTrue();
        });
    }

    @Test
    void recognizesNonEmptyFinishReasonAsCompatibleCompletionSignal() {
        assertThat(observer.inspect(data("""
                {"choices":[{"delta":{},"finish_reason":"stop"}]}
                """))).satisfies(event -> {
            assertThat(event.output()).isFalse();
            assertThat(event.protocolComplete()).isTrue();
            assertThat(event.done()).isFalse();
        });
        assertThat(observer.inspect(data("""
                {"choices":[{"delta":{"content":"last"},"finish_reason":"stop"}]}
                """))).satisfies(event -> {
            assertThat(event.output()).isTrue();
            assertThat(event.protocolComplete()).isTrue();
        });
        assertThat(observer.inspect(data("""
                {"choices":[{"delta":{},"finish_reason":null}]}
                """))).extracting(InternalModelSseStreamObserver.ObservedEvent::protocolComplete)
                .isEqualTo(false);
        assertThat(observer.inspect(data("""
                {"choices":[{"delta":{},"finish_reason":""}]}
                """))).extracting(InternalModelSseStreamObserver.ObservedEvent::protocolComplete)
                .isEqualTo(false);
    }

    @Test
    void commentsCannotExtendFirstOutputDeadline() {
        Flux<ServerSentEvent<String>> comments = Flux.interval(Duration.ofMillis(20))
                .map(ignored -> ServerSentEvent.<String>builder().comment("keepalive").build());

        StepVerifier.create(observer.observe(comments, SHORT_TIMEOUT, SHORT_TIMEOUT)
                        .filter(InternalModelSseStreamObserver.ObservedEvent::output))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void commentsCannotExtendIdleDeadlineAfterOutput() {
        Flux<ServerSentEvent<String>> source = Flux.concat(
                Flux.just(data("{\"choices\":[{\"delta\":{\"content\":\"first\"}}]}")),
                Flux.interval(Duration.ofMillis(20))
                        .map(ignored -> ServerSentEvent.<String>builder().comment("keepalive").build()));

        StepVerifier.create(observer.observe(source, SHORT_TIMEOUT, SHORT_TIMEOUT)
                        .filter(InternalModelSseStreamObserver.ObservedEvent::output))
                .expectNextCount(1)
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1));
    }

    private static ServerSentEvent<String> data(String value) {
        return ServerSentEvent.<String>builder().data(value).build();
    }
}
