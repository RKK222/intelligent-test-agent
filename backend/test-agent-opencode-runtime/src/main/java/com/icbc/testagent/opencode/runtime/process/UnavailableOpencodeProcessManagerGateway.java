package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;

/**
 * 批次 2 的生产占位网关；真实管理进程 socket 接入前，初始化请求明确返回不可用。
 */
public class UnavailableOpencodeProcessManagerGateway implements OpencodeProcessManagerGateway {

    @Override
    public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
        throw unavailable();
    }

    @Override
    public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
        throw unavailable();
    }

    private PlatformException unavailable() {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "opencode 管理进程尚未接入");
    }
}
