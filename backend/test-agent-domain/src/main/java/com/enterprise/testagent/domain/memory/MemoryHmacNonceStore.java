package com.enterprise.testagent.domain.memory;

import java.time.Duration;

/** Mem0 集群到 Java 模型网关的防重放 nonce 端口；多 Java 节点必须共享实现。 */
public interface MemoryHmacNonceStore {
    boolean claim(String clientId, String nonce, Duration ttl);
}
