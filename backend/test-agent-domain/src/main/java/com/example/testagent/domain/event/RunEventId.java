package com.example.testagent.domain.event;

import com.example.testagent.domain.support.DomainValidation;

/**
 * RunEvent 领域 ID，事件只追加不更新，eventId 用于跨存储和前端日志定位。
 */
public record RunEventId(String value) {

    /**
     * 校验事件 ID 前缀，避免把其他资源 ID 写入事件表。
     */
    public RunEventId {
        value = DomainValidation.requirePrefixedId(value, "evt_", "eventId");
    }

    /**
     * 返回原始 eventId 字符串，便于日志和错误详情直接输出。
     */
    @Override
    public String toString() {
        return value;
    }
}
