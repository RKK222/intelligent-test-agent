package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;

/** 公共生命周期服务针对 LOCAL_CLIENT 目标使用的反向隧道端口。 */
public interface LocalClientLifecycleGateway {

    LocalClientLifecycleResult start(LocalClientInstanceId clientInstanceId, long generation, String traceId);

    LocalClientLifecycleResult restart(LocalClientInstanceId clientInstanceId, long generation, String traceId);

    LocalClientLifecycleResult stop(LocalClientInstanceId clientInstanceId, long generation, String traceId);

    LocalClientLifecycleResult status(LocalClientInstanceId clientInstanceId, long generation, String traceId);
}
