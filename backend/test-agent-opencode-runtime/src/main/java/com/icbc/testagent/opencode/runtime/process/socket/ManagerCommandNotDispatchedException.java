package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.Map;
import java.util.Objects;

/**
 * manager 命令在查找本地 WebSocket 连接阶段即失败，明确表示命令尚未发送。
 */
public final class ManagerCommandNotDispatchedException extends PlatformException {

    public ManagerCommandNotDispatchedException(OpencodeContainerId containerId) {
        super(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "TestAgent 管理进程未连接",
                Map.of("containerId", Objects.requireNonNull(containerId, "containerId must not be null").value()));
    }
}
