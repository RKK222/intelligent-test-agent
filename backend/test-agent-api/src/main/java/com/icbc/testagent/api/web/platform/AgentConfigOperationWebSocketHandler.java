package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.AgentConfigProgressEvent;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent 配置进度 WebSocket：ticket 校验后只推送 snapshot/step/completed/failed 事件。
 */
@Component
public class AgentConfigOperationWebSocketHandler implements WebSocketHandler {

    private final AgentConfigOperationTicketService ticketService;
    private final AgentConfigApplicationService agentConfigService;
    private final AgentConfigProgressHub progressHub;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public AgentConfigOperationWebSocketHandler(
            AgentConfigOperationTicketService ticketService,
            AgentConfigApplicationService agentConfigService,
            AgentConfigProgressHub progressHub,
            ObjectMapper objectMapper,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
        this.progressHub = Objects.requireNonNull(progressHub, "progressHub must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String traceId = traceId(session.getHandshakeInfo().getHeaders());
        AgentConfigOperationTicket ticket;
        try {
            String origin = session.getHandshakeInfo().getHeaders().getOrigin();
            if (!allowedOrigins.contains(origin)) {
                return sendAndClose(session, error("FORBIDDEN", "origin denied", traceId, Map.of()));
            }
            ticket = ticketService.consume(query(session.getHandshakeInfo().getUri(), "ticket"), origin);
            String operationIdFromPath = operationId(session.getHandshakeInfo().getUri());
            if (!ticket.operationId().equals(operationIdFromPath)) {
                return sendAndClose(session, error("FORBIDDEN", "operationId 与 ticket 不匹配", traceId, Map.of()));
            }
        } catch (PlatformException exception) {
            return sendAndClose(session, error(exception.errorCode().name(), exception.getMessage(), traceId, exception.details()));
        } catch (Exception exception) {
            return sendAndClose(session, error(ErrorCode.FORBIDDEN.name(), "Agent 配置进度 WebSocket 拒绝连接", traceId, Map.of()));
        }

        AgentConfigOperationTicket activeTicket = ticket;
        Mono<String> snapshot = Mono.fromSupplier(() -> snapshot(activeTicket.operationId(), traceId));
        Flux<String> live = progressHub.events(activeTicket.operationId())
                .map(event -> write(eventMessage(event)))
                .takeUntil(this::terminalMessage);
        return session.send(Flux.concat(snapshot, live).map(session::textMessage));
    }

    private boolean terminalMessage(String payload) {
        return payload.contains("\"type\":\"completed\"") || payload.contains("\"type\":\"failed\"");
    }

    private String snapshot(String operationId, String traceId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "snapshot");
        message.put("operationId", operationId);
        message.put("operation", agentConfigService.findOperation(operationId).orElse(null));
        message.put("traceId", traceId);
        return write(message);
    }

    private Map<String, Object> eventMessage(AgentConfigProgressEvent event) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", event.type());
        message.put("operationId", event.operationId());
        message.put("status", event.status().name());
        message.put("currentStep", event.step().name());
        message.put("errorCode", event.errorCode());
        message.put("errorMessage", event.errorMessage());
        message.put("commitHash", event.commitHash());
        message.put("traceId", event.traceId());
        message.put("occurredAt", event.occurredAt());
        return message;
    }

    private Map<String, Object> error(String code, String message, String traceId, Map<String, Object> details) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "failed");
        envelope.put("status", "FAILED");
        envelope.put("errorCode", code);
        envelope.put("errorMessage", message);
        envelope.put("traceId", traceId);
        envelope.put("details", details == null ? Map.of() : details);
        return envelope;
    }

    private Mono<Void> sendAndClose(WebSocketSession session, Map<String, Object> payload) {
        return session.send(Mono.just(session.textMessage(write(payload))))
                .then(session.close());
    }

    private String write(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            return "{\"type\":\"failed\",\"status\":\"FAILED\",\"errorCode\":\"INTERNAL_ERROR\",\"errorMessage\":\"Agent 配置进度序列化失败\"}";
        }
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

    private String operationId(URI uri) {
        String path = uri.getPath();
        String marker = "/operations/";
        int start = path.indexOf(marker);
        int end = path.lastIndexOf("/ws");
        if (start < 0 || end <= start) {
            return "";
        }
        return path.substring(start + marker.length(), end);
    }

    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
