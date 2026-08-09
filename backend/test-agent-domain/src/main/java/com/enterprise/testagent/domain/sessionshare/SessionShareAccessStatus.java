package com.enterprise.testagent.domain.sessionshare;

/** 被分享人列表和访问上下文使用的最终状态。 */
public enum SessionShareAccessStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    REMOVED,
    SESSION_ARCHIVED
}
