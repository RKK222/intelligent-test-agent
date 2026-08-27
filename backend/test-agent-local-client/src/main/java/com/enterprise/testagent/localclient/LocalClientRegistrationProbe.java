package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** 首次接入使用短连接完成真实 WSS REGISTER；认证成功前绝不写入本地凭据。 */
final class LocalClientRegistrationProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final LocalClientConfiguration configuration;
    private final LocalClientStateStore stateStore;
    private final LocalClientBuildInfo buildInfo;
    private final Transport transport;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    LocalClientRegistrationProbe(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalClientBuildInfo buildInfo) {
        this(configuration, stateStore, buildInfo, new JdkTransport());
    }

    LocalClientRegistrationProbe(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalClientBuildInfo buildInfo,
            Transport transport) {
        this.configuration = Objects.requireNonNull(configuration);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.buildInfo = Objects.requireNonNull(buildInfo);
        this.transport = Objects.requireNonNull(transport);
    }

    void verify(LocalClientCredentialFile.Credentials credentials) throws Exception {
        LocalClientFrame request = LocalClientRegistrationFrames.create(
                configuration,
                credentials,
                stateStore.read(),
                buildInfo,
                requestId("lcep_"),
                requestId("trace_"));
        LocalClientFrame response;
        try {
            response = transport.exchange(configuration.websocketUri(), request);
        } catch (Exception exception) {
            throw new PlatformConnectionException(exception);
        }
        if (response.type() == LocalClientFrameType.ERROR) {
            // 只读取稳定错误码做现场分类；服务端正文和 details 绝不进入客户端异常或日志。
            LocalClientPayloads.Error error;
            try {
                error = codec.payload(response, LocalClientPayloads.Error.class);
            } catch (RuntimeException exception) {
                throw new InvalidRegistrationResponseException(exception);
            }
            String errorCode = safeErrorCode(error.code());
            if ("UNAUTHENTICATED".equals(errorCode) || "RATE_LIMITED".equals(errorCode)) {
                throw new LocalClientEnrollment.AuthenticationException();
            }
            throw new RegistrationRejectedException(errorCode);
        }
        if (response.type() != LocalClientFrameType.REGISTERED
                || !request.requestId().equals(response.requestId())
                || response.connectionGeneration() == null
                || response.connectionGeneration() < 1) {
            throw new InvalidRegistrationResponseException();
        }
        LocalClientPayloads.Registered registered;
        try {
            registered = codec.payload(response, LocalClientPayloads.Registered.class);
        } catch (RuntimeException exception) {
            throw new InvalidRegistrationResponseException(exception);
        }
        if (registered.connectionGeneration() != response.connectionGeneration()) {
            throw new InvalidRegistrationResponseException();
        }
    }

    private static String safeErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return "UNKNOWN_PLATFORM_ERROR";
        }
        return errorCode;
    }

    private static String requestId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    @FunctionalInterface
    interface Transport {
        LocalClientFrame exchange(URI websocketUri, LocalClientFrame request) throws Exception;
    }

    /** 平台已返回受控 ERROR，但不是凭据失败；仅保留稳定错误码。 */
    static final class RegistrationRejectedException extends Exception {
        private final String failureCode;

        RegistrationRejectedException(String failureCode) {
            super("平台拒绝本地客户端登记: " + failureCode);
            this.failureCode = failureCode;
        }

        String failureCode() {
            return failureCode;
        }
    }

    /** 用户机未能完成到企业平台登记入口的 WebSocket 交换。 */
    static final class PlatformConnectionException extends Exception {
        PlatformConnectionException(Throwable cause) {
            super("无法连接企业平台登记地址", cause);
        }
    }

    /** 平台响应类型、关联 ID 或载荷不符合登记协议。 */
    static final class InvalidRegistrationResponseException extends Exception {
        InvalidRegistrationResponseException() {
            super("平台登记响应无效");
        }

        InvalidRegistrationResponseException(Throwable cause) {
            super("平台登记响应无效", cause);
        }
    }

    /** JDK WebSocket 只接收首个注册结果，随后立即关闭，避免首次接入进入控制运行态。 */
    private static final class JdkTransport implements Transport {
        private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

        @Override
        public LocalClientFrame exchange(URI websocketUri, LocalClientFrame request) throws Exception {
            CompletableFuture<LocalClientFrame> response = new CompletableFuture<>();
            ProbeListener listener = new ProbeListener(codec.encode(request), codec, response);
            WebSocket socket = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(websocketUri, listener)
                    .join();
            try {
                return response.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "ENROLLMENT_PROBE_COMPLETE");
            }
        }
    }

    private static final class ProbeListener implements WebSocket.Listener {
        private final String encodedRequest;
        private final LocalClientFrameCodec codec;
        private final CompletableFuture<LocalClientFrame> response;
        private final StringBuilder text = new StringBuilder();

        private ProbeListener(
                String encodedRequest,
                LocalClientFrameCodec codec,
                CompletableFuture<LocalClientFrame> response) {
            this.encodedRequest = encodedRequest;
            this.codec = codec;
            this.response = response;
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.sendText(encodedRequest, true);
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (text.length() > LocalClientProtocol.MAX_FRAME_CHARS) {
                response.completeExceptionally(new IllegalStateException("注册响应超过大小上限"));
                socket.abort();
                return CompletableFuture.completedFuture(null);
            }
            if (last) {
                try {
                    response.complete(codec.decode(text.toString()));
                } catch (RuntimeException exception) {
                    response.completeExceptionally(exception);
                }
            } else {
                socket.request(1);
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
            response.completeExceptionally(new IllegalStateException("平台注册连接已关闭"));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            response.completeExceptionally(error);
        }
    }
}
