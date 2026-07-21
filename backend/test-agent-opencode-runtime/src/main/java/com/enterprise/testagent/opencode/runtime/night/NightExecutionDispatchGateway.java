package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import java.util.List;
import reactor.core.publisher.Mono;

/** 从 XXL 执行所在 Java 到固定目标 Java 的公共路由端口。 */
@FunctionalInterface
public interface NightExecutionDispatchGateway {

    Mono<NightExecutionDispatchBatchResult> dispatch(
            String linuxServerId,
            List<NightExecutionTaskId> taskIds,
            String traceId);
}
