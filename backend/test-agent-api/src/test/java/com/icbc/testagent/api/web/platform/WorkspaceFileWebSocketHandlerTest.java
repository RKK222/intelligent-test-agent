package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.workspace.FileContentResponse;
import com.icbc.testagent.workspace.WorkspaceApplicationService;
import com.icbc.testagent.workspace.WorkspaceDirectoryService;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WorkspaceFileWebSocketHandlerTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Test
    void readsPublicAgentConfigFileThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        when(agentConfigService.readPublicAgentFile("review.md", "agw_123"))
                .thenReturn(new FileContentResponse("review.md", "content", 7));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.read","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"id\":\"req_1\"");
            assertThat(message).contains("\"type\":\"result\"");
            assertThat(message).contains("\"content\":\"content\"");
        });
        verify(agentConfigService).readPublicAgentFile("review.md", "agw_123");
    }

    @Test
    void rejectsAgentConfigWriteWhenTicketIsNotSuperAdmin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(false, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.write","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md","content":"changed"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).writePublicAgentFile("review.md", "changed", "agw_123");
    }

    @Test
    void rejectsAgentConfigRequestWhenWorktreeDoesNotMatchTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.read","params":{"scope":"PUBLIC","worktreeId":"agw_other","path":"review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).readPublicAgentFile("review.md", "agw_other");
    }

    @Test
    void rejectsProtectedWorkspaceConfigWriteForOrdinaryUser() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000")).thenReturn(new WorkspaceFileSocketTicket(
                "wft_workspace",
                "wrk_1234567890abcdef",
                "linux-1",
                "linux-1",
                false,
                false,
                "usr_1234567890abcdef",
                "workspace",
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60)));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_write","op":"workspace.write","params":{"workspaceId":"wrk_1234567890abcdef","path":".opencode/skills/pay/SKILL.md","content":"changed"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(workspaceService, never()).writeFile(Mockito.any(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void renamesWorkspaceFileThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_rename","op":"workspace.rename","params":{"workspaceId":"wrk_1234567890abcdef","path":"docs/old.md","name":"new.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"id\":\"req_rename\"");
            assertThat(message).contains("\"type\":\"result\"");
        });
        verify(workspaceService).renameFile(
                new com.icbc.testagent.domain.workspace.WorkspaceId("wrk_1234567890abcdef"),
                "docs/old.md",
                "new.md");
    }

    private static WorkspaceFileWebSocketHandler handler(
            WorkspaceFileSocketTicketService ticketService,
            AgentConfigApplicationService agentConfigService) {
        return new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
    }

    private static WorkspaceFileSocketTicket agentTicket(
            boolean superAdmin,
            String scope,
            String workspaceId,
            String worktreeId) {
        return new WorkspaceFileSocketTicket(
                "wft_public",
                workspaceId,
                "linux-1",
                null,
                superAdmin,
                "agent-config",
                scope,
                worktreeId,
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static WorkspaceFileSocketTicket workspaceTicket(String workspaceId) {
        return new WorkspaceFileSocketTicket(
                "wft_workspace",
                workspaceId,
                "linux-1",
                workspaceId,
                false,
                "workspace",
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final List<String> incoming;
        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();

        private FakeWebSocketSession(String path, List<String> incoming) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin("http://localhost:3000");
            headers.set("X-Trace-Id", TRACE_ID);
            this.handshakeInfo = new HandshakeInfo(URI.create("ws://127.0.0.1:8080" + path), headers, Mono.<Principal>empty(), null);
            this.incoming = List.copyOf(incoming);
        }

        static FakeWebSocketSession allowed(String path, List<String> incoming) {
            return new FakeWebSocketSession(path, incoming);
        }

        List<String> sentText() {
            return sentText;
        }

        @Override
        public String getId() {
            return "ws_test";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return Flux.fromIterable(incoming).map(this::textMessage);
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
                    bufferFactory.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public WebSocketMessage binaryMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pingMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pongMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(bufferFactory));
        }
    }
}
