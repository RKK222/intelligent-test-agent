package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationTransferGateway;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** 公共路由选中目标 Java 后，以内部一次性 WebSocket 流式搬迁快照。 */
@Service
public class WebSocketPersonalWorkspaceRelocationTransferGateway
        implements PersonalWorkspaceRelocationTransferGateway {

    private static final int CHUNK_BYTES = 256 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(30);

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final XxlJobProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebSocketPersonalWorkspaceRelocationTransferGateway(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            XxlJobProperties properties,
            ObjectMapper objectMapper) {
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public void transfer(
            PersonalWorkspaceRelocation relocation,
            Path archive,
            String snapshotSha256,
            long archiveSizeBytes,
            String traceId) {
        BackendJavaProcess backend = routeResolver.requireBackend(relocation.targetLinuxServerId());
        if (routeResolver.isCurrent(backend.backendProcessId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人工作区搬迁目标不能是源 Java",
                    Map.of("targetLinuxServerId", relocation.targetLinuxServerId()));
        }
        PersonalWorkspaceRelocationTransferDtos.TicketRequest request =
                new PersonalWorkspaceRelocationTransferDtos.TicketRequest(
                        relocation.relocationId(),
                        relocation.sourceLinuxServerId(),
                        relocation.targetLinuxServerId(),
                        snapshotSha256,
                        archiveSizeBytes);
        ApiResponse<PersonalWorkspaceRelocationTransferDtos.TicketResponse> response =
                forwarder.forwardSystemTyped(
                        backend,
                        PersonalWorkspaceRelocationTransferController.TICKET_PATH,
                        request,
                        new TypeReference<>() { },
                        traceId,
                        properties.getAccessToken());
        URI webSocketUri = webSocketUri(
                backend.listenUrl(),
                response.data().webSocketPath());
        TransferListener listener = new TransferListener();
        WebSocket socket;
        try {
            socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .header("Origin", PersonalWorkspaceRelocationTransferTicketStore.INTERNAL_ORIGIN)
                    .header(
                            PersonalWorkspaceRelocationTransferTicketStore.TICKET_HEADER,
                            response.data().ticket())
                    .header(
                            PersonalWorkspaceRelocationTransferTicketStore.SOURCE_SERVER_HEADER,
                            relocation.sourceLinuxServerId())
                    .header(TraceConstants.TRACE_ID_HEADER, traceId)
                    .buildAsync(webSocketUri, listener)
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            sendArchive(socket, archive);
            socket.sendText("{\"op\":\"complete\"}", true).join();
            String terminal = listener.terminal().get(TRANSFER_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            PersonalWorkspaceRelocationTransferDtos.TransferResponse result =
                    objectMapper.readValue(terminal, PersonalWorkspaceRelocationTransferDtos.TransferResponse.class);
            if (!result.success()) {
                throw new PlatformException(
                        parseErrorCode(result.code()),
                        result.message() == null ? "目标服务器恢复个人工作区失败" : result.message());
            }
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "completed");
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器个人工作区搬迁通道不可用",
                    Map.of("targetLinuxServerId", relocation.targetLinuxServerId()),
                    exception);
        }
    }

    private void sendArchive(WebSocket socket, Path archive) throws Exception {
        try (InputStream input = Files.newInputStream(archive)) {
            byte[] buffer = new byte[CHUNK_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                byte[] chunk = read == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, read);
                socket.sendBinary(ByteBuffer.wrap(chunk), true).join();
            }
        }
    }

    private URI webSocketUri(String listenUrl, String path) {
        try {
            URI base = URI.create(listenUrl);
            String scheme = switch (base.getScheme().toLowerCase(java.util.Locale.ROOT)) {
                case "http" -> "ws";
                case "https" -> "wss";
                default -> throw new IllegalArgumentException("unsupported backend scheme");
            };
            String basePath = base.getPath() == null || "/".equals(base.getPath())
                    ? ""
                    : base.getPath().replaceFirst("/$", "");
            URI relative = URI.create(path);
            return new URI(
                    scheme,
                    base.getUserInfo(),
                    base.getHost(),
                    base.getPort(),
                    basePath + relative.getPath(),
                    relative.getQuery(),
                    null);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "目标服务器搬迁地址无效");
        }
    }

    private ErrorCode parseErrorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (Exception ignored) {
            return ErrorCode.INTERNAL_ERROR;
        }
    }

    private static final class TransferListener implements WebSocket.Listener {
        private final CompletableFuture<String> terminal = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                terminal.complete(text.toString());
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!terminal.isDone()) {
                terminal.completeExceptionally(new IllegalStateException("relocation socket closed before result"));
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            terminal.completeExceptionally(error);
        }

        CompletableFuture<String> terminal() {
            return terminal;
        }
    }
}
