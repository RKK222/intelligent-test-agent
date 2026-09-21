package com.enterprise.testagent.domain.team;

/** 团队特权访问审计写入端口。 */
public interface TeamOversightAuditRepository {
    void append(TeamOversightAuditEvent event);
}
