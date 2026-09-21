package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 源节点接收内部控制请求，并通过文件 WebSocket 把过滤后的 shard 回传协调节点。 */
@RestController
public class TeamWorkspaceExportShardController {

    public static final String BUILD_PATH =
            "/api/internal/platform/workspace-management/team/export-shards/build";
    public static final String INSPECT_PATH =
            "/api/internal/platform/workspace-management/team/export-shards/inspect";
    public static final String DELETE_REVOKED_ARTIFACT_PATH =
            "/api/internal/platform/workspace-management/team/export-shards/delete-revoked-artifact";
    public static final String WEB_SOCKET_PATH =
            "/api/internal/platform/workspace-management/team/export-shards/receive/ws";
    public static final String INTERNAL_ORIGIN = "https://test-agent.internal";
    private static final String ACCESS_TOKEN_HEADER = "XXL-JOB-ACCESS-TOKEN";

    private final WebSocketTeamWorkspaceExportShardGateway gateway;
    private final byte[] expectedToken;

    public TeamWorkspaceExportShardController(
            WebSocketTeamWorkspaceExportShardGateway gateway,
            XxlJobProperties properties) {
        this.gateway = Objects.requireNonNull(gateway);
        this.expectedToken = properties.getAccessToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(BUILD_PATH)
    public ApiResponse<TeamWorkspaceExportShardDtos.BuildResponse> build(
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody TeamWorkspaceExportShardDtos.BuildRequest request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(gateway.buildAndSend(request, traceId), traceId);
    }

    @PostMapping(INSPECT_PATH)
    public ApiResponse<TeamWorkspaceExportShardDtos.InspectResponse> inspect(
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody TeamWorkspaceExportShardDtos.InspectRequest request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(gateway.inspectLocal(request, traceId), traceId);
    }

    @PostMapping(DELETE_REVOKED_ARTIFACT_PATH)
    public ApiResponse<Boolean> deleteRevokedArtifact(
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody TeamWorkspaceExportShardDtos.DeleteRevokedArtifactRequest request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        gateway.deleteRevokedArtifactLocal(request.exportId());
        return ApiResponse.ok(true, RuntimeApiSupport.traceId(exchange));
    }

    private void requireToken(String value) {
        byte[] provided = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "XXL-JOB access token 无效");
        }
    }
}
