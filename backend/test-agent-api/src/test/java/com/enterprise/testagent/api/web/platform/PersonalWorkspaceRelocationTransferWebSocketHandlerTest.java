package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationArchiveUpload;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationReceiveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 验证内部搬迁 WebSocket 只接受票据绑定的二进制归档和单个完成控制帧。 */
class PersonalWorkspaceRelocationTransferWebSocketHandlerTest {

    private static final String RELOCATION_ID = "pwr_relocation_12345678";
    private static final String SHA256 = "a".repeat(64);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void streamsBinaryFramesThenCompletesWithSafeResult() throws Exception {
        PersonalWorkspaceRelocationTransferTicketService tickets =
                mock(PersonalWorkspaceRelocationTransferTicketService.class);
        PersonalWorkspaceRelocationReceiveService receiveService =
                mock(PersonalWorkspaceRelocationReceiveService.class);
        PersonalWorkspaceRelocationArchiveUpload upload = mock(PersonalWorkspaceRelocationArchiveUpload.class);
        when(tickets.consume("pwrt_ticket", PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN, "server-a"))
                .thenReturn(ticket());
        when(receiveService.begin(
                RELOCATION_ID, "server-a", "server-b", SHA256, 4L, "trace_relocation_ws"))
                .thenReturn(upload);
        when(upload.complete()).thenReturn(
                new PersonalWorkspaceRelocationReceiveService.ReceiveResult(
                        RELOCATION_ID, "b".repeat(40)));
        byte[] first = new byte[]{1, 2};
        byte[] second = new byte[]{3, 4};
        FakeWebSocketSession session = new FakeWebSocketSession(
                List.of(first, second, "{\"op\":\"complete\"}"),
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                "server-a");

        new PersonalWorkspaceRelocationTransferWebSocketHandler(tickets, receiveService, OBJECT_MAPPER)
                .handle(session)
                .block(Duration.ofSeconds(2));

        ArgumentCaptor<byte[]> frames = ArgumentCaptor.forClass(byte[].class);
        verify(upload, org.mockito.Mockito.times(2)).append(frames.capture());
        assertThat(frames.getAllValues()).containsExactly(first, second);
        verify(upload).complete();
        verify(upload, never()).abort();
        assertThat(session.sentText()).singleElement().satisfies(payload -> {
            try {
                JsonNode result = OBJECT_MAPPER.readTree(payload);
                assertThat(result.path("success").asBoolean()).isTrue();
                assertThat(result.path("relocationId").asText()).isEqualTo(RELOCATION_ID);
                assertThat(result.path("headCommit").asText()).isEqualTo("b".repeat(40));
                assertThat(payload).doesNotContain("workspaceId", "/data/", "unifiedAuthId");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void rejectsInvalidTicketBeforeOpeningArchive() throws Exception {
        PersonalWorkspaceRelocationTransferTicketService tickets =
                mock(PersonalWorkspaceRelocationTransferTicketService.class);
        PersonalWorkspaceRelocationReceiveService receiveService =
                mock(PersonalWorkspaceRelocationReceiveService.class);
        when(tickets.consume("pwrt_ticket", "https://browser.example", "server-a"))
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "个人工作区搬迁 ticket 无效或已使用"));
        FakeWebSocketSession session = new FakeWebSocketSession(
                List.of(), "https://browser.example", "server-a");

        new PersonalWorkspaceRelocationTransferWebSocketHandler(tickets, receiveService, OBJECT_MAPPER)
                .handle(session)
                .block(Duration.ofSeconds(2));

        verifyNoInteractions(receiveService);
        JsonNode result = OBJECT_MAPPER.readTree(session.sentText().getFirst());
        assertThat(result.path("success").asBoolean()).isFalse();
        assertThat(result.path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(result.hasNonNull("relocationId")).isFalse();
    }

    @Test
    void abortsPartialArchiveWhenConnectionEndsBeforeCompleteFrame() throws Exception {
        PersonalWorkspaceRelocationTransferTicketService tickets =
                mock(PersonalWorkspaceRelocationTransferTicketService.class);
        PersonalWorkspaceRelocationReceiveService receiveService =
                mock(PersonalWorkspaceRelocationReceiveService.class);
        PersonalWorkspaceRelocationArchiveUpload upload = mock(PersonalWorkspaceRelocationArchiveUpload.class);
        when(tickets.consume("pwrt_ticket", PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN, "server-a"))
                .thenReturn(ticket());
        when(receiveService.begin(
                RELOCATION_ID, "server-a", "server-b", SHA256, 4L, "trace_relocation_ws"))
                .thenReturn(upload);
        FakeWebSocketSession session = new FakeWebSocketSession(
                List.of(new byte[]{1, 2}),
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                "server-a");

        new PersonalWorkspaceRelocationTransferWebSocketHandler(tickets, receiveService, OBJECT_MAPPER)
                .handle(session)
                .block(Duration.ofSeconds(2));

        verify(upload).append(org.mockito.ArgumentMatchers.any(byte[].class));
        verify(upload, never()).complete();
        verify(upload).abort();
        JsonNode result = OBJECT_MAPPER.readTree(session.sentText().getFirst());
        assertThat(result.path("success").asBoolean()).isFalse();
        assertThat(result.path("code").asText()).isEqualTo("CONFLICT");
    }

    private PersonalWorkspaceRelocationTransferTicket ticket() {
        return new PersonalWorkspaceRelocationTransferTicket(
                "pwrt_ticket",
                RELOCATION_ID,
                "server-a",
                "server-b",
                SHA256,
                4L,
                "trace_relocation_ws",
                Instant.parse("2026-08-04T04:01:00Z"));
    }

    private static final class FakeWebSocketSession implements WebSocketSession {

        private final HandshakeInfo handshakeInfo;
        private final List<Object> incoming;
        private final DataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();

        private FakeWebSocketSession(List<Object> incoming, String origin, String sourceServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.set(PersonalWorkspaceRelocationTransferTicketStore.TICKET_HEADER, "pwrt_ticket");
            headers.set(PersonalWorkspaceRelocationTransferTicketStore.SOURCE_SERVER_HEADER, sourceServer);
            headers.set("X-Trace-Id", "trace_relocation_ws");
            this.handshakeInfo = new HandshakeInfo(
                    URI.create("ws://127.0.0.1:8080"
                            + PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH),
                    headers,
                    Mono.<Principal>empty(),
                    null);
            this.incoming = List.copyOf(incoming);
        }

        List<String> sentText() {
            return sentText;
        }

        @Override
        public String getId() {
            return "ws_personal_relocation_test";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return buffers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return Flux.fromIterable(incoming).map(value -> value instanceof byte[] bytes
                    ? binaryMessage(factory -> factory.wrap(bytes))
                    : textMessage(value.toString()));
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> sentText.add(message.getPayloadAsText()))
                    .then();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return Mono.just(CloseStatus.NORMAL);
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
