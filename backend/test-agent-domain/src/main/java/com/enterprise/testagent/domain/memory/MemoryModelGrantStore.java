package com.enterprise.testagent.domain.memory;

import java.time.Duration;
import java.util.Optional;

/** 记忆抽取模型的一次性短期授权端口；实现只能用不可逆摘要寻址。 */
public interface MemoryModelGrantStore {
    boolean save(String grantDigest, MemoryModelGrantPayload payload, Duration ttl);

    Optional<MemoryModelGrantPayload> consume(String grantDigest);
}
