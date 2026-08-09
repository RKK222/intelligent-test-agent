package com.enterprise.testagent.domain.memory;

import com.enterprise.testagent.domain.support.DomainValidation;

/** 平台记忆治理 ID；与 Mem0 memory ID 分离。 */
public record MemoryId(String value) {
    public MemoryId {
        value = DomainValidation.requireText(value, "memoryId");
        if (value.length() > 128) {
            throw new IllegalArgumentException("memoryId must not exceed 128 characters");
        }
    }
}
