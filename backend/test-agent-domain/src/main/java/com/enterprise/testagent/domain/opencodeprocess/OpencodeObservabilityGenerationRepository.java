package com.enterprise.testagent.domain.opencodeprocess;

import java.util.Optional;

/**
 * 保存 OpenCode 进程当前 Observability 启动代次。
 *
 * <p>该值与会随健康检查更新的业务 traceId 分离，用于拒绝上一进程代次继续上传遥测。</p>
 */
public interface OpencodeObservabilityGenerationRepository {

    void save(OpencodeProcessId processId, String generation);

    Optional<String> findByProcessId(OpencodeProcessId processId);
}
