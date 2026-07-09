package com.icbc.testagent.api.web.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * WebSocketLoggingAspect 单元测试。
 */
class WebSocketLoggingAspectTest {

    private WebSocketLoggingAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    @BeforeEach
    void setUp() {
        aspect = new WebSocketLoggingAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(TestWebSocketHandler.class);
        when(signature.getName()).thenReturn("handle");
    }

    @Test
    @DisplayName("从参数中提取 WebSocketSession")
    void extractSessionFindsWebSocketSession() {
        WebSocketSession session = mock(WebSocketSession.class);

        WebSocketSession result = aspect.extractSession(new Object[]{"ignored", session});

        assertThat(result).isSameAs(session);
    }

    @Test
    @DisplayName("从握手信息提取 WebSocket path")
    void pathUsesHandshakeUri() {
        WebSocketSession session = session("/api/internal/platform/terminal/ws");

        assertThat(aspect.path(session)).isEqualTo("/api/internal/platform/terminal/ws");
    }

    @Test
    @DisplayName("Mono 完成语义保持不变")
    void logWebSocketKeepsMonoCompletion() throws Throwable {
        WebSocketSession session = session("/api/internal/platform/workspace/files/ws");
        when(joinPoint.getArgs()).thenReturn(new Object[]{session});
        when(joinPoint.proceed()).thenReturn(Mono.empty());

        Object result = aspect.logWebSocket(joinPoint);

        assertThat(result).isInstanceOf(Mono.class);
        StepVerifier.create((Mono<?>) result).verifyComplete();
    }

    @Test
    @DisplayName("Mono 异常语义保持不变")
    void logWebSocketKeepsMonoError() throws Throwable {
        WebSocketSession session = session("/api/internal/platform/workspace/files/ws");
        when(joinPoint.getArgs()).thenReturn(new Object[]{session});
        when(joinPoint.proceed()).thenReturn(Mono.error(new IllegalStateException("failed")));

        Object result = aspect.logWebSocket(joinPoint);

        assertThat(result).isInstanceOf(Mono.class);
        StepVerifier.create((Mono<?>) result)
                .expectErrorMatches(error -> error instanceof IllegalStateException && "failed".equals(error.getMessage()))
                .verify();
    }

    private WebSocketSession session(String path) {
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "trace_1234567890abcdef");
        when(handshakeInfo.getUri()).thenReturn(URI.create("ws://127.0.0.1:8080" + path));
        when(handshakeInfo.getHeaders()).thenReturn(headers);
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        return session;
    }

    static class TestWebSocketHandler {
        Mono<Void> handle(WebSocketSession session) {
            return Mono.empty();
        }
    }
}
