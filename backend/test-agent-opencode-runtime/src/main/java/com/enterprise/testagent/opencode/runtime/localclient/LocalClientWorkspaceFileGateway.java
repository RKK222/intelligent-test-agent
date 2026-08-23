package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 本地工作区文件 RPC 网关。浏览器消息仍由服务端 ticket 鉴权，本类只向固定 generation 转发。
 */
@Service
public class LocalClientWorkspaceFileGateway {

    private static final Duration FILE_TIMEOUT = LocalClientProtocol.LONG_REQUEST_TIMEOUT;
    private static final Duration GIT_ACCESS_TIMEOUT = Duration.ofSeconds(75);

    private final LocalClientTunnelGateway tunnelGateway;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    public LocalClientWorkspaceFileGateway(LocalClientTunnelGateway tunnelGateway) {
        this.tunnelGateway = Objects.requireNonNull(tunnelGateway, "tunnelGateway must not be null");
    }

    public JsonNode invoke(
            String clientInstanceId,
            long connectionGeneration,
            String workspaceId,
            String rootDigest,
            String operation,
            JsonNode parameters,
            String traceId) {
        return invoke(
                clientInstanceId,
                connectionGeneration,
                workspaceId,
                rootDigest,
                operation,
                parameters,
                traceId,
                FILE_TIMEOUT);
    }

    /** 本地 Git 权限巡检使用独立有界超时，避免后台任务继承长文件传输等待时间。 */
    public JsonNode checkGitAccess(
            String clientInstanceId,
            long connectionGeneration,
            String workspaceId,
            String rootDigest,
            String traceId) {
        return invoke(
                clientInstanceId,
                connectionGeneration,
                workspaceId,
                rootDigest,
                "workspace.git-access.check",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                traceId,
                GIT_ACCESS_TIMEOUT);
    }

    /** 浏览器文件 WebSocket 断开时使用短超时清理远端上传临时文件，避免清理线程悬挂一天。 */
    public void abortUpload(
            String clientInstanceId,
            long connectionGeneration,
            String workspaceId,
            String rootDigest,
            String uploadId,
            String traceId) {
        com.fasterxml.jackson.databind.node.ObjectNode parameters =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        parameters.put("uploadId", uploadId);
        invoke(
                clientInstanceId,
                connectionGeneration,
                workspaceId,
                rootDigest,
                "workspace.upload.abort",
                parameters,
                traceId,
                LocalClientProtocol.REQUEST_TIMEOUT);
    }

    private JsonNode invoke(
            String clientInstanceId,
            long connectionGeneration,
            String workspaceId,
            String rootDigest,
            String operation,
            JsonNode parameters,
            String traceId,
            Duration timeout) {
        LocalClientFrame frame = tunnelGateway.request(
                        new LocalClientInstanceId(clientInstanceId),
                        connectionGeneration,
                        LocalClientFrameType.FILE_REQUEST,
                        new LocalClientPayloads.FileRequest(
                                workspaceId, rootDigest, operation, parameters),
                        traceId,
                        timeout)
                .block(timeout.plusSeconds(1));
        if (frame == null || frame.type() != LocalClientFrameType.FILE_RESPONSE) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地文件 RPC 响应无效");
        }
        LocalClientPayloads.FileResponse response = codec.payload(frame, LocalClientPayloads.FileResponse.class);
        if (!response.success()) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地文件 RPC 执行失败");
        }
        return response.result();
    }
}
