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
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "proxy-authenticate", "host", "cookie", "connection",
            "content-length", "transfer-encoding", "upgrade", "keep-alive", "te", "trailer");

    private final LocalClientConfiguration configuration;
    private final LocalClientCredentialFile.Credentials credentials;
    private final LocalClientBuildInfo buildInfo;
    private final LocalClientStateStore stateStore;
    private final OpencodeProcessSupervisor supervisor;
    private final LocalModelRelay modelRelay;
    private final LocalClientFileRpcHandler fileRpcHandler;
    private final LocalClientSelfUpdater selfUpdater;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec(objectMapper);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService inboundExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("local-client-inbound-", 0).factory());
    private final ExecutorService operationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("local-client-heartbeat-", 0).factory());
    private final Map<String, Future<?>> operations = new ConcurrentHashMap<>();
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean();
    private final AtomicBoolean acceptingRequests = new AtomicBoolean(true);
    private final AtomicBoolean reEnrollmentRequired = new AtomicBoolean();
    private final AtomicInteger exitCode = new AtomicInteger();
    private final AtomicReference<CompletableFuture<Void>> activeConnectionClose = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> heartbeatTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> versionCheckTask = new AtomicReference<>();

    LocalClientConnection(
            LocalClientConfiguration configuration,
            LocalClientCredentialFile.Credentials credentials,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler) {
        this.configuration = configuration;
        this.credentials = credentials;
        this.buildInfo = LocalClientBuildInfo.current();
        this.stateStore = stateStore;
        this.supervisor = supervisor;
        this.modelRelay = modelRelay;
        this.fileRpcHandler = fileRpcHandler;
        this.selfUpdater = createSelfUpdater();
    }

    int runForever() {
        long backoffSeconds = 1;
        while (!closed.get() && !reEnrollmentRequired.get() && !Thread.currentThread().isInterrupted()) {
            ConnectionListener listener = new ConnectionListener();
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
                    LOGGER.warn("local_client_connection_failed server={} retrySeconds={}",
                            configuration.serverBaseUri(), backoffSeconds);
                }
            } finally {
                activeConnectionClose.compareAndSet(listener.closedFuture(), null);
                disconnected();
            }
            if (!closed.get() && !reEnrollmentRequired.get()) {
                sleep(Duration.ofSeconds(backoffSeconds));
                backoffSeconds = Math.min(30, backoffSeconds * 2);
            }
        }
        return exitCode.get();
    }

    private void handleFrame(LocalClientFrame frame) {
        if (isRegistrationAuthenticationFailure(frame, codec, generation.get())) {
            stateStore.requireReEnrollment();
            reEnrollmentRequired.set(true);
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
            stateStore.clearReEnrollmentRequirement();
            modelRelay.updateGrant(registered.modelGrant());
            startHeartbeat();
            startVersionChecks();
            if (selfUpdater != null) {
                selfUpdater.reportStoredResult();
            }
            LOGGER.info("local_client_registered clientInstanceId={} generation={}",
                    stateStore.read().clientInstanceId(), registered.connectionGeneration());
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
            case ERROR -> throw new IllegalStateException("server rejected local client connection");
            default -> throw new IllegalArgumentException("unsupported server frame type: " + frame.type());
        }
    }

    private void submit(LocalClientFrame frame, Runnable task) {
        if (!acceptingRequests.get()) {
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
        FutureTask<Void> future = new FutureTask<>(() -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                sendError(frame, exception);
            } finally {
                operations.remove(frame.requestId());
            }
        }, null);
        Future<?> previous = operations.putIfAbsent(frame.requestId(), future);
        if (previous != null) {
            throw new IllegalStateException("duplicate server requestId");
        }
        try {
            operationExecutor.execute(future);
        } catch (RuntimeException exception) {
            operations.remove(frame.requestId(), future);
            throw exception;
        }
    }

    private void handleVersionPolicy(LocalClientFrame frame) {
        LocalClientPayloads.VersionPolicy policy = codec.payload(frame, LocalClientPayloads.VersionPolicy.class);
        if (!stateStore.read().clientInstanceId().equals(policy.clientInstanceId())
                || policy.connectionGeneration() != generation.get()
                || policy.policyRevision() < 1) {
            throw new IllegalArgumentException("version policy coordinates are invalid");
        }
        // 非强制策略只驱动平台站内信；实际切换始终等待带幂等 commandId 的 UPDATE_COMMAND。
    }

    private void submitUpdateCommand(LocalClientFrame frame) {
        LocalClientSelfUpdater updater = requireSelfUpdater();
        LocalClientPayloads.UpdateCommand command = codec.payload(frame, LocalClientPayloads.UpdateCommand.class);
        requireUpdateCoordinates(frame, command.commandId(), command.connectionGeneration());
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
        requireSelfUpdater().handleApply(apply);
    }

    private void handleUpdateCancel(LocalClientFrame frame) {
        LocalClientPayloads.UpdateCancel cancel = codec.payload(frame, LocalClientPayloads.UpdateCancel.class);
        requireUpdateCoordinates(frame, cancel.commandId(), cancel.connectionGeneration());
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
        LocalClientPayloads.LifecycleResult result = switch (action) {
            case "START" -> supervisor.start(command.preferredPort());
            case "RESTART" -> supervisor.restart(command.preferredPort());
            case "STOP" -> supervisor.stop();
            case "STATUS" -> supervisor.status();
            default -> throw new IllegalArgumentException("unsupported lifecycle action");
        };
        sendResponse(frame, LocalClientFrameType.LIFECYCLE_RESULT, result);
    }

    private void handleFile(LocalClientFrame frame) {
        LocalClientPayloads.FileRequest request = codec.payload(frame, LocalClientPayloads.FileRequest.class);
        sendResponse(frame, LocalClientFrameType.FILE_RESPONSE,
                new LocalClientPayloads.FileResponse(true, fileRpcHandler.handle(request)));
    }

    private void handleHttp(LocalClientFrame frame) {
        LocalClientPayloads.HttpRequest request = codec.payload(frame, LocalClientPayloads.HttpRequest.class);
        LocalClientPayloads.LifecycleResult status = supervisor.status();
        if (!status.success() || !status.opencodeHealthy() || status.opencodePort() == null) {
            throw new IllegalStateException("local OpenCode is not healthy");
        }
        String pathAndQuery = request.pathAndQuery();
        if (pathAndQuery == null || !pathAndQuery.startsWith("/") || pathAndQuery.contains("\r") || pathAndQuery.contains("\n")) {
            throw new IllegalArgumentException("OpenCode request path is invalid");
        }
        URI target = URI.create("http://127.0.0.1:" + status.opencodePort() + pathAndQuery);
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(target).timeout(Duration.ofHours(24));
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
        if (future != null) {
            future.cancel(true);
        }
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
                status = new LocalClientPayloads.LifecycleResult(
                        false, "FAILED", null, null, null, false, null, "状态检查失败");
            }
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
            WebSocket socket = webSocket.get();
            if (socket != null) {
                socket.abort();
            }
        }
    }

    private void startVersionChecks() {
        if (!buildInfo.capabilities().contains(LocalClientBuildInfo.SELF_UPDATE_CAPABILITY)) {
            return;
        }
        ScheduledFuture<?> previous = versionCheckTask.getAndSet(
                scheduler.schedule(this::versionCheckSafely, 0, TimeUnit.SECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
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
        operations.forEach((id, future) -> future.cancel(true));
        operations.clear();
        fileRpcHandler.abortAll();
        if (selfUpdater != null) {
            selfUpdater.handleConnectionLost();
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

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!resourcesClosed.compareAndSet(false, true)) {
            return;
        }
        closed.set(true);
        WebSocket socket = webSocket.getAndSet(null);
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "CLIENT_SHUTDOWN");
        }
        disconnected();
        inboundExecutor.close();
        operationExecutor.close();
        scheduler.close();
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
        operations.forEach((id, future) -> future.cancel(true));
        operations.clear();
        fileRpcHandler.abortAll();
    }

    @Override
    public void resume() {
        acceptingRequests.set(true);
    }

    @Override
    public void requestExit(int requestedExitCode) {
        if (requestedExitCode != LocalClientSelfUpdater.LAUNCHER_APPLY_EXIT_CODE) {
            throw new IllegalArgumentException("unsupported local client exit code");
        }
        exitCode.compareAndSet(0, requestedExitCode);
        closed.set(true);
        CompletableFuture<Void> closeFuture = activeConnectionClose.get();
        if (closeFuture != null) {
            closeFuture.complete(null);
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
            closedFuture.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closedFuture.completeExceptionally(error);
        }

        CompletableFuture<Void> closedFuture() {
            return closedFuture;
        }
    }
}
