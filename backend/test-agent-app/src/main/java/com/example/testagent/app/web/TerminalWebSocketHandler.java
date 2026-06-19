package com.example.testagent.app.web;

import com.example.testagent.app.config.TestAgentRuntimeProperties;
import com.example.testagent.app.terminal.TerminalActiveSessionRegistry;
import com.example.testagent.app.terminal.TerminalApplicationService;
import com.example.testagent.app.terminal.TerminalClientMessage;
import com.example.testagent.app.terminal.TerminalMessageCodec;
import com.example.testagent.app.terminal.TerminalProcessFactory;
import com.example.testagent.app.terminal.TerminalProcessSession;
import com.example.testagent.app.terminal.TerminalServerMessage;
import com.example.testagent.app.terminal.TerminalTicket;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.observability.TraceConstants;
import com.example.testagent.observability.TraceIdSupport;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * 受控 PTY WebSocket handler。所有 upgrade 必须先消费短期 ticket，不能直接信任前端路径。
 */
@Component
public class TerminalWebSocketHandler implements WebSocketHandler {

    private final TerminalApplicationService terminalService;
    private final TerminalProcessFactory processFactory;
    private final TerminalMessageCodec codec;
    private final Set<String> allowedOrigins;
    private final TerminalActiveSessionRegistry activeSessions;

    public TerminalWebSocketHandler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalMessageCodec codec,
            TestAgentRuntimeProperties properties,
            TerminalActiveSessionRegistry activeSessions) {
        this.terminalService = terminalService;
        this.processFactory = processFactory;
        this.codec = codec;
        this.allowedOrigins = Set.copyOf(properties.getSecurity().getCorsAllowedOrigins());
        this.activeSessions = activeSessions;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String traceId = traceId(session.getHandshakeInfo().getHeaders());
        TerminalTicket ticket;
        try {
            URI uri = session.getHandshakeInfo().getUri();
            String origin = session.getHandshakeInfo().getHeaders().getOrigin();
            if (!allowedOrigins.contains(origin)) {
                return sendErrorAndClose(session, "PTY_ORIGIN_DENIED", "origin denied");
            }
            ticket = terminalService.consumeTicket(sessionId(uri.getPath()), query(uri, "ticket"), origin, traceId);
        } catch (PlatformException exception) {
            return sendErrorAndClose(session, exception.errorCode().name(), exception.getMessage());
        } catch (Exception exception) {
            return sendErrorAndClose(session, "PTY_DENIED", "terminal denied");
        }

        TerminalActiveSessionRegistry.Lease lease = null;
        TerminalProcessSession terminal;
        try {
            lease = activeSessions.reserve(ticket);
            terminal = processFactory.start(ticket);
        } catch (PlatformException exception) {
            if (lease != null) {
                lease.close();
            }
            return sendErrorAndClose(session, exception.errorCode().name(), exception.getMessage());
        } catch (Exception exception) {
            if (lease != null) {
                lease.close();
            }
            return sendErrorAndClose(session, "PTY_UNAVAILABLE", "terminal unavailable");
        }
        TerminalActiveSessionRegistry.Lease activeLease = lease;
        TerminalProcessSession activeTerminal = terminal;
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(codec::decode)
                .flatMap(message -> handleClientMessage(activeTerminal, message))
                .then();
        Mono<Void> outbound = session.send(activeTerminal.output()
                .map(codec::encode)
                .map(session::textMessage));
        return Mono.when(inbound, outbound)
                .doFinally(ignored -> {
                    activeLease.close();
                    activeTerminal.close().subscribe();
                });
    }

    private Mono<Void> handleClientMessage(TerminalProcessSession terminal, TerminalClientMessage message) {
        if ("input".equals(message.type())) {
            return terminal.input(message.data());
        }
        if ("resize".equals(message.type())) {
            return terminal.resize(message.cols(), message.rows());
        }
        if ("close".equals(message.type())) {
            return terminal.close();
        }
        return Mono.empty();
    }

    private Mono<Void> sendErrorAndClose(WebSocketSession session, String code, String message) {
        return session.send(Mono.just(session.textMessage(codec.encode(TerminalServerMessage.error(code, message)))))
                .then(session.close());
    }

    private SessionId sessionId(String path) {
        List<String> segments = List.of(path.split("/"));
        int index = segments.indexOf("sessions");
        if (index < 0 || index + 1 >= segments.size()) {
            throw new IllegalArgumentException("missing session id");
        }
        return new SessionId(segments.get(index + 1));
    }

    private String query(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                return pair[1];
            }
        }
        return "";
    }

    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
