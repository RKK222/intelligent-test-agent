package com.enterprise.testagent.domain.memory;

/** 记忆适用边界；团队记忆在 V1 中只能绑定 Application。 */
public enum MemoryScope {
    PERSONAL_GLOBAL,
    PERSONAL_APPLICATION,
    TEAM_APPLICATION
}
