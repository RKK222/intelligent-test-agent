package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;

/**
 * 后端到容器管理进程的控制面网关；真实 socket 实现由后续批次替换。
 */
public interface OpencodeProcessManagerGateway {

    OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command);

    OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command);

    OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command);

    OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command);

    /**
     * 停止有平台归属的精确实例；旧手工实现默认 fail closed，绝不降级为仅端口 stop。
     */
    default OpencodeProcessControlResult stopOwnedProcess(OpencodeProcessOwnedStopCommand command) {
        throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent manager 不支持实例校验停止");
    }
}
