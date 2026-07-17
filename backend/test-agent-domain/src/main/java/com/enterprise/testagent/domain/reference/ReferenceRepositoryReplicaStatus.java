package com.enterprise.testagent.domain.reference;

/** 单台 Linux 服务器上的引用资产副本状态。 */
public enum ReferenceRepositoryReplicaStatus {
    PENDING,
    PROCESSING,
    READY,
    RETRY_WAIT,
    BLOCKED,
    DEFERRED
}
