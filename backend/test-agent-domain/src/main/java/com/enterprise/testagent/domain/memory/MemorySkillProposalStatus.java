package com.enterprise.testagent.domain.memory;

/** Skill 提案生命周期；DRAFT 仅表示审核通过后的可编辑草稿，不代表已经提交或发布。 */
public enum MemorySkillProposalStatus {
    PENDING_REVIEW,
    DRAFT,
    REJECTED,
    PUBLISHED,
    ARCHIVED
}
