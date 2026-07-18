package com.enterprise.testagent.domain.reference;

/** 引用资产库代次对应的内部操作语义，用于区分合法切换与磁盘指针漂移。 */
public enum ReferenceRepositoryOperationType {
    INITIALIZE,
    SYNCHRONIZE,
    SWITCH_BRANCH,
    VERIFY_POINTERS
}
