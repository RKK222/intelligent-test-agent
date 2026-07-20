package com.enterprise.testagent.xxljob.admin;

import java.time.Instant;

/** JIT upsert 命令；password 是不可登录随机值，原生登录入口同时被过滤器禁用。 */
public record PlatformXxlJobUserUpsert(
        String platformUserId,
        String username,
        String password,
        int role,
        String permission,
        String sessionDigest,
        Instant sessionExpiresAt) {
}
