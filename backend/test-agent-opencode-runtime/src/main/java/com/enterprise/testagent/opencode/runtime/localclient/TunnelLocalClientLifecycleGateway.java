package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.runtime.process.LocalClientLifecycleGateway;
import com.enterprise.testagent.opencode.runtime.process.LocalClientLifecycleResult;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** LOCAL_CLIENT 生命周期通过反向隧道执行，不接触 opencode-manager。 */
@Service
public class TunnelLocalClientLifecycleGateway implements LocalClientLifecycleGateway {

    private final LocalClientTunnelGateway tunnelGateway;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    public TunnelLocalClientLifecycleGateway(LocalClientTunnelGateway tunnelGateway) {
        this.tunnelGateway = tunnelGateway;
    }

    @Override
    public LocalClientLifecycleResult start(
            LocalClientInstanceId clientInstanceId, long generation, String traceId) {
        return invoke(clientInstanceId, generation, "START", traceId);
    }

    @Override
    public LocalClientLifecycleResult restart(
            LocalClientInstanceId clientInstanceId, long generation, String traceId) {
        return invoke(clientInstanceId, generation, "RESTART", traceId);
    }

    @Override
    public LocalClientLifecycleResult stop(
            LocalClientInstanceId clientInstanceId, long generation, String traceId) {
        return invoke(clientInstanceId, generation, "STOP", traceId);
    }

    @Override
    public LocalClientLifecycleResult status(
            LocalClientInstanceId clientInstanceId, long generation, String traceId) {
        return invoke(clientInstanceId, generation, "STATUS", traceId);
    }

    private LocalClientLifecycleResult invoke(
            LocalClientInstanceId clientInstanceId,
            long generation,
            String action,
            String traceId) {
        LocalClientFrame frame = tunnelGateway.request(
                        clientInstanceId,
                        generation,
                        LocalClientFrameType.LIFECYCLE_COMMAND,
                        new LocalClientPayloads.LifecycleCommand(action, null, java.util.Map.of()),
                        traceId,
                        LocalClientProtocol.REQUEST_TIMEOUT)
                .block();
        if (frame == null || frame.type() != LocalClientFrameType.LIFECYCLE_RESULT) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端生命周期响应类型无效");
        }
        LocalClientPayloads.LifecycleResult result = codec.payload(frame, LocalClientPayloads.LifecycleResult.class);
        LocalClientProcessStatus status;
        try {
            status = LocalClientProcessStatus.valueOf(result.processStatus().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端生命周期状态无效");
        }
        return new LocalClientLifecycleResult(
                result.success(),
                status,
                result.processId(),
                result.processStartedAt(),
                result.opencodePort(),
                result.opencodeHealthy(),
                result.executable(),
                result.message());
    }
}
