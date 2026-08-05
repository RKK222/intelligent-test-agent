package com.enterprise.testagent.domain.supportaccess;

/** 排查审计分页筛选条件；空值表示不限制。 */
public record SupportAccessAuditQuery(
        String actorUserId,
        String targetUserId,
        String incidentId,
        String outcome) {

    public SupportAccessAuditQuery {
        actorUserId = normalize(actorUserId);
        targetUserId = normalize(targetUserId);
        incidentId = normalize(incidentId);
        outcome = normalize(outcome);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
