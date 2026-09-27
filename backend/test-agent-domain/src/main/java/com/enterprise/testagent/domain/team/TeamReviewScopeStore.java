package com.enterprise.testagent.domain.team;

import java.util.Optional;

/** 短效审阅范围端口；Run 绑定必须原子完成，禁止旧范围被后续 Run 复用。 */
public interface TeamReviewScopeStore {
    void save(TeamReviewModels.Scope scope);
    Optional<TeamReviewModels.Scope> find(String id);
    boolean bindRun(String id, String runId);
}
