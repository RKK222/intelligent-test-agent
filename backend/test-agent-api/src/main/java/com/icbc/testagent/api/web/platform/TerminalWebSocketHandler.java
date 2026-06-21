package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.opencode.runtime.terminal.TerminalActiveSessionRegistry;
import com.icbc.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.icbc.testagent.opencode.runtime.terminal.TerminalAuditLogger;
import com.icbc.testagent.opencode.runtime.terminal.TerminalClientMessage;
import com.icbc.testagent.opencode.runtime.terminal.TerminalInputRateLimiter;
import com.icbc.testagent.opencode.runtime.terminal.TerminalMessageCodec;
import com.icbc.testagent.opencode.runtime.terminal.TerminalProcessFactory;
import com.icbc.testagent.opencode.runtime.terminal.TerminalProcessSession;
import com.icbc.testagent.opencode.runtime.terminal.TerminalServerMessage;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicket;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

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
    private final int maxInputBytes;
    private final int inputMessagesPerWindow;
    private final int resizeMessagesPerWindow;
    private final Duration rateLimitWindow;
    private final Duration idleTimeout;
    private final Duration hardTimeout;
    private final TerminalAuditLogger auditLogger;

    /**
     * 装配终端 WebSocket 依赖和安全阈值，所有可配置数值都会规整到正数。
     */
    public TerminalWebSocketHandler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalMessageCodec codec,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins,
            @Value("${test-agent.terminal.max-input-bytes:16384}") int maxInputBytes,
            @Value("${test-agent.terminal.input-messages-per-window:64}") int inputMessagesPerWindow,
            @Value("${test-agent.terminal.resize-messages-per-window:10}") int resizeMessagesPerWindow,
            @Value("${test-agent.terminal.rate-limit-window:1s}") Duration rateLimitWindow,
            @Value("${test-agent.terminal.idle-timeout:10m}") Duration idleTimeout,
            @Value("${test-agent.terminal.hard-timeout:2h}") Duration hardTimeout,
            TerminalActiveSessionRegistry activeSessions,
            TerminalAuditLogger auditLogger) {
        this.terminalService = terminalService;
        this.processFactory = processFactory;
        this.codec = codec;
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        this.activeSessions = activeSessions;
        this.maxInputBytes = Math.max(1, maxInputBytes);
        this.inputMessagesPerWindow = Math.max(1, inputMessagesPerWindow);
        this.resizeMessagesPerWindow = Math.max(1, resizeMessagesPerWindow);
        this.rateLimitWindow = positive(rateLimitWindow, Duration.ofSeconds(1));
        this.idleTimeout = positive(idleTimeout, Duration.ofMinutes(10));
        this.hardTimeout = positive(hardTimeout, Duration.ofHours(2));
        this.auditLogger = auditLogger;
    }

    /**
     * 处理终端 WebSocket 生命周期：校验来源、消费 ticket、启动 PTY、转发输入输出并释放租约。
     */
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
            auditLogger.upgradeAccepted(ticket);
        } catch (PlatformException exception) {
            auditLogger.upgradeRejected(ticket, exception.errorCode().name());
            if (lease != null) {
                lease.close();
            }
            return sendErrorAndClose(session, exception.errorCode().name(), exception.getMessage());
        } catch (Exception exception) {
            auditLogger.upgradeRejected(ticket, "PTY_UNAVAILABLE");
            if (lease != null) {
                lease.close();
            }
            return sendErrorAndClose(session, "PTY_UNAVAILABLE", "terminal unavailable");
        }
        TerminalActiveSessionRegistry.Lease activeLease = lease;
        TerminalProcessSession activeTerminal = terminal;
        AtomicBoolean terminalClosed = new AtomicBoolean(false);
        Sinks.Many<TerminalServerMessage> controlMessages = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<Long> activity = Sinks.many().multicast().directBestEffort();
        TerminalInputRateLimiter inputRateLimiter = new TerminalInputRateLimiter(
                Clock.systemUTC(),
                maxInputBytes,
                inputMessagesPerWindow,
                resizeMessagesPerWindow,
                rateLimitWindow);
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(codec::decode)
                .doOnNext(ignored -> activity.tryEmitNext(System.nanoTime()))
                .concatMap(message -> handleClientMessage(ticket, session, activeTerminal, message, inputRateLimiter, controlMessages, terminalClosed))
                .onErrorResume(TerminalConnectionClosed.class, ignored -> Mono.empty())
                .doFinally(ignored -> {
                    controlMessages.tryEmitComplete();
                    activity.tryEmitComplete();
                    closeTerminal(activeTerminal, terminalClosed).subscribe();
                })
                .then();
        Mono<Void> outbound = session.send(activeTerminal.output()
                .doOnNext(message -> auditTerminalOutput(ticket, message))
                .mergeWith(controlMessages.asFlux())
                .map(codec::encode)
                .map(session::textMessage));
        Mono<Void> main = Mono.when(inbound, outbound);
        Mono<Void> timeout = timeout(ticket, session, activeTerminal, controlMessages, terminalClosed, activity);
        return Mono.firstWithSignal(main, timeout)
                .onErrorResume(TerminalConnectionClosed.class, ignored -> Mono.empty())
                .doFinally(ignored -> {
                    activeLease.close();
                    closeTerminal(activeTerminal, terminalClosed).subscribe();
                });
    }

    /**
     * 处理单条前端消息，非法类型或限流失败都会关闭 PTY 以收紧安全边界。
     */
    private Mono<Void> handleClientMessage(
            TerminalTicket ticket,
            WebSocketSession session,
            TerminalProcessSession terminal,
            TerminalClientMessage message,
            TerminalInputRateLimiter inputRateLimiter,
            Sinks.Many<TerminalServerMessage> controlMessages,
            AtomicBoolean terminalClosed) {
        if (!supported(message)) {
            controlMessages.tryEmitNext(TerminalServerMessage.error("VALIDATION_ERROR", "invalid terminal message"));
            controlMessages.tryEmitComplete();
            return closeTerminal(terminal, terminalClosed)
                    .then(session.close())
                    .then(Mono.error(new TerminalConnectionClosed()));
        }
        TerminalInputRateLimiter.Decision decision = inputRateLimiter.check(message);
        if (!decision.allowed()) {
            auditLogger.inputRejected(ticket, decision.code(), inputBytes(message));
            controlMessages.tryEmitNext(TerminalServerMessage.error(decision.code(), decision.message()));
            controlMessages.tryEmitComplete();
            return closeTerminal(terminal, terminalClosed)
                    .then(session.close())
                    .then(Mono.error(new TerminalConnectionClosed()));
        }
        if ("input".equals(message.type())) {
            auditLogger.input(ticket, inputBytes(message));
            return terminal.input(message.data());
        }
        if ("resize".equals(message.type())) {
            auditLogger.resize(ticket, message.cols(), message.rows());
            return terminal.resize(message.cols(), message.rows());
        }
        if ("close".equals(message.type())) {
            auditLogger.close(ticket, message.reason());
            return closeTerminal(terminal, terminalClosed);
        }
        return Mono.empty();
    }

    /**
     * 只允许 input、resize、close 三类客户端消息进入 PTY。
     */
    private boolean supported(TerminalClientMessage message) {
        if (message == null || message.type() == null) {
            return false;
        }
        return "input".equals(message.type()) || "resize".equals(message.type()) || "close".equals(message.type());
    }

    /**
     * 幂等关闭 PTY，避免 inbound/outbound/timeout 多路同时结束时重复销毁进程。
     */
    private Mono<Void> closeTerminal(TerminalProcessSession terminal, AtomicBoolean terminalClosed) {
        if (!terminalClosed.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return terminal.close();
    }

    /**
     * 合并空闲超时与硬超时，先触发者会向前端发送错误并关闭连接。
     */
    private Mono<Void> timeout(
            TerminalTicket ticket,
            WebSocketSession session,
            TerminalProcessSession terminal,
            Sinks.Many<TerminalServerMessage> controlMessages,
            AtomicBoolean terminalClosed,
            Sinks.Many<Long> activity) {
        Mono<String> idleTimeoutSignal = activity.asFlux()
                .startWith(0L)
                .switchMap(ignored -> Mono.delay(idleTimeout))
                .next()
                .thenReturn("terminal idle timeout");
        Mono<String> hardTimeoutSignal = Mono.delay(hardTimeout)
                .thenReturn("terminal hard timeout");
        return Mono.firstWithSignal(idleTimeoutSignal, hardTimeoutSignal)
                .flatMap(message -> {
                    auditLogger.timeout(ticket, message);
                    controlMessages.tryEmitNext(TerminalServerMessage.error("PTY_TIMEOUT", message));
                    controlMessages.tryEmitComplete();
                    return closeTerminal(terminal, terminalClosed)
                            .then(session.close())
                            .then(Mono.error(new TerminalConnectionClosed()));
                });
    }

    /**
     * 将无效配置值替换为默认值，避免配置错误导致超时策略失效。
     */
    private Duration positive(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    /**
     * 计算输入消息 UTF-8 字节数，用于限流和审计。
     */
    private int inputBytes(TerminalClientMessage message) {
        return message == null || message.data() == null ? 0 : message.data().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 只审计 PTY exit envelope，普通输出内容不进入业务日志。
     */
    private void auditTerminalOutput(TerminalTicket ticket, TerminalServerMessage message) {
        if ("exit".equals(message.type()) && message.exitCode() != null) {
            auditLogger.exit(ticket, message.exitCode());
        }
    }

    /**
     * 在 upgrade 拒绝或启动失败时先发送统一错误 envelope，再关闭 WebSocket。
     */
    private Mono<Void> sendErrorAndClose(WebSocketSession session, String code, String message) {
        return session.send(Mono.just(session.textMessage(codec.encode(TerminalServerMessage.error(code, message)))))
                .then(session.close());
    }

    /**
     * 从 WebSocket 路径提取 sessionId，避免信任 ticket 之外的任意查询参数。
     */
    private SessionId sessionId(String path) {
        List<String> segments = List.of(path.split("/"));
        int index = segments.indexOf("sessions");
        if (index < 0 || index + 1 >= segments.size()) {
            throw new IllegalArgumentException("missing session id");
        }
        return new SessionId(segments.get(index + 1));
    }

    /**
     * 读取原始 query 参数值，ticket 本身由 TerminalApplicationService 做一次性消费校验。
     */
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

    /**
     * 从 WebSocket handshake header 解析 traceId，缺失时生成新的链路 ID。
     */
    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    /**
     * 内部控制异常，用于终止 reactive 链路且不再额外记录错误响应。
     */
    private static final class TerminalConnectionClosed extends RuntimeException {
    }
}
