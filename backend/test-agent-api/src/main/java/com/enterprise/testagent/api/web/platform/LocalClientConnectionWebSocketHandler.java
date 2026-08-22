package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
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
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final LocalClientUpdateCoordinator updateCoordinator;
    private final LocalWorkspaceApplicationService workspaceService;
    private final LocalClientControlSecuritySettings securitySettings;
    private final LocalClientAuthenticationRateLimiter authenticationRateLimiter;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private TraceArchiveService traceArchiveService;
    private UserRepository userRepository;
    private TraceCatalogRepository traceCatalogRepository;
    private BackendHttpForwarder backendHttpForwarder;
    private ManagerControlSettings managerSettings;

    /** 方法注入保持既有 handler 单测构造器兼容，同时让生产连接具备 Trace 归档能力。 */
    @Autowired
    void setObservabilityServices(
            TraceArchiveService traceArchiveService,
            UserRepository userRepository,
            TraceCatalogRepository traceCatalogRepository,
            BackendHttpForwarder backendHttpForwarder,
            ManagerControlSettings managerSettings) {
        this.traceArchiveService = Objects.requireNonNull(traceArchiveService);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.traceCatalogRepository = Objects.requireNonNull(traceCatalogRepository);
        this.backendHttpForwarder = Objects.requireNonNull(backendHttpForwarder);
        this.managerSettings = Objects.requireNonNull(managerSettings);
    }

    public LocalClientConnectionWebSocketHandler(
            LocalClientCredentialApplicationService credentialService,
            LocalClientRegistrationService registrationService,
            LocalClientConnectionRegistry connectionRegistry,
            LocalClientConnectionSupersessionService supersessionService,
            LocalClientTunnelGateway tunnelGateway,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStartupService startupService,
            LocalClientUpdateCoordinator updateCoordinator,
            LocalWorkspaceApplicationService workspaceService,
            LocalClientControlSecuritySettings securitySettings,
            LocalClientAuthenticationRateLimiter authenticationRateLimiter) {
        this.credentialService = Objects.requireNonNull(credentialService);
        this.registrationService = Objects.requireNonNull(registrationService);
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry);
        this.supersessionService = Objects.requireNonNull(supersessionService);
        this.tunnelGateway = Objects.requireNonNull(tunnelGateway);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.startupService = Objects.requireNonNull(startupService);
        this.updateCoordinator = Objects.requireNonNull(updateCoordinator);
        this.workspaceService = Objects.requireNonNull(workspaceService);
        this.securitySettings = Objects.requireNonNull(securitySettings);
        this.authenticationRateLimiter = Objects.requireNonNull(authenticationRateLimiter);
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
        String clientAddress = securitySettings.resolveClientAddress(
                session.getHandshakeInfo().getHeaders(), session.getHandshakeInfo().getRemoteAddress());
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
                        clientAddress))
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
            String clientAddress) {
        if (message.getType() != WebSocketMessage.Type.TEXT) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端只接受文本协议帧"));
        }
        LocalClientFrame frame = codec.decode(message.getPayloadAsText());
        ConnectionState state = stateRef.get();
        if (state == null) {
            if (frame.type() != LocalClientFrameType.REGISTER) {
                return Mono.error(new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端首帧必须完成注册认证"));
            }
            return register(frame, outbound, closeSignal, stateRef, sender, handshakeTraceId, clientAddress);
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
            String clientAddress) {
        LocalClientPayloads.Register payload = codec.payload(frame, LocalClientPayloads.Register.class);
        boolean strictLegacyRegistration = LocalClientPayloads.isStrictLegacyRegister(frame.payload(), payload);
        return Mono.fromCallable(() -> {
                    authenticationRateLimiter.acquire(clientAddress);
                    var authenticated = credentialService.authenticate(
                            payload.clientKey(), payload.unifiedAuthId(), strictLegacyRegistration);
                    authenticationRateLimiter.authenticationSucceeded(clientAddress);
                    UserId userId = new UserId(authenticated.userId());
                    LocalClientRegistrationService.Registration registration = registrationService.register(
                            userId,
                            payload,
                            routeResolver.currentBackendProcessId(),
                            clientAddress);
                    supersessionService.supersede(
                            registration.previousRoute(), registration.route(), frame.traceId());
                    ConnectionState state = new ConnectionState(
                            userId,
                            registration.route().clientInstanceId(),
                            registration.route().connectionGeneration(),
                            registration.modelGrantFingerprint(),
                            frame.traceId(),
                            registration.selfUpdateSupported(),
                            payload.capabilities() != null
                                    && payload.capabilities().contains("OPENCODE_OBSERVABILITY_V1"),
                            ConcurrentHashMap.newKeySet(),
                            new ConcurrentHashMap<>());
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

    Mono<Void> handleAuthenticated(
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
        return switch (frame.type()) {
            case VERSION_CHECK -> blockingUpdate(() -> updateCoordinator.handleVersionCheck(
                    requireSelfUpdate(state),
                    state.clientInstanceId(),
                    state.generation(),
                    codec.payload(frame, LocalClientPayloads.VersionCheck.class),
                    frame.traceId()));
            case UPDATE_PREPARED -> blockingUpdate(() -> updateCoordinator.handlePrepared(
                    requireSelfUpdate(state),
                    state.clientInstanceId(),
                    state.generation(),
                    codec.payload(frame, LocalClientPayloads.UpdatePrepared.class),
                    frame.traceId()));
            case UPDATE_STATUS -> {
                LocalClientPayloads.UpdateStatus status = codec.payload(frame, LocalClientPayloads.UpdateStatus.class);
                yield Mono.fromCallable(() -> updateCoordinator.handleStatus(
                                requireSelfUpdate(state),
                                state.clientInstanceId(),
                                state.generation(),
                                status,
                                frame.traceId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(persistedAck -> emit(outbound, new LocalClientFrame(
                                LocalClientProtocol.VERSION,
                                LocalClientFrameType.UPDATE_STATUS_ACK,
                                frame.requestId(),
                                frame.traceId(),
                                state.generation(),
                                codec.payload(persistedAck)), closeSignal))
                        .then();
            }
            case WORKSPACE_REGISTER -> registerWorkspace(frame, outbound, closeSignal, state);
            case OBSERVABILITY_BATCH -> declareObservabilityBatch(frame, state);
            case TRACE_CHUNK_UPLOAD -> uploadTraceChunk(frame, outbound, closeSignal, state);
            case LIFECYCLE_RESULT, HTTP_RESPONSE, STREAM_OPEN, STREAM_CHUNK, STREAM_END,
                    FILE_RESPONSE, BINARY_CHUNK, ERROR -> acceptTunnelResponse(state, frame);
            default -> Mono.error(new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "客户端发送了不允许的帧类型"));
        };
    }

    private Mono<Void> declareObservabilityBatch(LocalClientFrame frame, ConnectionState state) {
        requireObservability(state);
        LocalClientPayloads.ObservabilityBatch batch = codec.payload(
                frame, LocalClientPayloads.ObservabilityBatch.class);
        validateObservabilityCoordinates(
                state, frame, batch.clientInstanceId(), batch.connectionGeneration(), batch.traceId());
        if (state.traceDeclarations().size() >= 8
                || batch.batchId() == null
                || !batch.batchId().matches("obs_[a-f0-9]{32}")
                || batch.runtimeGeneration() == null
                || batch.runtimeGeneration().isBlank()
                || batch.firstSequence() < 1
                || batch.lastSequence() < batch.firstSequence()
                || batch.contentLength() < 1
                || batch.contentLength() > 256L * 1024L
                || batch.sha256() == null
                || !batch.sha256().matches("[a-f0-9]{64}")) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "Observability 分片声明无效"));
        }
        LocalClientPayloads.ObservabilityBatch previous = state.traceDeclarations()
                .putIfAbsent(frame.requestId(), batch);
        if (previous != null && !previous.equals(batch)) {
            return Mono.error(new PlatformException(ErrorCode.CONFLICT, "Observability requestId 已被占用"));
        }
        return Mono.empty();
    }

    private Mono<Void> uploadTraceChunk(
            LocalClientFrame frame,
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            ConnectionState state) {
        requireObservability(state);
        LocalClientPayloads.ObservabilityBatch declaration = state.traceDeclarations().get(frame.requestId());
        LocalClientPayloads.TraceChunkUpload upload = codec.payload(
                frame, LocalClientPayloads.TraceChunkUpload.class);
        validateObservabilityCoordinates(
                state, frame, upload.clientInstanceId(), upload.connectionGeneration(), upload.traceId());
        if (declaration == null
                || !declaration.batchId().equals(upload.batchId())
                || declaration.firstSequence() != upload.firstSequence()
                || declaration.lastSequence() != upload.lastSequence()
                || !declaration.sha256().equals(upload.sha256())) {
            return Mono.error(new PlatformException(ErrorCode.CONFLICT, "Trace 上传缺少匹配的批次声明"));
        }
        byte[] body;
        try {
            body = Base64.getDecoder().decode(upload.dataBase64());
        } catch (IllegalArgumentException exception) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分片 Base64 无效"));
        }
        if (body.length != declaration.contentLength()) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分片长度与声明不一致"));
        }
        return Mono.fromCallable(() -> {
                    var user = userRepository.findByUserId(state.userId())
                            .filter(com.enterprise.testagent.domain.user.User::canLogin)
                            .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端用户已停用"));
                    var identity = new OpencodeObservabilityModels.IngestionIdentity(
                            user, null, state.clientInstanceId().value(), declaration.runtimeGeneration());
                    var runtime = new OpencodeObservabilityModels.RuntimeIdentity(
                            declaration.runtimeKind(), declaration.runtimeGeneration(), null, null,
                            state.clientInstanceId().value());
                    return archiveLocalOrForward(
                            identity,
                            runtime,
                            declaration.coverageStartAt(),
                            declaration.droppedCount(),
                            declaration.complete(),
                            declaration.traceId(),
                            declaration.firstSequence(),
                            declaration.lastSequence(),
                            declaration.sha256(),
                            body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(ack -> {
                    state.traceDeclarations().remove(frame.requestId(), declaration);
                    emit(outbound, new LocalClientFrame(
                            LocalClientProtocol.VERSION,
                            LocalClientFrameType.TRACE_CHUNK_ACK,
                            frame.requestId(),
                            frame.traceId(),
                            state.generation(),
                            codec.payload(new LocalClientPayloads.TraceChunkAck(
                                    declaration.batchId(),
                                    state.clientInstanceId().value(),
                                    state.generation(),
                                    ack.traceId(),
                                    ack.firstSequence(),
                                    ack.lastSequence(),
                                    ack.sha256(),
                                    ack.completeThrough(),
                                    ack.archiveStatus(),
                                    ack.archivedAt()))), closeSignal);
                    emit(outbound, new LocalClientFrame(
                            LocalClientProtocol.VERSION,
                            LocalClientFrameType.TRACE_UPLOAD_WATERMARK,
                            frame.requestId() + "_wm",
                            frame.traceId(),
                            state.generation(),
                            codec.payload(new LocalClientPayloads.TraceUploadWatermark(
                                    state.clientInstanceId().value(),
                                    state.generation(),
                                    ack.traceId(),
                                    ack.completeThrough(),
                                    Instant.now()))), closeSignal);
                })
                .doOnError(error -> {
                    state.traceDeclarations().remove(frame.requestId(), declaration);
                    emitRequestError(outbound, closeSignal, state, frame, error);
                })
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    /** 首次归档留在当前连接 Java；后续重连若落到其它 Java，统一转发到冻结的 owner。 */
    private OpencodeObservabilityModels.TraceAck archiveLocalOrForward(
            OpencodeObservabilityModels.IngestionIdentity identity,
            OpencodeObservabilityModels.RuntimeIdentity runtime,
            Instant coverageStartAt,
            long droppedCount,
            boolean complete,
            String traceId,
            long firstSequence,
            long lastSequence,
            String sha256,
            byte[] body) {
        var existing = traceCatalogRepository.find(traceId);
        if (existing.isPresent()) {
            BackendProcessId owner = new BackendProcessId(existing.get().backendProcessId());
            if (!routeResolver.isCurrent(owner)) {
                var backend = routeResolver.requireBackend(owner);
                var request = new OpencodeObservabilityModels.InternalTraceChunk(
                        identity.user().userId().value(),
                        identity.clientInstanceId(),
                        identity.generation(),
                        runtime,
                        coverageStartAt,
                        droppedCount,
                        complete,
                        traceId,
                        firstSequence,
                        lastSequence,
                        sha256,
                        Base64.getEncoder().encodeToString(body));
                return backendHttpForwarder.forwardSystemTyped(
                        backend,
                        "/api/internal/agent/opencode-observability/v1/traces/"
                                + traceId + "/chunks/" + firstSequence,
                        "PUT",
                        request,
                        new TypeReference<ApiResponse<OpencodeObservabilityModels.TraceAck>>() { },
                        traceId,
                        "Bearer " + managerSettings.token()).data();
            }
        }
        return traceArchiveService.ingestLocalChunk(
                identity,
                runtime,
                coverageStartAt,
                droppedCount,
                complete,
                traceId,
                firstSequence,
                lastSequence,
                sha256,
                body);
    }

    private void validateObservabilityCoordinates(
            ConnectionState state,
            LocalClientFrame frame,
            String clientInstanceId,
            long connectionGeneration,
            String traceId) {
        if (!state.clientInstanceId().value().equals(clientInstanceId)
                || state.generation() != connectionGeneration
                || !frame.traceId().equals(traceId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Observability 连接坐标已失效");
        }
    }

    private void requireObservability(ConnectionState state) {
        if (!state.observabilitySupported() || traceArchiveService == null || userRepository == null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端未声明 OPENCODE_OBSERVABILITY_V1 能力");
        }
    }

    /**
     * 客户端主动注册不能占住当前 WebSocket 的 concatMap：业务服务会反向发出两个 FILE_REQUEST，
     * 必须先释放入站流才能继续接收对应 FILE_RESPONSE。
     */
    private Mono<Void> registerWorkspace(
            LocalClientFrame frame,
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            ConnectionState state) {
        if (state.workspaceRequestIds().size() >= 4 || !state.workspaceRequestIds().add(frame.requestId())) {
            emitRequestError(outbound, closeSignal, state, frame, new PlatformException(
                    ErrorCode.CONFLICT, "本地工作区注册请求正在处理中"));
            return Mono.empty();
        }
        LocalClientPayloads.WorkspaceRegister request = codec.payload(
                frame, LocalClientPayloads.WorkspaceRegister.class);
        Mono.fromCallable(() -> workspaceService.create(
                        state.userId(),
                        state.clientInstanceId(),
                        request.name(),
                        request.rootPath(),
                        frame.traceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(ignored -> state.workspaceRequestIds().remove(frame.requestId()))
                .subscribe(
                        workspace -> emit(outbound, new LocalClientFrame(
                                LocalClientProtocol.VERSION,
                                LocalClientFrameType.WORKSPACE_REGISTERED,
                                frame.requestId(),
                                frame.traceId(),
                                state.generation(),
                                codec.payload(new LocalClientPayloads.WorkspaceRegistered(
                                        workspace.workspaceId(), workspace.name(), workspace.rootPath()))), closeSignal),
                        error -> emitRequestError(outbound, closeSignal, state, frame, error));
        return Mono.empty();
    }

    private Mono<Void> blockingUpdate(Runnable action) {
        return Mono.fromRunnable(action).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private static UserId requireSelfUpdate(ConnectionState state) {
        if (!state.selfUpdateSupported()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端未声明 SELF_UPDATE_V1 能力");
        }
        return state.userId();
    }

    private Mono<Void> acceptTunnelResponse(ConnectionState state, LocalClientFrame frame) {
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

    /** 业务请求失败只终止对应注册操作，不关闭已认证的客户端连接。 */
    private void emitRequestError(
            Sinks.Many<LocalClientFrame> outbound,
            Sinks.One<String> closeSignal,
            ConnectionState state,
            LocalClientFrame request,
            Throwable error) {
        ErrorCode errorCode = error instanceof PlatformException platformException
                ? platformException.errorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = error instanceof PlatformException
                ? error.getMessage()
                : "本地工作区注册失败";
        try {
            emit(outbound, new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.ERROR,
                    request.requestId(),
                    request.traceId(),
                    state.generation(),
                    codec.payload(new LocalClientPayloads.Error(
                            errorCode.name(), message, false, Map.of()))), closeSignal);
        } catch (RuntimeException ignored) {
            // 连接已结束时由客户端断线处理收敛等待中的注册请求。
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

    private static String safeCloseReason(String reason) {
        String resolved = reason == null || reason.isBlank() ? "CONNECTION_CLOSED" : reason;
        return resolved.substring(0, Math.min(100, resolved.length()));
    }

    record ConnectionState(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long generation,
            String modelGrantFingerprint,
            String traceId,
            boolean selfUpdateSupported,
            boolean observabilitySupported,
            Set<String> workspaceRequestIds,
            Map<String, LocalClientPayloads.ObservabilityBatch> traceDeclarations) {

        ConnectionState(
                UserId userId,
                LocalClientInstanceId clientInstanceId,
                long generation,
                String modelGrantFingerprint,
                String traceId,
                boolean selfUpdateSupported,
                Set<String> workspaceRequestIds) {
            this(userId, clientInstanceId, generation, modelGrantFingerprint, traceId,
                    selfUpdateSupported, false, workspaceRequestIds, new ConcurrentHashMap<>());
        }
    }
}
