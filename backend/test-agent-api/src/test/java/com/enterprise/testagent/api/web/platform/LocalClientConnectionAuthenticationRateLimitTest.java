package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientRegistrationService;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientTunnelGateway;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 验证 WSS 首帧认证实际使用可信来源限流，而不是可伪造的转发头首项。 */
class LocalClientConnectionAuthenticationRateLimitTest {

    @Test
    void sameRealSourceCannotBypassAuthenticationLimitWithSpoofedForwardedPrefix() {
        LocalClientCredentialApplicationService credentials = mock(LocalClientCredentialApplicationService.class);
        when(credentials.authenticate(anyString(), anyString(), anyBoolean()))
                .thenThrow(new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端认证失败"));
        LocalClientAuthenticationRateLimiter limiter = new LocalClientAuthenticationRateLimiter(
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
                1,
                Duration.ofMinutes(1));
        LocalClientConnectionWebSocketHandler handler = new LocalClientConnectionWebSocketHandler(
                credentials,
                mock(LocalClientRegistrationService.class),
                mock(LocalClientConnectionRegistry.class),
                mock(LocalClientConnectionSupersessionService.class),
                mock(LocalClientTunnelGateway.class),
                mock(BackendJavaRouteResolver.class),
                mock(OpencodeProcessStartupService.class),
                mock(LocalClientUpdateCoordinator.class),
                new LocalClientControlSecuritySettings(false, "127.0.0.1"),
                limiter);

        handler.handle(session("198.51.100.10, 203.0.113.8")).block(Duration.ofSeconds(3));
        handler.handle(session("198.51.100.11, 203.0.113.8")).block(Duration.ofSeconds(3));

        verify(credentials, times(1)).authenticate(anyString(), anyString(), anyBoolean());
    }

    private static FakeWebSocketSession session(String forwardedFor) {
        LocalClientFrameCodec codec = new LocalClientFrameCodec();
        LocalClientPayloads.Register payload = new LocalClientPayloads.Register(
                "tack_v1_invalid",
                "lci_auth_rate_limit",
                "kylin-device",
                "linux",
                "arm64",
                "20260820120000",
                "1.18.4",
                List.of(),
                "UC-001",
                "1",
                List.of("SELF_UPDATE_V1"));
        LocalClientFrame frame = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.REGISTER,
                "register-rate-limit",
                "trace-auth-rate-limit",
                null,
                codec.payload(payload));
        return new FakeWebSocketSession(codec.encode(frame), forwardedFor);
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final String incoming;
        private final DataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sent = new ArrayList<>();
        private boolean open = true;

        private FakeWebSocketSession(String incoming, String forwardedFor) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Forwarded-For", forwardedFor);
            this.handshakeInfo = new HandshakeInfo(
                    URI.create("wss://platform.example" + LocalClientConnectionWebSocketHandler.PATH),
                    headers,
                    new LinkedMultiValueMap<>(),
                    Mono.<Principal>empty(),
                    null,
                    new InetSocketAddress("127.0.0.1", 8443),
                    Map.of(),
                    "ws-auth-rate-limit");
            this.incoming = incoming;
        }

        @Override
        public String getId() {
            return "ws-auth-rate-limit";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return Flux.just(textMessage(incoming));
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages).doOnNext(message -> sent.add(message.getPayloadAsText())).then();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            open = false;
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return Mono.just(CloseStatus.NORMAL);
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return buffers;
        }

        @Override
        public WebSocketMessage textMessage(String payload) {
            return new WebSocketMessage(
                    WebSocketMessage.Type.TEXT,
                    buffers.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(buffers));
        }

        @Override
        public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(buffers));
        }

        @Override
        public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(buffers));
        }
    }
}
