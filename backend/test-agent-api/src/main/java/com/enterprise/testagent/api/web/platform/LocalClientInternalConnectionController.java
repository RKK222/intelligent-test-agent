package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientTunnelGateway;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 目标 Java 内部关闭接口；只关闭精确 generation，不能误伤已经重连的新连接。 */
@RestController
public class LocalClientInternalConnectionController {

    public static final String PATH =
            "/api/internal/platform/local-opencode-client/connections/internal-revoke";

    private final LocalClientConnectionRegistry connections;
    private final LocalClientTunnelGateway tunnelGateway;
    private final byte[] expectedToken;

    public LocalClientInternalConnectionController(
            LocalClientConnectionRegistry connections,
            LocalClientTunnelGateway tunnelGateway,
            XxlJobProperties properties) {
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
        this.tunnelGateway = Objects.requireNonNull(tunnelGateway, "tunnelGateway must not be null");
        String configuredToken = properties.getAccessToken();
        this.expectedToken = (configuredToken == null ? "" : configuredToken).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(PATH)
    public ApiResponse<LocalClientInternalConnectionDtos.RevokeResponse> revoke(
            @RequestHeader(value = NightExecutionInternalDispatchController.ACCESS_TOKEN_HEADER, required = false)
                    String accessToken,
            @RequestBody LocalClientInternalConnectionDtos.RevokeRequest request,
            ServerWebExchange exchange) {
        requireToken(accessToken);
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId(request.clientInstanceId());
        boolean closed = connections.close(
                clientInstanceId,
                request.connectionGeneration(),
                safeReason(request.reason()));
        if (closed) {
            tunnelGateway.failConnection(
                    clientInstanceId,
                    request.connectionGeneration(),
                    new PlatformException(ErrorCode.LOCAL_CLIENT_DISCONNECTED, "本地客户端连接已撤销"));
        }
        return ApiResponse.ok(
                new LocalClientInternalConnectionDtos.RevokeResponse(closed),
                RuntimeApiSupport.traceId(exchange));
    }

    private void requireToken(String value) {
        byte[] provided = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "后台内部 access token 无效");
        }
    }

    private static String safeReason(String value) {
        return value == null || value.isBlank() ? "CONNECTION_REVOKED" : value.substring(0, Math.min(128, value.length()));
    }
}
