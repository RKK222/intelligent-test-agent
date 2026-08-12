package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import java.util.List;
import reactor.core.publisher.Mono;

/** 从 XXL 执行所在 Java 到固定目标 Java 的公共路由端口。 */
@FunctionalInterface
public interface NightExecutionDispatchGateway {

    Mono<NightExecutionDispatchBatchResult> dispatch(
            String linuxServerId,
            List<NightExecutionTaskId> taskIds,
            String traceId);

    /** 本地任务必须精确发送到持有该 generation 连接的 Java。 */
    default Mono<NightExecutionDispatchBatchResult> dispatchLocal(
            LocalClientInstanceId clientInstanceId,
            BackendProcessId backendProcessId,
            List<NightExecutionTaskId> taskIds,
            String traceId) {
        return dispatch("local:" + clientInstanceId.value(), taskIds, traceId);
    }
}
