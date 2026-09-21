package com.enterprise.testagent.domain.team;

/** 团队代码视图中一项变更的来源。 */
public enum TeamContributionType {
    PUBLISHED_COMMIT,
    PERSONAL_COMMIT,
    UNCOMMITTED,
    SYNC_MERGE
}
