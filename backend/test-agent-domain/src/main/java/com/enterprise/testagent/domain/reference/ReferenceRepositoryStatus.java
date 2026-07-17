package com.enterprise.testagent.domain.reference;

/** 引用资产库总体状态。 */
public enum ReferenceRepositoryStatus {
    UNINITIALIZED,
    INITIALIZING,
    VERIFYING,
    SYNCHRONIZING,
    READY,
    FAILED;

    /** 活动状态用于 POST 幂等判断，避免重复开启 generation。 */
    public boolean active() {
        return this == INITIALIZING || this == VERIFYING || this == SYNCHRONIZING;
    }
}
