package com.enterprise.testagent.domain.memory;

/** 记忆形成来源；该字段不保存任何原始对话内容。 */
public enum MemorySource {
    NATIVE,
    MANUAL,
    /** 只用于读取升级前遗留数据，新学习链不再生成。 */
    EXPLICIT,
    /** 只用于读取升级前遗留数据，新学习链不再生成。 */
    IMPLICIT,
    TEAM_PROPOSAL,
    ADMIN_CREATED
}
