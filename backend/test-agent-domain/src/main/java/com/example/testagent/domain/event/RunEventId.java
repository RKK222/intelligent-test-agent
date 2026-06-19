package com.example.testagent.domain.event;

import com.example.testagent.domain.support.DomainValidation;

/**
 * RunEvent 领域 ID，事件只追加不更新，eventId 用于跨存储和前端日志定位。
 */
public record RunEventId(String value) {

    public RunEventId {
        value = DomainValidation.requirePrefixedId(value, "evt_", "eventId");
    }

    @Override
    public String toString() {
        return value;
    }
}
