package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 应用源码进度 WebSocket。
 *
 * <p>每次连接先读数据库权威快照，再轮询持久化步骤；关闭连接只停止观察，不触发 operation 取消。
 * 数据库轮询也让任意 Java/服务器写入的全局与分服务器步骤都能安全汇聚到签发节点。
 */
@Component
public class AppSourceOperationWebSocketHandler implements WebSocketHandler {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    private final AppSourceOperationTicketService tickets;
    private final AppSourceApplicationService appSources;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;
    private final Duration pollInterval;

    public AppSourceOperationWebSocketHandler(
            AppSourceOperationTicketService tickets,
            AppSourceApplicationService appSources,
            ObjectMapper objectMapper,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins) {
        this(tickets, appSources, objectMapper, allowedOrigins, DEFAULT_POLL_INTERVAL);
    }

    AppSourceOperationWebSocketHandler(
            AppSourceOperationTicketService tickets,
            AppSourceApplicationService appSources,
            ObjectMapper objectMapper,
            String allowedOrigins,
            Duration pollInterval) {
        this.tickets = Objects.requireNonNull(tickets, "tickets must not be null");
        this.appSources = Objects.requireNonNull(appSources, "appSources must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String operationId = operationId(session.getHandshakeInfo().getUri());
        AppSourceOperationTicket ticket;
        try {
            String origin = session.getHandshakeInfo().getHeaders().getOrigin();
            if (!allowedOrigins.contains(origin)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码进度 WebSocket 拒绝连接");
            }
            ticket = tickets.consume(
                    query(session.getHandshakeInfo().getUri(), "ticket"),
                    operationId,
                    origin);
        } catch (PlatformException exception) {
            return sendAndClose(session, failed(null, exception.errorCode().name(), exception.getMessage()));
        } catch (RuntimeException exception) {
            return sendAndClose(session, failed(null, ErrorCode.FORBIDDEN.name(), "应用源码进度 WebSocket 拒绝连接"));
        }

        UserId userId = new UserId(ticket.userId());
        Mono<AppSourceApplicationService.OperationSnapshot> first = read(
                ticket.operationId(), userId, ticket.appAdmin());
        Flux<String> messages = first.flatMapMany(snapshot -> Flux.concat(
                        Mono.just(write(message("snapshot", snapshot))),
                        subsequent(ticket, userId, snapshot)))
                .onErrorResume(PlatformException.class, exception -> Flux.just(write(failed(
                        ticket.operationId(),
                        exception.errorCode().name(),
                        exception.getMessage()))))
                .onErrorResume(RuntimeException.class, exception -> Flux.just(write(failed(
                        ticket.operationId(),
                        ErrorCode.INTERNAL_ERROR.name(),
                        "应用源码进度读取失败"))));
        return session.send(messages.map(session::textMessage));
    }

    private Flux<String> subsequent(
            AppSourceOperationTicket ticket,
            UserId userId,
            AppSourceApplicationService.OperationSnapshot initial) {
        if (initial.status().terminal()) {
            return Flux.just(write(message(terminalType(initial.status()), initial)));
        }
        return Flux.defer(() -> Mono.delay(pollInterval)
                        .then(read(ticket.operationId(), userId, ticket.appAdmin())))
                .repeat()
                .startWith(initial)
                .distinctUntilChanged()
                .skip(1)
                .takeUntil(snapshot -> snapshot.status().terminal())
                .map(snapshot -> write(message(
                        snapshot.status().terminal() ? terminalType(snapshot.status()) : "step",
                        snapshot)));
    }

    private Mono<AppSourceApplicationService.OperationSnapshot> read(
            String operationId,
            UserId userId,
            boolean appAdmin) {
        return Mono.fromCallable(() -> appSources.getOperation(operationId, userId, appAdmin))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String terminalType(AppSourceOperationStatus status) {
        return status == AppSourceOperationStatus.FAILED ? "failed" : "completed";
    }

    private Map<String, Object> message(
            String type,
            AppSourceApplicationService.OperationSnapshot operation) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("operationId", operation.operationId());
        message.put("operation", AppSourceDtos.operation(operation));
        message.put("traceId", operation.traceId());
        return message;
    }

    private Map<String, Object> failed(String operationId, String errorCode, String errorMessage) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "failed");
        message.put("operationId", operationId);
        message.put("status", AppSourceOperationStatus.FAILED.name());
        message.put("errorCode", errorCode);
        message.put("errorMessage", errorMessage);
        return message;
    }

    private Mono<Void> sendAndClose(WebSocketSession session, Map<String, Object> payload) {
        return session.send(Mono.just(session.textMessage(write(payload))))
                .then(session.close());
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return "{\"type\":\"failed\",\"status\":\"FAILED\","
                    + "\"errorCode\":\"INTERNAL_ERROR\",\"errorMessage\":\"应用源码进度序列化失败\"}";
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
        String marker = "/app-source-operations/";
        int start = path.indexOf(marker);
        int end = path.lastIndexOf("/ws");
        if (start < 0 || end <= start) {
            return "";
        }
        return path.substring(start + marker.length(), end);
    }
}
