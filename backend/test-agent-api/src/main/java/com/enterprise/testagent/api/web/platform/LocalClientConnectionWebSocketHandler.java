package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionSender;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientRegistrationService;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientTunnelGateway;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/** 本地客户端反向 WSS 入口；首帧认证，后续所有帧按 connection generation fencing。 */
@Component
public class LocalClientConnectionWebSocketHandler implements WebSocketHandler {

    public static final String PATH = "/api/internal/platform/local-opencode-client/connections/ws";
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientConnectionWebSocketHandler.class);

    private final LocalClientCredentialApplicationService credentialService;
    private final LocalClientRegistrationService registrationService;
    private final LocalClientConnectionRegistry connectionRegistry;
    private final LocalClientConnectionSupersessionService supersessionService;
    private final LocalClientTunnelGateway tunnelGateway;
    private final BackendJavaRouteResolver routeResolver;
    private final OpencodeProcessStartupService startupService;
    private final LocalClientControlSecuritySettings securitySettings;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    public LocalClientConnectionWebSocketHandler(
            LocalClientCredentialApplicationService credentialService,
            LocalClientRegistrationService registrationService,
            LocalClientConnectionRegistry connectionRegistry,
            LocalClientConnectionSupersessionService supersessionService,
            LocalClientTunnelGateway tunnelGateway,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStartupService startupService,
            LocalClientControlSecuritySettings securitySettings) {
        this.credentialService = Objects.requireNonNull(credentialService);
        this.registrationService = Objects.requireNonNull(registrationService);
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry);
        this.supersessionService = Objects.requireNonNull(supersessionService);
        this.tunnelGateway = Objects.requireNonNull(tunnelGateway);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.startupService = Objects.requireNonNull(startupService);
        this.securitySettings = Objects.requireNonNull(securitySettings);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        try {
            securitySettings.requireSecure(
                    session.getHandshakeInfo().getUri(),
                    session.getHandshakeInfo().getHeaders(),
                    session.getHandshakeInfo().getRemoteAddress());
        } catch (PlatformException exception) {
            return session.close(new CloseStatus(1008, "SECURE_TRANSPORT_REQUIRED"));
        }
        String handshakeTraceId = TraceIdSupport.resolve(
                session.getHandshakeInfo().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(128));
        Sinks.One<String> closeSignal = Sinks.one();
        AtomicReference<ConnectionState> stateRef = new AtomicReference<>();
        LocalClientConnectionSender sender = new LocalClientConnectionSender() {
            @Override
            public void send(LocalClientFrame frame) {
                emit(outbound, frame, closeSignal);
            }

            @Override
            public void close(String reason) {
                closeSignal.tryEmitValue(reason == null ? "CONNECTION_CLOSED" : reason);
            }
        };

        Mono<Void> inbound = session.receive()
                .concatMap(message -> receive(
                        message,
                        outbound,
                        closeSignal,
                        stateRef,
                        sender,
                        handshakeTraceId,
                        observedAddress(session)))
                .onErrorResume(exception -> {
                    ConnectionState state = stateRef.get();
                    String traceId = state == null ? handshakeTraceId : state.traceId();
                    LOGGER.warn(
                            "local_client_websocket_failed clientInstanceId={} generation={} traceId={}",
                            state == null ? null : state.clientInstanceId().value(),
                            state == null ? null : state.generation(),
                            traceId,
                            exception);
                    emitError(outbound, closeSignal, state, traceId, exception);
                    closeSignal.tryEmitValue("PROTOCOL_ERROR");
                    return Mono.empty();
                })
                .doFinally(ignored -> cleanup(stateRef.get(), outbound))
                .then();
        Mono<Void> outboundTransport = session.send(outbound.asFlux()
                .map(codec::encode)
                .map(session::textMessage));
        Mono<Void> normalTransport = Mono.when(inbound, outboundTransport);
        Mono<Void> forcedClose = closeSignal.asMono()
                .flatMap(reason -> session.close(new CloseStatus(1008, safeCloseReason(reason))));
        return Mono.firstWithSignal(normalTransport, forcedClose).then();
    }

    private Mono<Void> receive(
            WebSocketMessage message,
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            AtomicReference<ConnectionState> stateRef,
            LocalClientConnectionSender sender,
            String handshakeTraceId,
            String observedAddress) {
        if (message.getType() != WebSocketMessage.Type.TEXT) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端只接受文本协议帧"));
        }
        LocalClientFrame frame = codec.decode(message.getPayloadAsText());
        ConnectionState state = stateRef.get();
        if (state == null) {
            if (frame.type() != LocalClientFrameType.REGISTER) {
                return Mono.error(new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端首帧必须完成注册认证"));
            }
            return register(frame, outbound, closeSignal, stateRef, sender, handshakeTraceId, observedAddress);
        }
        return handleAuthenticated(frame, outbound, closeSignal, state);
    }

    private Mono<Void> register(
            LocalClientFrame frame,
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            AtomicReference<ConnectionState> stateRef,
            LocalClientConnectionSender sender,
            String handshakeTraceId,
            String observedAddress) {
        LocalClientPayloads.Register payload = codec.payload(frame, LocalClientPayloads.Register.class);
        return Mono.fromCallable(() -> {
                    var authenticated = credentialService.authenticate(payload.clientKey());
                    UserId userId = new UserId(authenticated.userId());
                    LocalClientRegistrationService.Registration registration = registrationService.register(
                            userId,
                            payload,
                            routeResolver.currentBackendProcessId(),
                            observedAddress);
                    supersessionService.supersede(
                            registration.previousRoute(), registration.route(), frame.traceId());
                    ConnectionState state = new ConnectionState(
                            userId,
                            registration.route().clientInstanceId(),
                            registration.route().connectionGeneration(),
                            registration.modelGrantFingerprint(),
                            frame.traceId());
                    if (!stateRef.compareAndSet(null, state)) {
                        throw new PlatformException(ErrorCode.CONFLICT, "本地客户端重复注册");
                    }
                    connectionRegistry.register(
                            state.clientInstanceId(),
                            state.userId(),
                            state.generation(),
                            state.modelGrantFingerprint(),
                            sender);
                    emit(outbound, new LocalClientFrame(
                            LocalClientProtocol.VERSION,
                            LocalClientFrameType.REGISTERED,
                            frame.requestId(),
                            handshakeTraceId,
                            state.generation(),
                            codec.payload(new LocalClientPayloads.Registered(
                                    state.generation(),
                                    registration.rawModelGrant(),
                                    registration.modelGrantExpiresAt(),
                                    Instant.now()))), closeSignal);
                    LOGGER.info(
                            "local_client_connected clientInstanceId={} userId={} generation={} backendProcessId={} traceId={}",
                            state.clientInstanceId().value(),
                            state.userId().value(),
                            state.generation(),
                            routeResolver.currentBackendProcessIdValue(),
                            frame.traceId());
                    autoStart(state);
                    return state;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /** 注册响应入队后异步走公共启动入口，避免阻塞同一 WebSocket 入站流等待生命周期回包。 */
    private void autoStart(ConnectionState state) {
        Mono.fromRunnable(() -> startupService.startLocalClientAndVerify(
                        state.clientInstanceId(), state.generation(), state.traceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> LOGGER.info(
                                "local_client_auto_start_succeeded clientInstanceId={} generation={} traceId={}",
                                state.clientInstanceId().value(), state.generation(), state.traceId()),
                        error -> LOGGER.warn(
                                "local_client_auto_start_failed clientInstanceId={} generation={} traceId={}",
                                state.clientInstanceId().value(), state.generation(), state.traceId(), error));
    }

    private Mono<Void> handleAuthenticated(
            LocalClientFrame frame,
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            ConnectionState state) {
        if (frame.connectionGeneration() == null || frame.connectionGeneration() != state.generation()) {
            return Mono.error(new PlatformException(ErrorCode.CONFLICT, "本地客户端帧 generation 已失效"));
        }
        if (frame.type() == LocalClientFrameType.HEARTBEAT) {
            LocalClientPayloads.Heartbeat heartbeat = codec.payload(frame, LocalClientPayloads.Heartbeat.class);
            return Mono.fromCallable(() -> registrationService.heartbeat(
                            state.clientInstanceId(),
                            state.generation(),
                            routeResolver.currentBackendProcessId(),
                            state.modelGrantFingerprint(),
                            heartbeat))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(route -> emit(outbound, new LocalClientFrame(
                            LocalClientProtocol.VERSION,
                            LocalClientFrameType.HEARTBEAT_ACK,
                            frame.requestId(),
                            frame.traceId(),
                            state.generation(),
                            codec.payload(new LocalClientPayloads.HeartbeatAck(
                                    Instant.now(),
                                    Instant.now().plus(LocalClientProtocol.CONNECTION_TTL)))), closeSignal))
                    .then();
        }
        if (frame.type() == LocalClientFrameType.REGISTER
                || frame.type() == LocalClientFrameType.REGISTERED
                || frame.type() == LocalClientFrameType.HEARTBEAT_ACK
                || frame.type() == LocalClientFrameType.LIFECYCLE_COMMAND
                || frame.type() == LocalClientFrameType.HTTP_REQUEST
                || frame.type() == LocalClientFrameType.FILE_REQUEST
                || frame.type() == LocalClientFrameType.CANCEL
                || frame.type() == LocalClientFrameType.MODEL_GRANT) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端发送了不允许的帧类型"));
        }
        if (!tunnelGateway.accept(state.clientInstanceId(), frame)) {
            LOGGER.debug(
                    "local_client_orphan_response clientInstanceId={} generation={} requestId={} type={}",
                    state.clientInstanceId().value(), state.generation(), frame.requestId(), frame.type());
        }
        return Mono.empty();
    }

    private void cleanup(ConnectionState state, Sinks.Many<LocalClientFrame> outbound) {
        complete(outbound);
        if (state == null) {
            return;
        }
        connectionRegistry.disconnect(state.clientInstanceId(), state.generation());
        tunnelGateway.failConnection(
                state.clientInstanceId(),
                state.generation(),
                new PlatformException(ErrorCode.LOCAL_CLIENT_DISCONNECTED, "本地客户端连接已断开"));
        Mono.fromRunnable(() -> registrationService.disconnect(state.clientInstanceId(), state.generation()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        LOGGER.info(
                "local_client_disconnected clientInstanceId={} userId={} generation={} traceId={}",
                state.clientInstanceId().value(),
                state.userId().value(),
                state.generation(),
                state.traceId());
    }

    private void emitError(
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            ConnectionState state,
            String traceId,
            Throwable error) {
        ErrorCode errorCode = error instanceof PlatformException platformException
                ? platformException.errorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = error instanceof PlatformException
                ? error.getMessage()
                : "本地客户端连接处理失败";
        Long generation = state == null ? null : state.generation();
        try {
            emit(outbound, new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.ERROR,
                    "lce_" + Long.toUnsignedString(System.nanoTime()),
                    traceId,
                    generation,
                    codec.payload(new LocalClientPayloads.Error(
                            errorCode.name(), message, false, Map.of()))), closeSignal);
        } catch (RuntimeException ignored) {
            // 发送队列已经关闭时直接进入 WebSocket close。
        }
    }

    private static void emit(
            Sinks.Many<LocalClientFrame> outbound,
            LocalClientFrame frame,
            Sinks.One<String> closeSignal) {
        synchronized (outbound) {
            Sinks.EmitResult result = outbound.tryEmitNext(frame);
            if (result != Sinks.EmitResult.OK) {
                closeSignal.tryEmitValue("OUTBOUND_BACKPRESSURE");
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端发送队列不可用");
            }
        }
    }

    private static void complete(Sinks.Many<LocalClientFrame> outbound) {
        synchronized (outbound) {
            outbound.tryEmitComplete();
        }
    }

    private static String observedAddress(WebSocketSession session) {
        InetSocketAddress address = session.getHandshakeInfo().getRemoteAddress();
        return address == null ? null : address.getHostString() + ":" + address.getPort();
    }

    private static String safeCloseReason(String reason) {
        String resolved = reason == null || reason.isBlank() ? "CONNECTION_CLOSED" : reason;
        return resolved.substring(0, Math.min(100, resolved.length()));
    }

    private record ConnectionState(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long generation,
            String modelGrantFingerprint,
            String traceId) {
    }
}
