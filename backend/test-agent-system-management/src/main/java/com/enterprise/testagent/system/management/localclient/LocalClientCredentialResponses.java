package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import java.time.Instant;

/** 本地客户端密钥设置页与连接认证使用的稳定返回模型。 */
public final class LocalClientCredentialResponses {

    private LocalClientCredentialResponses() {
    }

    public record CredentialView(
            boolean exists,
            String maskedKey,
            long version,
            LocalClientCredentialStatus status,
            Instant createdAt,
            Instant updatedAt,
            boolean revealAvailable) {
    }

    public record PlaintextKey(String clientKey) {
    }

    public record AuthenticatedCredential(String userId, long version) {
    }
}
