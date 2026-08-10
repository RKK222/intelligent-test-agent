package com.enterprise.testagent.domain.sessionshare;

/** 分享链接自身状态；过期由 expiresAt 动态判断，不改写永久链接。 */
public enum SessionShareStatus {
    ACTIVE,
    REVOKED
}
