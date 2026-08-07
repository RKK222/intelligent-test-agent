package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunResendId;
import java.util.List;
import reactor.core.publisher.Mono;

/** 从扫描所在 Java 路由到记录固定目标 Java 的批量端口。 */
@FunctionalInterface
public interface RunResendDispatchGateway {

    Mono<RunResendDispatchBatchResult> dispatch(
            String linuxServerId,
            List<RunResendId> resendIds,
            String traceId);
}
