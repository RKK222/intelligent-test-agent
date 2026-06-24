package com.icbc.testagent.opencode.runtime.process;

/**
 * 后端到容器管理进程的控制面网关；真实 socket 实现由后续批次替换。
 */
public interface OpencodeProcessManagerGateway {

    OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command);

    OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command);
}
