package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.workspace.TeamWorkspaceExportService;
import com.enterprise.testagent.workspace.TeamWorkspaceExportShardGateway;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

/**
 * 团队导出 shard 跨节点实现：控制请求走公共路由/HTTP 转发，文件字节走一次性 WebSocket。
 */
@Service
public class WebSocketTeamWorkspaceExportShardGateway implements TeamWorkspaceExportShardGateway {

    private static final int CHUNK_BYTES = 256 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(30);
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024 + 64L * 1024 * 1024;

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final TeamWorkspaceExportShardTicketStore tickets;
    private final TeamWorkspaceExportService exportService;
    private final XxlJobProperties properties;
    private final ManagerControlSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebSocketTeamWorkspaceExportShardGateway(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            TeamWorkspaceExportShardTicketStore tickets,
            @Lazy TeamWorkspaceExportService exportService,
            XxlJobProperties properties,
            ManagerControlSettings settings,
            ObjectMapper objectMapper) {
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.tickets = Objects.requireNonNull(tickets);
        this.exportService = Objects.requireNonNull(exportService);
        this.properties = Objects.requireNonNull(properties);
        this.settings = Objects.requireNonNull(settings);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Preflight inspect(Request request) {
        BackendJavaProcess source = routeResolver.requireBackend(request.sourceLinuxServerId());
        TeamWorkspaceExportShardDtos.InspectRequest control = new TeamWorkspaceExportShardDtos.InspectRequest(
                request.exportId(), request.exportItemId(), request.sourceLinuxServerId(), request.userId(),
                request.personalWorkspaceId(), request.versionId(), request.maxUncompressedBytes(),
                request.maxFileCount());
        ApiResponse<TeamWorkspaceExportShardDtos.InspectResponse> response = forwarder.forwardSystemTyped(
                source,
                TeamWorkspaceExportShardController.INSPECT_PATH,
                control,
                new TypeReference<>() { },
                request.traceId(),
                properties.getAccessToken());
        return new Preflight(response.data().fileCount(), response.data().uncompressedBytes());
    }

    @Override
    public TransferredShard fetch(Request request, Path target) {
        BackendJavaProcess source = routeResolver.requireBackend(request.sourceLinuxServerId());
        if (routeResolver.isCurrent(source.backendProcessId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "跨节点 shard 源节点不能是当前 Java");
        }
        TeamWorkspaceExportShardTicket ticket = tickets.issue(
                request.sourceLinuxServerId(), target, MAX_ARCHIVE_BYTES);
        TeamWorkspaceExportShardDtos.BuildRequest control = new TeamWorkspaceExportShardDtos.BuildRequest(
                request.exportId(), request.exportItemId(), request.sourceLinuxServerId(), request.userId(),
                request.personalWorkspaceId(), request.versionId(), request.maxUncompressedBytes(),
                request.maxFileCount(), settings.listenUrl(), ticket.value());
        ApiResponse<TeamWorkspaceExportShardDtos.BuildResponse> response = forwarder.forwardSystemTyped(
                source,
                TeamWorkspaceExportShardController.BUILD_PATH,
                control,
                new TypeReference<>() { },
                request.traceId(),
                properties.getAccessToken());
        TeamWorkspaceExportShardDtos.BuildResponse result = response.data();
        return new TransferredShard(
                result.fileCount(), result.uncompressedBytes(), result.archiveBytes(),
                result.sha256(), result.excluded());
    }

    @Override
    public void deleteRevokedArtifact(String coordinatorLinuxServerId, String exportId, String traceId) {
        BackendJavaProcess coordinator = routeResolver.requireBackend(coordinatorLinuxServerId);
        if (routeResolver.isCurrent(coordinator.backendProcessId())) {
            deleteRevokedArtifactLocal(exportId);
            return;
        }
        forwarder.forwardSystemTyped(
                coordinator,
                TeamWorkspaceExportShardController.DELETE_REVOKED_ARTIFACT_PATH,
                new TeamWorkspaceExportShardDtos.DeleteRevokedArtifactRequest(exportId),
                new TypeReference<ApiResponse<Boolean>>() { },
                traceId,
                properties.getAccessToken());
    }

    void deleteRevokedArtifactLocal(String exportId) {
        exportService.deleteRevokedArtifact(exportId);
    }

    /** 在源节点本地生成 shard，随后主动连接协调节点的一次性接收通道。 */
    TeamWorkspaceExportShardDtos.BuildResponse buildAndSend(
            TeamWorkspaceExportShardDtos.BuildRequest request, String traceId) {
        Request sourceRequest = new Request(
                request.exportId(), request.exportItemId(), request.sourceLinuxServerId(), request.userId(),
                request.personalWorkspaceId(), request.versionId(), request.maxUncompressedBytes(),
                request.maxFileCount(), traceId);
        TeamWorkspaceExportService.LocalShard shard = exportService.buildTransferShard(sourceRequest);
        try {
            sendArchive(request, shard, traceId);
            return new TeamWorkspaceExportShardDtos.BuildResponse(
                    shard.fileCount(), shard.uncompressedBytes(), shard.archiveBytes(),
                    shard.sha256(), shard.excluded());
        } finally {
            try {
                Files.deleteIfExists(shard.path());
            } catch (Exception ignored) {
            }
        }
    }

    TeamWorkspaceExportShardDtos.InspectResponse inspectLocal(
            TeamWorkspaceExportShardDtos.InspectRequest request, String traceId) {
        Request sourceRequest = new Request(
                request.exportId(), request.exportItemId(), request.sourceLinuxServerId(), request.userId(),
                request.personalWorkspaceId(), request.versionId(), request.maxUncompressedBytes(),
                request.maxFileCount(), traceId);
        Preflight result = exportService.inspectTransferShard(sourceRequest);
        return new TeamWorkspaceExportShardDtos.InspectResponse(
                result.fileCount(), result.uncompressedBytes());
    }

    private void sendArchive(
            TeamWorkspaceExportShardDtos.BuildRequest request,
            TeamWorkspaceExportService.LocalShard shard,
            String traceId) {
        TransferListener listener = new TransferListener();
        WebSocket socket = null;
        try {
            socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .header("Origin", TeamWorkspaceExportShardController.INTERNAL_ORIGIN)
                    .header(TeamWorkspaceExportShardTicketStore.TICKET_HEADER, request.receiveTicket())
                    .header(TeamWorkspaceExportShardTicketStore.SOURCE_SERVER_HEADER, request.sourceLinuxServerId())
                    .header(TraceConstants.TRACE_ID_HEADER, traceId)
                    .buildAsync(webSocketUri(request.coordinatorBaseUrl()), listener)
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            try (InputStream input = Files.newInputStream(shard.path())) {
                byte[] buffer = new byte[CHUNK_BYTES];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    byte[] chunk = read == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, read);
                    socket.sendBinary(ByteBuffer.wrap(chunk), true).join();
                }
            }
            socket.sendText(objectMapper.writeValueAsString(java.util.Map.of(
                    "op", "complete", "sha256", shard.sha256(), "archiveBytes", shard.archiveBytes())), true).join();
            String terminal = listener.terminal().get(TRANSFER_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            TeamWorkspaceExportShardDtos.ReceiveResponse response = objectMapper.readValue(
                    terminal, TeamWorkspaceExportShardDtos.ReceiveResponse.class);
            if (!response.success()) {
                throw new PlatformException(ErrorCode.CONFLICT,
                        response.message() == null ? "协调节点接收 shard 失败" : response.message());
            }
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "completed");
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            if (socket != null) socket.abort();
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "团队导出 shard WebSocket 不可用",
                    java.util.Map.of("coordinatorBaseUrl", request.coordinatorBaseUrl()), exception);
        }
    }

    private URI webSocketUri(String baseUrl) {
        try {
            URI base = URI.create(baseUrl);
            String scheme = switch (base.getScheme().toLowerCase(java.util.Locale.ROOT)) {
                case "http" -> "ws";
                case "https" -> "wss";
                default -> throw new IllegalArgumentException("unsupported backend scheme");
            };
            String basePath = base.getPath() == null || "/".equals(base.getPath())
                    ? "" : base.getPath().replaceFirst("/$", "");
            return new URI(scheme, base.getUserInfo(), base.getHost(), base.getPort(),
                    basePath + TeamWorkspaceExportShardController.WEB_SOCKET_PATH, null, null);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "团队导出协调节点地址无效");
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
            if (last) terminal.complete(text.toString());
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!terminal.isDone()) terminal.completeExceptionally(
                    new IllegalStateException("team export shard socket closed before result"));
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
