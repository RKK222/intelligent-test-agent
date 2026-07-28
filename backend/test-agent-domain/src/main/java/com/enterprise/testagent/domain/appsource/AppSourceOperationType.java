package com.enterprise.testagent.domain.appsource;

/** 应用源码物化操作类型。 */
public enum AppSourceOperationType {
    DOWNLOAD,
    UPDATE,
    SWITCH_BRANCH,
    CHANGE_SELECTION,
    PROMOTE_TO_TEAM,
    RETRY_REPLICAS,
    CLEANUP
}
