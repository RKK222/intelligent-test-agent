package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 请求管理进程检测用户 opencode server 进程健康状态的命令。
 */
public record OpencodeProcessHealthCommand(
        OpencodeProcessId processId,
        String baseUrl,
        String traceId) {

    public OpencodeProcessHealthCommand {
        Objects.requireNonNull(processId, "processId must not be null");
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
