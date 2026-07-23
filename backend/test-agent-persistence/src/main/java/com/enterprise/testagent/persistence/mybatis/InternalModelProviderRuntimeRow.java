package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 启用供应商与 Token 明文的运行时联表行，仅供 JVM 快照加载使用。
 */
public record InternalModelProviderRuntimeRow(
        String providerId,
        String name,
        String baseUrl,
        boolean enabled,
        int sortOrder,
        Long tokenId,
        String tokenName,
        String authToken,
        Instant createdAt,
        Instant updatedAt) {
}
