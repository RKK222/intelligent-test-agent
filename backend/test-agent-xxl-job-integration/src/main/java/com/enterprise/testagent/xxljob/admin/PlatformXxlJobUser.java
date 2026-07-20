package com.enterprise.testagent.xxljob.admin;

import java.time.Instant;

/** 平台扩展后的 XXL 用户行，只在 Admin 子上下文 MyBatis 边界使用。 */
public record PlatformXxlJobUser(
        int id,
        String platformUserId,
        String username,
        String token,
        int role,
        String permission,
        String sessionDigest,
        Instant sessionExpiresAt) {
}
