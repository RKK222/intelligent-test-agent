package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 主动 WSS 连接、心跳、反向生命周期/HTTP/SSE/文件 RPC 与取消处理。 */
final class LocalClientConnection implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientConnection.class);
    private static final int MAX_NON_STREAM_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_ACTIVE_OPERATIONS = 64;
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "proxy-authenticate", "host", "cookie", "connection",
            "content-length", "transfer-encoding", "upgrade", "keep-alive", "te", "trailer");

    private final LocalClientConfiguration configuration;
    private final String clientKey;
    private final LocalClientStateStore stateStore;
    private final OpencodeProcessSupervisor supervisor;
    private final LocalModelRelay modelRelay;
    private final LocalClientFileRpcHandler fileRpcHandler;
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
    private final Map<String, LocalClientRuntimeSnapshot.ActiveOperation> operationProgress = new ConcurrentHashMap<>();
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<ScheduledFuture<?>> heartbeatTask = new AtomicReference<>();
    private final AtomicReference<ConnectionListener> activeListener = new AtomicReference<>();
    private final Semaphore reconnectSignal = new Semaphore(0);
    private final AtomicReference<LocalClientRuntimeSnapshot.ConnectionState> connectionState =
            new AtomicReference<>(LocalClientRuntimeSnapshot.ConnectionState.OFFLINE);
    private final AtomicReference<Instant> connectedAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();
    private final AtomicReference<LocalClientPayloads.LifecycleResult> processStatus = new AtomicReference<>();

    LocalClientConnection(
            LocalClientConfiguration configuration,
            String clientKey,
            LocalClientStateStore stateStore,
            OpencodeProcessSupervisor supervisor,
            LocalModelRelay modelRelay,
            LocalClientFileRpcHandler fileRpcHandler) {
        this.configuration = configuration;
        this.clientKey = clientKey;
        this.stateStore = stateStore;
        this.supervisor = supervisor;
        this.modelRelay = modelRelay;
        this.fileRpcHandler = fileRpcHandler;
    }

    void runForever() {
        long backoffSeconds = 1;
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.CONNECTING);
            ConnectionListener listener = new ConnectionListener();
            activeListener.set(listener);
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
                    lastFailure.set(exception.getClass().getSimpleName());
                    LOGGER.warn("local_client_connection_failed server={} retrySeconds={}",
                            configuration.serverBaseUri(), backoffSeconds);
                }
            } finally {
                activeListener.compareAndSet(listener, null);
                disconnected();
            }
            if (!closed.get()) {
                boolean reconnectRequested = waitForReconnect(Duration.ofSeconds(backoffSeconds));
                backoffSeconds = reconnectRequested ? 1 : Math.min(30, backoffSeconds * 2);
            }
        }
    }

    LocalClientRuntimeSnapshot runtimeSnapshot() {
        return new LocalClientRuntimeSnapshot(
                connectionState.get(),
                connectedAt.get(),
                lastFailure.get(),
                processStatus.get(),
                List.copyOf(operationProgress.values()));
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

    private void handleFrame(LocalClientFrame frame) {
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
            modelRelay.updateGrant(registered.modelGrant());
            startHeartbeat();
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
            case ERROR -> throw new IllegalStateException("server rejected local client connection");
            default -> throw new IllegalArgumentException("unsupported server frame type: " + frame.type());
        }
    }

    private void submit(LocalClientFrame frame, Runnable task) {
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
                operationProgress.remove(frame.requestId());
            }
        }, null);
        Future<?> previous = operations.putIfAbsent(frame.requestId(), future);
        if (previous != null) {
            throw new IllegalStateException("duplicate server requestId");
        }
        operationProgress.put(frame.requestId(), new LocalClientRuntimeSnapshot.ActiveOperation(
                frame.requestId(), frame.type(), Instant.now()));
        try {
            operationExecutor.execute(future);
        } catch (RuntimeException exception) {
            operations.remove(frame.requestId(), future);
            operationProgress.remove(frame.requestId());
            throw exception;
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
        processStatus.set(result);
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
        operationProgress.remove(cancel.targetRequestId());
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
                            reportedAddresses(),
                            Instant.now()))));
        } catch (RuntimeException exception) {
            WebSocket socket = webSocket.get();
            if (socket != null) {
                socket.abort();
            }
        }
    }

    private void sendRegistration(WebSocket socket) {
        LocalClientPersistentState state = stateStore.read();
        LocalClientPayloads.Register register = new LocalClientPayloads.Register(
                clientKey,
                state.clientInstanceId(),
                configuration.clientName(),
                platform(),
                architecture(),
                "0.1.0",
                "1.18.4",
                reportedAddresses());
        LocalClientFrame frame = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.REGISTER,
                requestId("lcr_"),
                requestId("trace_"),
                null,
                codec.payload(register));
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
        operations.forEach((id, future) -> future.cancel(true));
        operations.clear();
        operationProgress.clear();
        fileRpcHandler.abortAll();
        connectedAt.set(null);
        if (!closed.get()) {
            connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.OFFLINE);
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

    private static List<String> reportedAddresses() {
        try {
            List<String> result = new ArrayList<>();
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && result.size() < 16) {
                        result.add(address.getHostAddress());
                    }
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String platform() {
        return LocalClientPaths.isMac() ? "darwin" : "linux";
    }

    private static String architecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return "aarch64".equals(arch) ? "arm64" : arch;
    }

    private static String requestId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connectionState.set(LocalClientRuntimeSnapshot.ConnectionState.STOPPING);
        reconnectSignal.release();
        WebSocket socket = webSocket.getAndSet(null);
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "CLIENT_SHUTDOWN");
        }
        ConnectionListener listener = activeListener.get();
        if (listener != null) {
            listener.closedFuture().complete(null);
        }
        disconnected();
        inboundExecutor.close();
        operationExecutor.close();
        scheduler.close();
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
