package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientDownloadTrust;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 主动 WSS 连接、心跳、反向生命周期/HTTP/SSE/文件 RPC 与取消处理。 */
final class LocalClientConnection implements AutoCloseable, LocalClientSelfUpdater.RuntimeControl {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientConnection.class);
    private static final int MAX_NON_STREAM_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_ACTIVE_OPERATIONS = 64;
    static final long OBSERVABILITY_IDLE_NANOS = LocalObservabilitySettings.DEFAULT_IDLE_BEFORE_UPLOAD.toNanos();
    static final long OBSERVABILITY_ACK_TIMEOUT_NANOS =
            LocalObservabilitySettings.DEFAULT_ACKNOWLEDGEMENT_TIMEOUT.toNanos();
    static final long OBSERVABILITY_BYTES_PER_SECOND =
            LocalObservabilitySettings.DEFAULT_UPLOAD_BYTES_PER_SECOND;
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "proxy-authenticate", "host", "cookie", "connection",
            "content-length", "transfer-encoding", "upgrade", "keep-alive", "te", "trailer");

    private final LocalClientConfiguration configuration;
    private final LocalClientCredentialFile.Credentials credentials;
    private final LocalClientBuildInfo buildInfo;
    private final LocalClientPlatform platform = LocalClientPlatform.current();
    private final LocalClientStateStore stateStore;
    private final OpencodeProcessSupervisor supervisor;
    private final LocalModelRelay modelRelay;
    private final LocalClientFileRpcHandler fileRpcHandler;
    private final LocalClientSelfUpdater selfUpdater;
    private final LocalObservabilityRelay observabilityRelay;
    private final LocalObservabilitySettings observabilitySettings;
    private final LocalClientPublicCapabilityUpdater publicCapabilityUpdater;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec(objectMapper);
    private final HttpClient httpClient = loopbackHttpClient();
    private final ExecutorService inboundExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("local-client-inbound-", 0).factory());
    private final ExecutorService operationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("local-client-heartbeat-", 0).factory());
    private final Map<String, Future<?>> operations = new ConcurrentHashMap<>();
    private final Map<String, LocalClientRuntimeSnapshot.ActiveOperation> operationProgress = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LocalClientPayloads.WorkspaceRegistered>> workspaceRegistrations =
            new ConcurrentHashMap<>();
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong connectionAttemptSequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean();
    private final AtomicBoolean acceptingRequests = new AtomicBoolean(true);
    private final AtomicBoolean reEnrollmentRequired = new AtomicBoolean();
    private final AtomicInteger exitCode = new AtomicInteger();
    private final AtomicReference<CompletableFuture<Void>> activeConnectionClose = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> heartbeatTask = new AtomicReference<>();
    private final AtomicReference<ConnectionListener> activeListener = new AtomicReference<>();
    private final Semaphore reconnectSignal = new Semaphore(0);
    private final AtomicReference<LocalClientRuntimeSnapshot.ConnectionState> connectionState =
            new AtomicReference<>(LocalClientRuntimeSnapshot.ConnectionState.OFFLINE);
    private final AtomicReference<Instant> connectedAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();
    private final AtomicReference<LocalClientPayloads.LifecycleResult> processStatus = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> versionCheckTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> observabilityUploadTask = new AtomicReference<>();
    private final AtomicBoolean observabilityInFlight = new AtomicBoolean();
    private final AtomicLong observabilityInFlightStartedNanos = new AtomicLong();
    private final Map<String, LocalObservabilityRelay.PendingChunk> observabilityRequests = new ConcurrentHashMap<>();
    private final AtomicLong lastForegroundActivityNanos = new AtomicLong(System.nanoTime());
    private final AtomicLong observabilityRetryNotBeforeNanos = new AtomicLong();
    private final AtomicInteger observabilityFailureCount = new AtomicInteger();

    LocalClientConnection(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler) {
        this(configuration, credentials, stateStore, supervisor, modelRelay, fileRpcHandler, null,
                LocalObservabilitySettings.defaults(), null);
    }

    LocalClientConnection(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler,
            LocalObservabilityRelay observabilityRelay) {
        this(configuration, credentials, stateStore, supervisor, modelRelay, fileRpcHandler, observabilityRelay,
                LocalObservabilitySettings.defaults(), null);
    }

    LocalClientConnection(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler,
            LocalObservabilityRelay observabilityRelay,
            LocalObservabilitySettings observabilitySettings) {
        this(configuration, credentials, stateStore, supervisor, modelRelay, fileRpcHandler, observabilityRelay,
                observabilitySettings, null);
    }

    LocalClientConnection(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler,
            LocalObservabilityRelay observabilityRelay,
            LocalObservabilitySettings observabilitySettings,
            LocalClientPublicCapabilityStore publicCapabilityStore) {
        this.configuration = configuration;
        this.credentials = credentials;
        this.buildInfo = LocalClientBuildInfo.current();
        this.stateStore = stateStore;
        this.supervisor = supervisor;
        this.modelRelay = modelRelay;
        this.fileRpcHandler = fileRpcHandler;
        this.observabilityRelay = observabilityRelay;
        this.observabilitySettings = observabilitySettings;
        this.selfUpdater = createSelfUpdater();
        this.publicCapabilityUpdater = publicCapabilityStore == null ? null
                : new LocalClientPublicCapabilityUpdater(publicCapabilityStore, supervisor, new CapabilitySink());
    }

    int runForever() {
        long backoffSeconds = 1;
        while (!closed.get() && !reEnrollmentRequired.get() && !Thread.currentThread().isInterrupted()) {
            long attempt = connectionAttemptSequence.incrementAndGet();
            long attemptStartedNanos = System.nanoTime();
            connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.CONNECTING);
            LOGGER.info("local_client_connection_attempt_started attempt={} server={} connectTimeoutSeconds={} retryBackoffSeconds={}",
                    attempt, configuration.serverBaseUri(), 10, backoffSeconds);
            ConnectionListener listener = new ConnectionListener();
            activeListener.set(listener);
            activeConnectionClose.set(listener.closedFuture());
            try {
                WebSocket socket = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(configuration.websocketUri(), listener)
                        .join();
                webSocket.set(socket);
                listener.closedFuture().join();
                // 只有完成 REGISTERED 的连接才重置退避；密钥错误等认证失败不能形成每秒重连风暴。
                if (generation.get() > 0) {
                    backoffSeconds = 1;
                }
            } catch (RuntimeException exception) {
                if (!closed.get()) {
                    String failureCode = connectionFailureCode(exception);
                    lastFailure.set(failureCode);
                    LOGGER.warn("local_client_connection_attempt_failed attempt={} server={} failureCode={} rootFailureType={} durationMs={} retrySeconds={}",
                            attempt, configuration.serverBaseUri(), failureCode,
                            LocalClientDiagnostics.rootFailureType(exception),
                            LocalClientDiagnostics.elapsedMillis(attemptStartedNanos), backoffSeconds);
                }
            } finally {
                activeListener.compareAndSet(listener, null);
                activeConnectionClose.compareAndSet(listener.closedFuture(), null);
                disconnected();
            }
            if (!closed.get() && !reEnrollmentRequired.get()) {
                boolean reconnectRequested = waitForReconnect(Duration.ofSeconds(backoffSeconds));
                backoffSeconds = reconnectRequested ? 1 : Math.min(30, backoffSeconds * 2);
            }
        }
        LOGGER.info("local_client_connection_loop_stopped closed={} reEnrollmentRequired={} interrupted={} exitCode={}",
                closed.get(), reEnrollmentRequired.get(), Thread.currentThread().isInterrupted(), exitCode.get());
        return exitCode.get();
    }

    LocalClientRuntimeSnapshot runtimeSnapshot() {
        return new LocalClientRuntimeSnapshot(
                connectionState.get(),
                connectedAt.get(),
                lastFailure.get(),
                processStatus.get(),
                List.copyOf(operationProgress.values()));
    }

    LocalClientPublicCapabilityStore.State publicCapabilitySnapshot() {
        return publicCapabilityUpdater == null
                ? LocalClientPublicCapabilityStore.State.empty()
                : publicCapabilityUpdater.snapshot();
    }

    /** 托盘确认只提交平台已通知且仍保存在本地状态中的目标摘要。 */
    void requestPublicCapabilityUpdate(String expectedDigest) {
        long currentGeneration = generation.get();
        if (publicCapabilityUpdater == null
                || connectionState.get() != LocalClientRuntimeSnapshot.ConnectionState.ONLINE
                || currentGeneration < 1
                || expectedDigest == null
                || !expectedDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("公共能力更新当前不可确认");
        }
        String requestId = requestId("lcpq_");
        sendFrame(new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_REQUEST,
                requestId,
                requestId("trace_"),
                currentGeneration,
                codec.payload(new LocalClientPayloads.PublicCapabilityUpdateRequest(
                        stateStore.read().clientInstanceId(), currentGeneration, expectedDigest))));
    }

    /** 中断当前连接并唤醒同一重连循环，不创建旁路连接。 */
    void reconnect() {
        if (closed.get()) {
            return;
        }
        connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.CONNECTING);
        reconnectSignal.drainPermits();
        reconnectSignal.release();
        WebSocket socket = webSocket.get();
        if (socket != null) {
            socket.abort();
        }
        ConnectionListener listener = activeListener.get();
        if (listener != null) {
            listener.closedFuture().complete(null);
        }
    }

    /** 通过当前已认证连接注册用户在托盘中选择的本机目录，不依赖浏览器登录态。 */
    CompletableFuture<LocalClientPayloads.WorkspaceRegistered> registerWorkspace(String name, String rootPath) {
        long currentGeneration = generation.get();
        if (connectionState.get() != LocalClientRuntimeSnapshot.ConnectionState.ONLINE || currentGeneration < 1) {
            return CompletableFuture.failedFuture(new IllegalStateException("本地客户端尚未连接平台"));
        }
        String requestId = requestId("lcwr_");
        CompletableFuture<LocalClientPayloads.WorkspaceRegistered> result = new CompletableFuture<>();
        workspaceRegistrations.put(requestId, result);
        result.orTimeout(45, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> workspaceRegistrations.remove(requestId, result));
        try {
            sendFrame(new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.WORKSPACE_REGISTER,
                    requestId,
                    requestId("trace_"),
                    currentGeneration,
                    codec.payload(new LocalClientPayloads.WorkspaceRegister(name, rootPath))));
        } catch (RuntimeException exception) {
            workspaceRegistrations.remove(requestId, result);
            result.completeExceptionally(exception);
        }
        return result;
    }

    private void handleFrame(LocalClientFrame frame) {
        if (isRegistrationAuthenticationFailure(frame, codec, generation.get())) {
            stateStore.requireReEnrollment();
            reEnrollmentRequired.set(true);
            LOGGER.warn("local_client_registration_rejected failureCode=UNAUTHENTICATED reEnrollmentRequired=true");
            throw new IllegalStateException("本地客户端凭据已失效，请手动重新接入");
        }
        if (frame.type() == LocalClientFrameType.REGISTERED) {
            LocalClientPayloads.Registered registered = codec.payload(frame, LocalClientPayloads.Registered.class);
            if (generation.get() != 0
                    || frame.connectionGeneration() == null
                    || frame.connectionGeneration() != registered.connectionGeneration()) {
                throw new IllegalStateException("registered frame generation is invalid");
            }
            generation.set(registered.connectionGeneration());
            connectedAt.set(Instant.now());
            lastFailure.set(null);
            connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.ONLINE);
            stateStore.clearReEnrollmentRequirement();
            modelRelay.updateGrant(registered.modelGrant());
            supervisor.configureManagedModel(registered.managedModelConfig());
            startHeartbeat();
            startVersionChecks();
            startObservabilityUpload();
            if (selfUpdater != null) {
                selfUpdater.reportStoredResult();
            }
            if (publicCapabilityUpdater != null) {
                publicCapabilityUpdater.reportVersion();
            }
            LOGGER.info("local_client_registered clientInstanceId={} generation={} clientVersion={} platform={} architecture={} selfUpdateEnabled={} observabilityEnabled={} publicCapabilitySyncEnabled={}",
                    stateStore.read().clientInstanceId(), registered.connectionGeneration(), buildInfo.clientVersion(),
                    platform.platform(), platform.architecture(), selfUpdater != null,
                    observabilityRelay != null, publicCapabilityUpdater != null);
            return;
        }
        long currentGeneration = generation.get();
        if (currentGeneration < 1
                || frame.connectionGeneration() == null
                || frame.connectionGeneration() != currentGeneration) {
            throw new IllegalStateException("server frame generation does not match current connection");
        }
        switch (frame.type()) {
            case HEARTBEAT_ACK -> {
                // ACK 仅确认 TTL 已刷新；模型 grant 使用同一心跳续期，不需要在客户端换值。
            }
            case LIFECYCLE_COMMAND -> submit(frame, () -> handleLifecycle(frame));
            case FILE_REQUEST -> submit(frame, () -> handleFile(frame));
            case HTTP_REQUEST -> submit(frame, () -> handleHttp(frame));
            case CANCEL -> handleCancel(frame);
            case MODEL_GRANT -> {
                LocalClientPayloads.ModelGrant grant = codec.payload(frame, LocalClientPayloads.ModelGrant.class);
                modelRelay.updateGrant(grant.modelGrant());
            }
            case VERSION_POLICY -> handleVersionPolicy(frame);
            case UPDATE_COMMAND -> submitUpdateCommand(frame);
            case UPDATE_APPLY -> handleUpdateApply(frame);
            case UPDATE_CANCEL -> handleUpdateCancel(frame);
            case UPDATE_STATUS_ACK -> requireSelfUpdater().handleStatusAck(
                    codec.payload(frame, LocalClientPayloads.UpdateStatusAck.class));
            case PUBLIC_CAPABILITY_AVAILABLE -> requirePublicCapabilityUpdater().handleAvailable(
                    codec.payload(frame, LocalClientPayloads.PublicCapabilityAvailable.class));
            case PUBLIC_CAPABILITY_UPDATE_COMMAND -> requirePublicCapabilityUpdater().handleCommand(
                    codec.payload(frame, LocalClientPayloads.PublicCapabilityUpdateCommand.class));
            case BINARY_CHUNK -> operationExecutor.execute(() -> {
                try {
                    requirePublicCapabilityUpdater().handleChunk(
                            frame.requestId(), codec.payload(frame, LocalClientPayloads.BinaryChunk.class));
                } catch (RuntimeException exception) {
                    sendError(frame, exception);
                }
            });
            case PUBLIC_CAPABILITY_UPDATE_STATUS_ACK -> requirePublicCapabilityUpdater().handleStatusAck(
                    codec.payload(frame, LocalClientPayloads.PublicCapabilityUpdateStatusAck.class));
            case TRACE_CHUNK_ACK -> handleTraceChunkAck(frame);
            case TRACE_UPLOAD_WATERMARK -> handleTraceWatermark(frame);
            case WORKSPACE_REGISTERED -> completeWorkspaceRegistration(frame);
            case ERROR -> {
                if (!completeWorkspaceRegistrationError(frame)
                        && !completeObservabilityError(frame)) {
                    throw new IllegalStateException("server rejected local client connection");
                }
            }
            default -> throw new IllegalArgumentException("unsupported server frame type: " + frame.type());
        }
    }

    private void completeWorkspaceRegistration(LocalClientFrame frame) {
        CompletableFuture<LocalClientPayloads.WorkspaceRegistered> pending =
                workspaceRegistrations.remove(frame.requestId());
        if (pending == null) {
            throw new IllegalStateException("unknown workspace registration response");
        }
        pending.complete(codec.payload(frame, LocalClientPayloads.WorkspaceRegistered.class));
        LOGGER.info("local_client_workspace_registration_completed requestId={} traceId={} generation={}",
                frame.requestId(), frame.traceId(), generation.get());
    }

    private boolean completeWorkspaceRegistrationError(LocalClientFrame frame) {
        CompletableFuture<LocalClientPayloads.WorkspaceRegistered> pending =
                workspaceRegistrations.remove(frame.requestId());
        if (pending == null) {
            return false;
        }
        LocalClientPayloads.Error error = codec.payload(frame, LocalClientPayloads.Error.class);
        LOGGER.warn("local_client_workspace_registration_failed requestId={} traceId={} generation={} failureCode={}",
                frame.requestId(), frame.traceId(), generation.get(), error.code());
        pending.completeExceptionally(new IllegalStateException(
                error.message() == null || error.message().isBlank() ? "本地工作区注册失败" : error.message()));
        return true;
    }

    private void submit(LocalClientFrame frame, Runnable task) {
        markForegroundActivity();
        if (!acceptingRequests.get()) {
            LOGGER.info("local_client_operation_rejected requestId={} traceId={} operation={} generation={} reason=CLIENT_UPDATING",
                    frame.requestId(), frame.traceId(), frame.type(), generation.get());
            sendResponse(frame, LocalClientFrameType.ERROR, new LocalClientPayloads.Error(
                    "LOCAL_CLIENT_UPDATING",
                    "本地客户端正在切换版本",
                    true,
                    Map.of("operation", frame.type().name())));
            return;
        }
        // 入站由单线程串行提交；超过上限时关闭异常连接，避免服务端故障在客户端创建无界虚拟线程。
        if (operations.size() >= MAX_ACTIVE_OPERATIONS) {
            throw new IllegalStateException("too many active local client operations");
        }
        long operationStartedNanos = System.nanoTime();
        FutureTask<Void> future = new FutureTask<>(() -> {
            boolean succeeded = false;
            try {
                task.run();
                succeeded = true;
            } catch (RuntimeException exception) {
                LOGGER.warn("local_client_operation_failed requestId={} traceId={} operation={} generation={} durationMs={} rootFailureType={}",
                        frame.requestId(), frame.traceId(), frame.type(), generation.get(),
                        LocalClientDiagnostics.elapsedMillis(operationStartedNanos),
                        LocalClientDiagnostics.rootFailureType(exception));
                sendError(frame, exception);
            } finally {
                if (succeeded && frame.type() != LocalClientFrameType.HTTP_REQUEST) {
                    LOGGER.info("local_client_operation_completed requestId={} traceId={} operation={} generation={} durationMs={}",
                            frame.requestId(), frame.traceId(), frame.type(), generation.get(),
                            LocalClientDiagnostics.elapsedMillis(operationStartedNanos));
                }
                operations.remove(frame.requestId());
                operationProgress.remove(frame.requestId());
                markForegroundActivity();
            }
        }, null);
        Future<?> previous = operations.putIfAbsent(frame.requestId(), future);
        if (previous != null) {
            throw new IllegalStateException("duplicate server requestId");
        }
        operationProgress.put(frame.requestId(), new LocalClientRuntimeSnapshot.ActiveOperation(
                frame.requestId(), frame.type(), Instant.now()));
        if (frame.type() != LocalClientFrameType.HTTP_REQUEST) {
            LOGGER.info("local_client_operation_started requestId={} traceId={} operation={} generation={} activeOperations={}",
                    frame.requestId(), frame.traceId(), frame.type(), generation.get(), operations.size());
        }
        try {
            operationExecutor.execute(future);
        } catch (RuntimeException exception) {
            operations.remove(frame.requestId(), future);
            operationProgress.remove(frame.requestId());
            throw exception;
        }
    }

    private void handleVersionPolicy(LocalClientFrame frame) {
        LocalClientPayloads.VersionPolicy policy = codec.payload(frame, LocalClientPayloads.VersionPolicy.class);
        if (!acceptVersionPolicy(
                stateStore.read().clientInstanceId(), generation.get(), policy)) {
            throw new IllegalArgumentException("version policy coordinates are invalid");
        }
        LOGGER.info("local_client_version_policy_received generation={} policyRevision={} targetVersion={} direction={} force={}",
                generation.get(), policy.policyRevision(), policy.targetVersion(), policy.direction(), policy.force());
        // 非强制策略只驱动平台站内信；实际切换始终等待带幂等 commandId 的 UPDATE_COMMAND。
    }

    /**
     * 兼容旧服务端在尚未配置版本策略时发送的空策略。
     * 只有“无目标、SAME、非强制、revision=0”可作为无动作哨兵；真实更新策略仍必须使用正 revision。
     */
    static boolean acceptVersionPolicy(
            String expectedClientInstanceId,
            long expectedGeneration,
            LocalClientPayloads.VersionPolicy policy) {
        if (policy == null
                || !expectedClientInstanceId.equals(policy.clientInstanceId())
                || policy.connectionGeneration() != expectedGeneration) {
            return false;
        }
        if (policy.policyRevision() > 0) {
            return true;
        }
        return policy.policyRevision() == 0
                && policy.targetVersion() == null
                && "SAME".equals(policy.direction())
                && !policy.force();
    }

    /** 只输出固定错误码或异常类型，不把 WebSocket/HTTP 异常正文写入客户端日志。 */
    static String connectionFailureCode(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                        || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof IllegalArgumentException
                && "version policy coordinates are invalid".equals(cause.getMessage())) {
            return "VERSION_POLICY_INVALID";
        }
        return cause == null ? "UNKNOWN" : cause.getClass().getSimpleName();
    }

    private void submitUpdateCommand(LocalClientFrame frame) {
        LocalClientSelfUpdater updater = requireSelfUpdater();
        LocalClientPayloads.UpdateCommand command = codec.payload(frame, LocalClientPayloads.UpdateCommand.class);
        requireUpdateCoordinates(frame, command.commandId(), command.connectionGeneration());
        LOGGER.info("local_client_update_command_submitted commandId={} traceId={} generation={} targetVersion={} direction={} policyRevision={}",
                command.commandId(), frame.traceId(), command.connectionGeneration(), command.targetVersion(),
                command.direction(), command.policyRevision());
        FutureTask<Void> future = new FutureTask<>(() -> {
            try {
                updater.handleCommand(command);
            } catch (RuntimeException exception) {
                sendError(frame, exception);
            } finally {
                operations.remove(command.commandId());
            }
        }, null);
        Future<?> previous = operations.putIfAbsent(command.commandId(), future);
        if (previous != null) {
            // 跨节点补偿可能重复下发同一命令；正在准备时保持单任务，完成后由 updater 重发 PREPARED。
            return;
        }
        try {
            operationExecutor.execute(future);
        } catch (RuntimeException exception) {
            operations.remove(command.commandId(), future);
            throw exception;
        }
    }

    private void handleUpdateApply(LocalClientFrame frame) {
        LocalClientPayloads.UpdateApply apply = codec.payload(frame, LocalClientPayloads.UpdateApply.class);
        requireUpdateCoordinates(frame, apply.commandId(), apply.connectionGeneration());
        LOGGER.info("local_client_update_apply_received commandId={} traceId={} generation={} targetVersion={}",
                apply.commandId(), frame.traceId(), apply.connectionGeneration(), apply.targetVersion());
        requireSelfUpdater().handleApply(apply);
    }

    private void handleUpdateCancel(LocalClientFrame frame) {
        LocalClientPayloads.UpdateCancel cancel = codec.payload(frame, LocalClientPayloads.UpdateCancel.class);
        requireUpdateCoordinates(frame, cancel.commandId(), cancel.connectionGeneration());
        LOGGER.info("local_client_update_cancel_received commandId={} traceId={} generation={} targetVersion={}",
                cancel.commandId(), frame.traceId(), cancel.connectionGeneration(), cancel.targetVersion());
        requireSelfUpdater().handleCancel(cancel);
        Future<?> future = operations.remove(cancel.commandId());
        if (future != null) {
            future.cancel(true);
        }
    }

    private void requireUpdateCoordinates(LocalClientFrame frame, String commandId, long payloadGeneration) {
        if (commandId == null
                || !commandId.equals(frame.requestId())
                || payloadGeneration != generation.get()) {
            throw new IllegalArgumentException("update frame coordinates are invalid");
        }
    }

    private void handleLifecycle(LocalClientFrame frame) {
        LocalClientPayloads.LifecycleCommand command = codec.payload(frame, LocalClientPayloads.LifecycleCommand.class);
        String action = command.action() == null ? "" : command.action().trim().toUpperCase(Locale.ROOT);
        long startedNanos = System.nanoTime();
        LOGGER.info("local_opencode_lifecycle_started requestId={} traceId={} action={} preferredPort={}",
                frame.requestId(), frame.traceId(), action, command.preferredPort());
        LocalClientPayloads.LifecycleResult result = switch (action) {
            case "START" -> supervisor.start(command.preferredPort());
            case "RESTART" -> supervisor.restart(command.preferredPort());
            case "STOP" -> supervisor.stop();
            case "STATUS" -> supervisor.status();
            default -> throw new IllegalArgumentException("unsupported lifecycle action");
        };
        processStatus.set(result);
        LOGGER.info("local_opencode_lifecycle_completed requestId={} traceId={} action={} success={} processStatus={} processId={} port={} healthy={} durationMs={}",
                frame.requestId(), frame.traceId(), action, result.success(), result.processStatus(),
                result.processId(), result.opencodePort(), result.opencodeHealthy(),
                LocalClientDiagnostics.elapsedMillis(startedNanos));
        sendResponse(frame, LocalClientFrameType.LIFECYCLE_RESULT, result);
    }

    private void handleFile(LocalClientFrame frame) {
        LocalClientPayloads.FileRequest request = codec.payload(frame, LocalClientPayloads.FileRequest.class);
        sendResponse(frame, LocalClientFrameType.FILE_RESPONSE,
                new LocalClientPayloads.FileResponse(true, fileRpcHandler.handle(request)));
    }

    private void handleHttp(LocalClientFrame frame) {
        LocalClientPayloads.HttpRequest request = codec.payload(frame, LocalClientPayloads.HttpRequest.class);
        LOGGER.debug("local_client_http_started requestId={} method={} path={}",
                frame.requestId(), request.method(), request.pathAndQuery());
        LocalClientPayloads.LifecycleResult status = supervisor.status();
        if (!status.success() || !status.opencodeHealthy() || status.opencodePort() == null) {
            throw new IllegalStateException("local OpenCode is not healthy");
        }
        String pathAndQuery = request.pathAndQuery();
        if (pathAndQuery == null || !pathAndQuery.startsWith("/") || pathAndQuery.contains("\r") || pathAndQuery.contains("\n")) {
            throw new IllegalArgumentException("OpenCode request path is invalid");
        }
        URI target = URI.create("http://127.0.0.1:" + status.opencodePort() + pathAndQuery);
        // OpenCode 1.18.4 的本地 HTTP server 不支持 JDK HttpClient 的 h2c upgrade；POST 虽会执行，
        // 但响应流不会结束。这里显式锁定 HTTP/1.1，避免平台在正常创建远端会话后误报 504。
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(target)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofHours(24));
        copyHeaders(request.headers(), builder);
        byte[] body = request.bodyBase64() == null || request.bodyBase64().isBlank()
                ? new byte[0]
                : Base64.getDecoder().decode(request.bodyBase64());
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    builder.method(request.method(), publisher).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (request.streaming()) {
                sendResponse(frame, LocalClientFrameType.STREAM_OPEN,
                        new LocalClientPayloads.StreamOpen(response.statusCode(), response.headers().map()));
                streamBody(frame, response.body());
                sendResponse(frame, LocalClientFrameType.STREAM_END, Map.of("complete", true));
                return;
            }
            byte[] responseBody = readBounded(response.body(), MAX_NON_STREAM_RESPONSE_BYTES);
            sendResponse(frame, LocalClientFrameType.HTTP_RESPONSE, new LocalClientPayloads.HttpResponse(
                    response.statusCode(), response.headers().map(), Base64.getEncoder().encodeToString(responseBody)));
            LOGGER.debug("local_client_http_completed requestId={} status={} responseBytes={}",
                    frame.requestId(), response.statusCode(), responseBody.length);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenCode HTTP request cancelled", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenCode HTTP request failed", exception);
        }
    }

    private void streamBody(LocalClientFrame frame, InputStream input) throws Exception {
        try (input) {
            byte[] buffer = new byte[LocalClientProtocol.BINARY_CHUNK_BYTES];
            long sequence = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                byte[] chunk = read == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, read);
                sendResponse(frame, LocalClientFrameType.STREAM_CHUNK, new LocalClientPayloads.BinaryChunk(
                        sequence++, Base64.getEncoder().encodeToString(chunk), false));
            }
        }
    }

    private void handleCancel(LocalClientFrame frame) {
        LocalClientPayloads.Cancel cancel = codec.payload(frame, LocalClientPayloads.Cancel.class);
        Future<?> future = operations.remove(cancel.targetRequestId());
        operationProgress.remove(cancel.targetRequestId());
        if (future != null) {
            future.cancel(true);
        }
        LOGGER.info("local_client_operation_cancelled requestId={} traceId={} targetRequestId={} taskFound={}",
                frame.requestId(), frame.traceId(), cancel.targetRequestId(), future != null);
    }

    private void startHeartbeat() {
        ScheduledFuture<?> previous = heartbeatTask.getAndSet(scheduler.scheduleAtFixedRate(
                this::heartbeatSafely,
                0,
                LocalClientProtocol.HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void heartbeatSafely() {
        try {
            long currentGeneration = generation.get();
            if (currentGeneration < 1 || webSocket.get() == null) {
                return;
            }
            LocalClientPayloads.LifecycleResult status;
            try {
                status = supervisor.status();
            } catch (RuntimeException exception) {
                LOGGER.warn("local_opencode_heartbeat_status_failed generation={} rootFailureType={}",
                        currentGeneration, LocalClientDiagnostics.rootFailureType(exception));
                status = new LocalClientPayloads.LifecycleResult(
                        false, "FAILED", null, null, null, false, null, "状态检查失败");
            }
            processStatus.set(status);
            sendFrame(new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.HEARTBEAT,
                    requestId("lch_"),
                    requestId("trace_"),
                    currentGeneration,
                    codec.payload(new LocalClientPayloads.Heartbeat(
                            status.processStatus(),
                            status.processId(),
                            status.processStartedAt(),
                            status.opencodePort(),
                            status.opencodeHealthy(),
                            LocalClientRegistrationFrames.reportedAddresses(),
                            Instant.now()))));
        } catch (RuntimeException exception) {
            LOGGER.warn("local_client_heartbeat_failed generation={} failureCode={} rootFailureType={}",
                    generation.get(), connectionFailureCode(exception),
                    LocalClientDiagnostics.rootFailureType(exception));
            WebSocket socket = webSocket.get();
            if (socket != null) {
                socket.abort();
            }
        }
    }

    private void startVersionChecks() {
        if (!shouldStartVersionChecks(configuration, buildInfo)) {
            return;
        }
        ScheduledFuture<?> previous = versionCheckTask.getAndSet(
                scheduler.schedule(this::versionCheckSafely, 0, TimeUnit.SECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /** 注册报文未声明自更新时不得发送 VERSION_CHECK，否则服务端会按能力 fencing 主动断开连接。 */
    static boolean shouldStartVersionChecks(
            LocalClientConfiguration configuration,
            LocalClientBuildInfo buildInfo) {
        return configuration.selfUpdateConfigured()
                && buildInfo.capabilities().contains(LocalClientBuildInfo.SELF_UPDATE_CAPABILITY);
    }

    private void startObservabilityUpload() {
        if (observabilityRelay == null
                || observabilitySettings.maxInFlight() == 0
                || !buildInfo.capabilities().contains(LocalClientBuildInfo.OBSERVABILITY_CAPABILITY)) {
            return;
        }
        ScheduledFuture<?> previous = observabilityUploadTask.getAndSet(
                scheduler.scheduleWithFixedDelay(
                        this::uploadObservabilitySafely,
                        1,
                        250,
                        TimeUnit.MILLISECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /** Trace 只在高优先级业务空闲三秒后发送；一次只允许一个分片等待 ACK。 */
    private void uploadObservabilitySafely() {
        try {
            long now = System.nanoTime();
            long currentGeneration = generation.get();
            if (observabilityInFlight.get()
                    && now - observabilityInFlightStartedNanos.get()
                    >= observabilitySettings.acknowledgementTimeout().toNanos()) {
                // ACK 丢失不能永久冻结低优先级队列；原分片仍在 spool，下一轮按相同摘要幂等重传。
                observabilityRequests.clear();
                observabilityInFlight.set(false);
                observabilityInFlightStartedNanos.set(0);
                observabilityBackoff();
                return;
            }
            if (!shouldUploadObservability(
                    currentGeneration,
                    webSocket.get() != null,
                    observabilityInFlight.get(),
                    !operations.isEmpty(),
                    modelRelay.active(),
                    now,
                    observabilityRetryNotBeforeNanos.get(),
                    Math.max(lastForegroundActivityNanos.get(), modelRelay.lastActivityNanos()),
                    observabilitySettings.idleBeforeUpload().toNanos())) {
                return;
            }
            LocalObservabilityRelay.PendingChunk pending = observabilityRelay.nextPending();
            if (pending == null || !observabilityInFlight.compareAndSet(false, true)) {
                return;
            }
            String requestId = requestId("lcobs_");
            observabilityRequests.put(requestId, pending);
            observabilityInFlightStartedNanos.set(now);
            try {
                String clientInstanceId = stateStore.read().clientInstanceId();
                sendFrame(new LocalClientFrame(
                        LocalClientProtocol.VERSION,
                        LocalClientFrameType.OBSERVABILITY_BATCH,
                        requestId,
                        pending.traceId(),
                        currentGeneration,
                        codec.payload(new LocalClientPayloads.ObservabilityBatch(
                                pending.batchId(), clientInstanceId, currentGeneration,
                                pending.traceId(), pending.runtimeGeneration(), pending.runtimeKind(),
                                pending.coverageStartAt(), pending.firstSequence(), pending.lastSequence(),
                                pending.sha256(), pending.contentLength(), pending.droppedCount(),
                                Math.max(0, observabilityRelay.pendingChunkCount() - 1)
                                        + observabilityRelay.blockedChunkCount(),
                                pending.complete(), pending.createdAt()))));
                byte[] body = observabilityRelay.readBody(pending);
                sendFrame(new LocalClientFrame(
                        LocalClientProtocol.VERSION,
                        LocalClientFrameType.TRACE_CHUNK_UPLOAD,
                        requestId,
                        pending.traceId(),
                        currentGeneration,
                        codec.payload(new LocalClientPayloads.TraceChunkUpload(
                                pending.batchId(), clientInstanceId, currentGeneration,
                                pending.traceId(), pending.firstSequence(), pending.lastSequence(),
                                pending.sha256(), Base64.getEncoder().encodeToString(body)))));
                long throttleNanos = Math.max(
                        TimeUnit.MILLISECONDS.toNanos(250),
                        (long) Math.ceil((double) body.length * 1_000_000_000D
                                / observabilitySettings.uploadBytesPerSecond()));
                observabilityRetryNotBeforeNanos.set(now + throttleNanos);
            } catch (RuntimeException exception) {
                observabilityRequests.remove(requestId);
                observabilityInFlight.set(false);
                observabilityInFlightStartedNanos.set(0);
                observabilityBackoff();
            }
        } catch (RuntimeException exception) {
            observabilityInFlight.set(false);
            observabilityInFlightStartedNanos.set(0);
            observabilityBackoff();
        }
    }

    private void handleTraceChunkAck(LocalClientFrame frame) {
        if (observabilityRelay == null) {
            throw new IllegalStateException("unexpected trace acknowledgement");
        }
        LocalClientPayloads.TraceChunkAck ack = codec.payload(frame, LocalClientPayloads.TraceChunkAck.class);
        LocalObservabilityRelay.PendingChunk pending = observabilityRequests.remove(frame.requestId());
        if (pending == null) {
            // 超时后的旧 ACK 或幂等重传的后到 ACK 不再影响当前连接，也不能删除新的 spool 坐标。
            return;
        }
        if (!stateStore.read().clientInstanceId().equals(ack.clientInstanceId())
                || ack.connectionGeneration() != generation.get()
                || !pending.batchId().equals(ack.batchId())) {
            throw new IllegalStateException("trace acknowledgement coordinates are invalid");
        }
        observabilityRelay.acknowledge(
                ack.batchId(), ack.traceId(), ack.lastSequence(), ack.sha256());
        observabilityFailureCount.set(0);
        observabilityInFlight.set(false);
        observabilityInFlightStartedNanos.set(0);
    }

    private void handleTraceWatermark(LocalClientFrame frame) {
        LocalClientPayloads.TraceUploadWatermark watermark = codec.payload(
                frame, LocalClientPayloads.TraceUploadWatermark.class);
        if (!stateStore.read().clientInstanceId().equals(watermark.clientInstanceId())
                || watermark.connectionGeneration() != generation.get()) {
            throw new IllegalStateException("trace watermark coordinates are invalid");
        }
    }

    private boolean completeObservabilityError(LocalClientFrame frame) {
        LocalObservabilityRelay.PendingChunk pending = observabilityRequests.remove(frame.requestId());
        if (pending == null) {
            return false;
        }
        LocalClientPayloads.Error error = codec.payload(frame, LocalClientPayloads.Error.class);
        observabilityInFlight.set(false);
        observabilityInFlightStartedNanos.set(0);
        if (isDigestConflict(error)) {
            // 不可重试的摘要冲突保留在 blocked spool；随后继续上传其它分片，Trace 在服务器端保持不完整。
            observabilityRelay.quarantine(pending, "TRACE_CHUNK_DIGEST_CONFLICT");
            observabilityFailureCount.set(0);
            observabilityRetryNotBeforeNanos.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            return true;
        }
        observabilityBackoff();
        return true;
    }

    static boolean isDigestConflict(LocalClientPayloads.Error error) {
        return error != null
                && !error.retryable()
                && "CONFLICT".equals(error.code())
                && error.details() != null
                && "TRACE_CHUNK_DIGEST_CONFLICT".equals(error.details().get("reason"));
    }

    private void observabilityBackoff() {
        int failures = Math.min(6, observabilityFailureCount.incrementAndGet());
        long delaySeconds = Math.min(30, 1L << Math.max(0, failures - 1));
        observabilityRetryNotBeforeNanos.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(delaySeconds));
    }

    private void markForegroundActivity() {
        lastForegroundActivityNanos.set(System.nanoTime());
    }

    /** 高优先级业务、退避窗口和三秒空闲门槛统一判定，Trace 永远不能抢占对话帧。 */
    static boolean shouldUploadObservability(
            long connectionGeneration,
            boolean connected,
            boolean traceInFlight,
            boolean foregroundOperationActive,
            boolean modelActive,
            long nowNanos,
            long retryNotBeforeNanos,
            long lastForegroundActivityNanos) {
        return shouldUploadObservability(
                connectionGeneration, connected, traceInFlight, foregroundOperationActive, modelActive,
                nowNanos, retryNotBeforeNanos, lastForegroundActivityNanos, OBSERVABILITY_IDLE_NANOS);
    }

    static boolean shouldUploadObservability(
            long connectionGeneration,
            boolean connected,
            boolean traceInFlight,
            boolean foregroundOperationActive,
            boolean modelActive,
            long nowNanos,
            long retryNotBeforeNanos,
            long lastForegroundActivityNanos,
            long idleNanos) {
        return connectionGeneration > 0
                && connected
                && !traceInFlight
                && !foregroundOperationActive
                && !modelActive
                && nowNanos >= retryNotBeforeNanos
                && nowNanos - lastForegroundActivityNanos >= idleNanos;
    }

    private void versionCheckSafely() {
        try {
            long currentGeneration = generation.get();
            if (currentGeneration < 1 || webSocket.get() == null) {
                return;
            }
            sendFrame(new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.VERSION_CHECK,
                    requestId("lcvc_"),
                    requestId("trace_"),
                    currentGeneration,
                    codec.payload(versionCheck(stateStore.read(), buildInfo, Instant.now()))));
            long delay = versionCheckDelaySeconds(ThreadLocalRandom.current().nextInt(61));
            versionCheckTask.set(scheduler.schedule(this::versionCheckSafely, delay, TimeUnit.SECONDS));
        } catch (RuntimeException exception) {
            LOGGER.warn("local_client_version_check_failed generation={} failureCode={} rootFailureType={}",
                    generation.get(), connectionFailureCode(exception),
                    LocalClientDiagnostics.rootFailureType(exception));
            WebSocket socket = webSocket.get();
            if (socket != null) {
                socket.abort();
            }
        }
    }

    private void sendRegistration(WebSocket socket) {
        LocalClientFrame frame = LocalClientRegistrationFrames.create(
                configuration,
                credentials,
                stateStore.read(),
                buildInfo,
                requestId("lcr_"),
                requestId("trace_"));
        synchronized (this) {
            socket.sendText(codec.encode(frame), true).toCompletableFuture().join();
        }
        LOGGER.info("local_client_registration_sent requestId={} traceId={} clientVersion={} platform={} architecture={}",
                frame.requestId(), frame.traceId(), buildInfo.clientVersion(), platform.platform(),
                platform.architecture());
    }

    private void sendResponse(LocalClientFrame request, LocalClientFrameType type, Object payload) {
        sendFrame(new LocalClientFrame(
                LocalClientProtocol.VERSION,
                type,
                request.requestId(),
                request.traceId(),
                generation.get(),
                codec.payload(payload)));
    }

    private void sendError(LocalClientFrame request, RuntimeException exception) {
        LOGGER.warn("local_client_operation_error_response requestId={} traceId={} operation={} generation={} failureCode=LOCAL_CLIENT_OPERATION_FAILED rootFailureType={}",
                request.requestId(), request.traceId(), request.type(), generation.get(),
                LocalClientDiagnostics.rootFailureType(exception));
        try {
            sendResponse(request, LocalClientFrameType.ERROR, new LocalClientPayloads.Error(
                    "LOCAL_CLIENT_OPERATION_FAILED",
                    "本地客户端请求执行失败",
                    false,
                    Map.of("operation", request.type().name())));
        } catch (RuntimeException ignored) {
            WebSocket socket = webSocket.get();
            if (socket != null) {
                socket.abort();
            }
        }
    }

    private void sendFrame(LocalClientFrame frame) {
        WebSocket socket = webSocket.get();
        if (socket == null) {
            throw new IllegalStateException("local client WebSocket is disconnected");
        }
        synchronized (this) {
            socket.sendText(codec.encode(frame), true).toCompletableFuture().join();
        }
    }

    private void disconnected() {
        long previousGeneration = generation.get();
        Instant previousConnectedAt = connectedAt.get();
        int activeOperationCount = operations.size();
        webSocket.set(null);
        generation.set(0);
        modelRelay.clearGrant();
        ScheduledFuture<?> task = heartbeatTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
        ScheduledFuture<?> versionTask = versionCheckTask.getAndSet(null);
        if (versionTask != null) {
            versionTask.cancel(false);
        }
        ScheduledFuture<?> observabilityTask = observabilityUploadTask.getAndSet(null);
        if (observabilityTask != null) {
            observabilityTask.cancel(false);
        }
        observabilityRequests.clear();
        observabilityInFlight.set(false);
        observabilityInFlightStartedNanos.set(0);
        markForegroundActivity();
        operations.forEach((id, future) -> future.cancel(true));
        operations.clear();
        operationProgress.clear();
        workspaceRegistrations.forEach((id, pending) -> pending.completeExceptionally(
                new IllegalStateException("平台连接已断开")));
        workspaceRegistrations.clear();
        fileRpcHandler.abortAll();
        connectedAt.set(null);
        if (!closed.get()) {
            connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.OFFLINE);
        }
        if (selfUpdater != null) {
            selfUpdater.handleConnectionLost();
        }
        if (previousGeneration > 0 || previousConnectedAt != null || activeOperationCount > 0) {
            long onlineDurationMs = previousConnectedAt == null
                    ? 0L
                    : Math.max(0L, Duration.between(previousConnectedAt, Instant.now()).toMillis());
            LOGGER.info("local_client_disconnected previousGeneration={} onlineDurationMs={} cancelledOperations={} closed={} reEnrollmentRequired={}",
                    previousGeneration, onlineDurationMs, activeOperationCount, closed.get(),
                    reEnrollmentRequired.get());
        }
    }

    private LocalClientSelfUpdater createSelfUpdater() {
        if (!configuration.selfUpdateConfigured()
                || !buildInfo.managedRelease()
                || !buildInfo.capabilities().contains(LocalClientBuildInfo.SELF_UPDATE_CAPABILITY)) {
            return null;
        }
        try {
            LocalClientDownloadTrust trust = new LocalClientDownloadTrust(
                    configuration.downloadBaseUri(),
                    LocalClientDownloadTrust.parsePublicKeyPem(configuration.signingPublicKeyBase64()));
            LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                    trust,
                    configuration.installRoot(),
                    objectMapper,
                    new LocalClientHttpFetcher(),
                    new LocalClientTarGzExtractor(),
                    new LocalClientCandidateChecker());
            return new LocalClientSelfUpdater(
                    buildInfo.clientVersion(),
                    stateStore.read().clientInstanceId(),
                    downloader::prepare,
                    new LocalClientUpdateMarkerStore(stateStore.stateDirectory()),
                    supervisor::stop,
                    this,
                    Clock.systemUTC());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to initialize local client self-updater", exception);
        }
    }

    private LocalClientSelfUpdater requireSelfUpdater() {
        if (selfUpdater == null) {
            throw new IllegalStateException("local client self-update is not configured");
        }
        return selfUpdater;
    }

    private LocalClientPublicCapabilityUpdater requirePublicCapabilityUpdater() {
        if (publicCapabilityUpdater == null) {
            throw new IllegalStateException("public capability updater is not configured");
        }
        return publicCapabilityUpdater;
    }

    private final class CapabilitySink implements LocalClientPublicCapabilityUpdater.ProtocolSink {
        @Override
        public void send(LocalClientFrameType type, String requestId, Object payload) {
            sendFrame(new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    type,
                    requestId,
                    LocalClientConnection.requestId("trace_"),
                    generation.get(),
                    codec.payload(payload)));
        }

        @Override
        public String clientInstanceId() {
            return stateStore.read().clientInstanceId();
        }

        @Override
        public long generation() {
            return generation.get();
        }
    }

    private static void copyHeaders(Map<String, List<String>> headers, HttpRequest.Builder builder) {
        if (headers == null) {
            return;
        }
        headers.forEach((name, values) -> {
            if (name == null || BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            if (values != null) {
                values.forEach(value -> builder.header(name, value));
            }
        });
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException("OpenCode response exceeds 1 MiB inline limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String requestId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    static HttpClient loopbackHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    static boolean isRegistrationAuthenticationFailure(
            LocalClientFrame frame,
            LocalClientFrameCodec codec,
            long currentGeneration) {
        if (currentGeneration != 0 || frame.type() != LocalClientFrameType.ERROR) {
            return false;
        }
        LocalClientPayloads.Error error = codec.payload(frame, LocalClientPayloads.Error.class);
        return "UNAUTHENTICATED".equals(error.code()) || "FORBIDDEN".equals(error.code());
    }

    static LocalClientPayloads.VersionCheck versionCheck(
            LocalClientPersistentState state,
            LocalClientBuildInfo buildInfo,
            Instant checkedAt) {
        return new LocalClientPayloads.VersionCheck(
                state.clientInstanceId(),
                buildInfo.clientVersion(),
                buildInfo.launcherVersion(),
                "1.18.4",
                buildInfo.capabilities(),
                checkedAt);
    }

    static long versionCheckDelaySeconds(int jitterSeconds) {
        if (jitterSeconds < 0 || jitterSeconds > 60) {
            throw new IllegalArgumentException("version check jitter must be between 0 and 60 seconds");
        }
        return Duration.ofMinutes(5).toSeconds() + jitterSeconds;
    }

    private boolean waitForReconnect(Duration duration) {
        try {
            return reconnectSignal.tryAcquire(duration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    @Override
    public void close() {
        if (!resourcesClosed.compareAndSet(false, true)) {
            return;
        }
        connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.STOPPING);
        LOGGER.info("local_client_connection_close_started generation={} activeOperations={}",
                generation.get(), operations.size());
        reconnectSignal.release();
        closed.set(true);
        WebSocket socket = webSocket.getAndSet(null);
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "CLIENT_SHUTDOWN");
        }
        ConnectionListener listener = activeListener.get();
        if (listener != null) {
            listener.closedFuture().complete(null);
        }
        CompletableFuture<Void> closeFuture = activeConnectionClose.get();
        if (closeFuture != null) {
            closeFuture.complete(null);
        }
        disconnected();
        inboundExecutor.close();
        operationExecutor.close();
        scheduler.close();
        LOGGER.info("local_client_connection_close_completed");
    }

    @Override
    public void sendStatus(LocalClientPayloads.UpdateStatus status) {
        sendFrame(new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_STATUS,
                status.commandId(),
                requestId("trace_"),
                generation.get(),
                codec.payload(status)));
    }

    @Override
    public void sendPrepared(LocalClientPayloads.UpdatePrepared payload) {
        sendFrame(new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_PREPARED,
                payload.commandId(),
                requestId("trace_"),
                generation.get(),
                codec.payload(payload)));
    }

    @Override
    public void quiesce() {
        acceptingRequests.set(false);
        LOGGER.info("local_client_runtime_quiesced activeOperations={}", operations.size());
        operations.forEach((id, future) -> future.cancel(true));
        operations.clear();
        fileRpcHandler.abortAll();
    }

    @Override
    public void resume() {
        acceptingRequests.set(true);
        LOGGER.info("local_client_runtime_resumed");
    }

    @Override
    public void requestExit(int requestedExitCode) {
        if (requestedExitCode != LocalClientSelfUpdater.LAUNCHER_APPLY_EXIT_CODE) {
            throw new IllegalArgumentException("unsupported local client exit code");
        }
        exitCode.compareAndSet(0, requestedExitCode);
        LOGGER.info("local_client_exit_requested source=self_update exitCode={} generation={}",
                requestedExitCode, generation.get());
        closed.set(true);
        connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.STOPPING);
        reconnectSignal.release();
        CompletableFuture<Void> closeFuture = activeConnectionClose.get();
        if (closeFuture != null) {
            closeFuture.complete(null);
        }
        ConnectionListener listener = activeListener.get();
        if (listener != null) {
            listener.closedFuture().complete(null);
        }
        WebSocket socket = webSocket.getAndSet(null);
        if (socket != null) {
            socket.abort();
        }
    }

    private final class ConnectionListener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();
        private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket socket) {
            webSocket.set(socket);
            LOGGER.info("local_client_websocket_opened attempt={}", connectionAttemptSequence.get());
            sendRegistration(socket);
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            synchronized (text) {
                text.append(data);
                if (text.length() > LocalClientProtocol.MAX_FRAME_CHARS) {
                    socket.abort();
                    closedFuture.completeExceptionally(new IllegalStateException("server frame too large"));
                    return CompletableFuture.completedFuture(null);
                }
                if (last) {
                    String encoded = text.toString();
                    text.setLength(0);
                    inboundExecutor.execute(() -> {
                        try {
                            handleFrame(codec.decode(encoded));
                            socket.request(1);
                        } catch (RuntimeException exception) {
                            socket.abort();
                            closedFuture.completeExceptionally(exception);
                        }
                    });
                } else {
                    socket.request(1);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket socket, ByteBuffer message) {
            socket.request(1);
            return socket.sendPong(message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            LOGGER.info("local_client_websocket_closed attempt={} statusCode={} registeredGeneration={}",
                    connectionAttemptSequence.get(), statusCode, generation.get());
            closedFuture.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            LOGGER.warn("local_client_websocket_error attempt={} failureCode={} rootFailureType={}",
                    connectionAttemptSequence.get(), connectionFailureCode(error),
                    LocalClientDiagnostics.rootFailureType(error));
            closedFuture.completeExceptionally(error);
        }

        CompletableFuture<Void> closedFuture() {
            return closedFuture;
        }
    }
}
