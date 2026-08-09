package com.enterprise.testagent.domain.memory;

/** 记忆形成来源；该字段不保存任何原始对话内容。 */
public enum MemorySource {
    MANUAL,
    EXPLICIT,
    IMPLICIT,
    TEAM_PROPOSAL,
    ADMIN_CREATED
}
