package com.enterprise.testagent.system.supportaccess;

import java.time.Instant;

/** 签发结果；grantToken 只返回一次，调用方必须仅保存在页面内存。 */
public record SupportAccessGrantIssue(
        String grantId,
        String grantToken,
        Instant expiresAt) {
}
