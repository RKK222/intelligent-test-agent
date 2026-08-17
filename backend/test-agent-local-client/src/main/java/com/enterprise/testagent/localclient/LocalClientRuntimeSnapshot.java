package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 托盘只读运行快照；状态均由现有连接和请求生命周期产生。 */
record LocalClientRuntimeSnapshot(
        ConnectionState connectionState,
        Instant connectedAt,
        String lastFailure,
        LocalClientPayloads.LifecycleResult processStatus,
        List<ActiveOperation> activeOperations) {

    LocalClientRuntimeSnapshot {
        activeOperations = activeOperations == null
                ? List.of()
                : activeOperations.stream().sorted(Comparator.comparing(ActiveOperation::startedAt)).toList();
    }

    enum ConnectionState {
        CONNECTING,
        ONLINE,
        OFFLINE,
        STOPPING
    }

    record ActiveOperation(String requestId, LocalClientFrameType type, Instant startedAt) {
    }
}
